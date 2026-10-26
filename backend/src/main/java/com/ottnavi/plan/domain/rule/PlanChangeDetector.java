package com.ottnavi.plan.domain.rule;

import com.ottnavi.plan.domain.enums.PlanChangeReasonType;
import com.ottnavi.plan.domain.enums.PlanTitleChangeType;
import com.ottnavi.plan.domain.rule.PlanChangeEvidence.AvailabilityShift;
import com.ottnavi.plan.domain.rule.PlanSnapshot.Month;
import com.ottnavi.plan.domain.rule.PlanSnapshot.Placement;
import com.ottnavi.plan.domain.rule.PlanSnapshot.Product;
import com.ottnavi.plan.domain.rule.PlanSnapshot.Unit;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * 이전 플랜과 재계산한 새 플랜을 비교해 변경 요약을 만든다(Task 069, 068 기록의 서버 규칙). 순수 Java라 DB 없이 테스트한다.
 *
 * <pre>
 * 이전 11월 [넷플릭스], 새 11월 [디즈니+] + 근거(카지노가 넷플릭스에서 빠짐)
 *   → monthChanges 1건(11월), 이유 TITLE_LEFT_SERVICE, 요약 "11월 구독이 넷플릭스에서 디즈니+로 바뀌었어요"
 * 새 달(1월)만 생기고 다른 변화가 없음 → 기록하지 않음(빈 Optional)
 * 변화는 있는데 이유가 하나도 없음 → 기록하지 않음(플랜은 갱신하되 change_summary는 남기지 않는다, 결정 4)
 * </pre>
 *
 * 규칙: 두 플랜이 함께 다루는 달(겹치는 달)만 변화로 센다. 새 플랜에만 있는 달(새 달)은 변화로 세지 않지만
 * 기록할 때는 monthChanges에 항상 넣는다(Task 068 승인).
 */
public final class PlanChangeDetector {

	private PlanChangeDetector() {
	}

	/** 빠짐·들어옴 이유의 후보 한 건(시청 단위 × 서비스) */
	private record ShiftCandidate(Unit unit, long ottServiceId) {
	}

	/**
	 * 변경 요약을 만든다. 기록할 만한 변화와 이유가 모두 있을 때만 값이 있다.
	 *
	 * @param previous 이전 플랜(재계산 직전 ACTIVE)
	 * @param current  재계산으로 만든 새 플랜
	 * @param evidence 이유 판정 근거
	 */
	public static Optional<PlanChangeSummary> detect(PlanSnapshot previous, PlanSnapshot current,
			PlanChangeEvidence evidence) {
		if (current.months().isEmpty()) {
			throw new IllegalArgumentException("새 플랜에 달이 없다: planId=" + current.planId());
		}

		// 1. 새 플랜의 달을 달(1일) 순서로 정렬하고, 이전 플랜은 달로 찾을 수 있게 모은다
		List<Month> currentMonths = new ArrayList<>(current.months());
		currentMonths.sort(Comparator.comparing(Month::month));
		Map<LocalDate, Month> previousByMonth = new HashMap<>();
		for (Month month : previous.months()) {
			previousByMonth.put(month.month(), month);
		}
		Set<String> overlapLabels = new HashSet<>(); // 겹치는 달(YYYY-MM)
		Set<LocalDate> overlapMonths = new HashSet<>();
		for (Month month : currentMonths) {
			if (previousByMonth.containsKey(month.month())) {
				overlapMonths.add(month.month());
				overlapLabels.add(YearMonth.from(month.month()).toString());
			}
		}
		LocalDate currentStart = currentMonths.get(0).month();

		// 2. 달 변화를 만든다. 겹치는 달은 상품·요금이 달라졌을 때만, 새 달은 항상 넣는다
		List<PlanChangeSummary.MonthChange> monthChanges = new ArrayList<>();
		List<Month> changedAfter = new ArrayList<>(); // 겹치는 달 중 변화가 있는 새 플랜의 달
		List<Month> changedBefore = new ArrayList<>(); // 위와 짝인 이전 플랜의 달
		for (Month month : currentMonths) {
			Month before = previousByMonth.get(month.month());
			if (before == null) {
				monthChanges.add(toMonthChange(null, month));
			} else if (isMonthChanged(before, month)) {
				monthChanges.add(toMonthChange(before, month));
				changedAfter.add(month);
				changedBefore.add(before);
			}
		}

		// 3. 작품 변화를 만든다. 자리는 (시청 완료 달, 그 달의 서비스)이다
		List<PlanChangeSummary.TitleChange> titleChanges = detectTitleChanges(previous, current, currentStart);

		// 4. 기록 조건: 겹치는 달의 달 변화, 또는 이전·새 자리 중 하나가 겹치는 달에 있는 작품 변화가 1건 이상이다
		boolean hasQualifyingChange = !changedAfter.isEmpty();
		Set<Long> changedUnitIds = new HashSet<>();
		for (PlanChangeSummary.TitleChange change : titleChanges) {
			changedUnitIds.add(change.watchUnitId());
			if (isInMonths(change.previous(), overlapLabels) || isInMonths(change.current(), overlapLabels)) {
				hasQualifyingChange = true;
			}
		}
		if (!hasQualifyingChange) {
			return Optional.empty();
		}

		// 5. 이유를 판정한다. 하나도 없으면 기록하지 않는다
		List<PlanChangeSummary.Reason> reasons = new ArrayList<>();
		addPriceReason(reasons, previous, changedAfter);
		addAvailabilityReasons(reasons, previous, current, evidence, changedUnitIds, changedBefore, changedAfter);
		addWatchedReason(reasons, previous, evidence, overlapMonths);
		if (reasons.isEmpty()) {
			return Optional.empty();
		}

		// 6. 요약 문장을 만든다. 겹치는 달의 첫 변화를 우선하고, 없으면 첫 작품 변화를 쓴다
		String summary = buildSummary(changedBefore, changedAfter, titleChanges, evidence);
		return Optional.of(new PlanChangeSummary(PlanChangeSummary.CURRENT_VERSION, summary, reasons, monthChanges,
				titleChanges));
	}

	// 달 변화

	/** 겹치는 달의 상품 집합이 다르거나 같은 상품의 적용 가격이 다르면 변화다. 이번 달 0원 같은 비용 차이만으로는 세지 않는다 */
	private static boolean isMonthChanged(Month before, Month after) {
		Map<Long, Integer> beforePrices = new HashMap<>();
		for (Product product : before.products()) {
			beforePrices.put(product.productId(), product.appliedPrice());
		}
		if (beforePrices.size() != after.products().size()) {
			return true;
		}
		for (Product product : after.products()) {
			Integer beforePrice = beforePrices.get(product.productId());
			if (beforePrice == null || beforePrice != product.appliedPrice()) {
				return true;
			}
		}
		return false;
	}

	/** 달 변화 한 건을 만든다. before가 null이면 이전 플랜에 없던 새 달이다(이전 비용 null, 이전 상품 빈 배열) */
	private static PlanChangeSummary.MonthChange toMonthChange(Month before, Month after) {
		List<PlanChangeSummary.Product> previousProducts = new ArrayList<>();
		Integer previousCost = null;
		if (before != null) {
			previousProducts = toProducts(before.products());
			previousCost = before.cost();
		}
		return new PlanChangeSummary.MonthChange(YearMonth.from(after.month()).toString(), after.isConfirmed(),
				previousProducts, toProducts(after.products()), previousCost, after.cost());
	}

	private static List<PlanChangeSummary.Product> toProducts(List<Product> products) {
		List<PlanChangeSummary.Product> result = new ArrayList<>();
		for (Product product : products) {
			result.add(new PlanChangeSummary.Product(product.productId(), product.ottServiceIds(), product.productName()));
		}
		return result;
	}

	// 작품 변화

	/** 시청 단위마다 이전·새 자리를 비교한다. 이전 자리가 새 플랜 첫 달보다 앞(이미 지난 달)이면 비교하지 않는다 */
	private static List<PlanChangeSummary.TitleChange> detectTitleChanges(PlanSnapshot previous, PlanSnapshot current,
			LocalDate currentStart) {
		// 시청 단위 ID 순으로 훑어 결과 순서를 고정한다
		Map<Long, Unit> previousUnits = new TreeMap<>();
		for (Unit unit : previous.units()) {
			previousUnits.put(unit.watchUnitId(), unit);
		}
		Map<Long, Unit> currentUnits = new TreeMap<>();
		for (Unit unit : current.units()) {
			currentUnits.put(unit.watchUnitId(), unit);
		}
		Set<Long> unitIds = new HashSet<>(previousUnits.keySet());
		unitIds.addAll(currentUnits.keySet());
		List<Long> sortedIds = new ArrayList<>(unitIds);
		sortedIds.sort(Comparator.naturalOrder());

		List<PlanChangeSummary.TitleChange> changes = new ArrayList<>();
		for (Long unitId : sortedIds) {
			Unit before = previousUnits.get(unitId);
			Unit after = currentUnits.get(unitId);
			Placement from = null;
			if (before != null) {
				from = before.placement();
			}
			Placement to = null;
			if (after != null) {
				to = after.placement();
			}
			if (from != null && from.month().isBefore(currentStart)) {
				continue; // 이전 자리가 이미 지난 달이면 비교하지 않는다
			}
			PlanTitleChangeType type = classify(from, to);
			if (type == null) {
				continue;
			}
			Unit named = after;
			if (named == null) {
				named = before;
			}
			changes.add(new PlanChangeSummary.TitleChange(type, unitId, named.title(), named.seasonNumber(),
					toPlacement(from), toPlacement(to)));
		}
		return changes;
	}

	/** 자리 두 개를 비교해 변화 종류를 정한다. 변화가 없으면 null */
	private static PlanTitleChangeType classify(Placement from, Placement to) {
		if (from == null && to == null) {
			return null;
		}
		if (from == null) {
			return PlanTitleChangeType.ADDED;
		}
		if (to == null) {
			return PlanTitleChangeType.REMOVED;
		}
		if (from.ottServiceId() != to.ottServiceId()) {
			return PlanTitleChangeType.CHANGED_SERVICE;
		}
		if (!from.month().equals(to.month())) {
			return PlanTitleChangeType.MOVED_MONTH;
		}
		return null;
	}

	private static PlanChangeSummary.Placement toPlacement(Placement placement) {
		if (placement == null) {
			return null;
		}
		return new PlanChangeSummary.Placement(YearMonth.from(placement.month()).toString(), placement.ottServiceId());
	}

	private static boolean isInMonths(PlanChangeSummary.Placement placement, Set<String> monthLabels) {
		return placement != null && monthLabels.contains(placement.month());
	}

	// 이유

	/** PRICE_CHANGED: 변화가 있는 달의 상품 중 이전 플랜에 남은 가격과 지금 가격이 다른 것. 이전 플랜에 없던 상품은 판정하지 않는다 */
	private static void addPriceReason(List<PlanChangeSummary.Reason> reasons, PlanSnapshot previous,
			List<Month> changedAfter) {
		Map<Long, Integer> previousPrices = new HashMap<>();
		for (Month month : previous.months()) {
			for (Product product : month.products()) {
				previousPrices.putIfAbsent(product.productId(), product.appliedPrice());
			}
		}
		Map<Long, Product> changedProducts = new LinkedHashMap<>();
		for (Month month : changedAfter) {
			for (Product product : month.products()) {
				Integer oldPrice = previousPrices.get(product.productId());
				if (oldPrice != null && oldPrice != product.appliedPrice()) {
					changedProducts.putIfAbsent(product.productId(), product);
				}
			}
		}
		if (changedProducts.isEmpty()) {
			return;
		}
		Product first = changedProducts.values().iterator().next();
		String description = PlanChangeMessages.priceChanged(first.productName(), previousPrices.get(first.productId()),
				first.appliedPrice(), changedProducts.size() - 1);
		reasons.add(new PlanChangeSummary.Reason(PlanChangeReasonType.PRICE_CHANGED, description, null,
				first.ottServiceIds().get(0)));
	}

	/**
	 * TITLE_LEFT_SERVICE / TITLE_JOINED_SERVICE: 이전 플랜 이후(changed_at > 이전 data_as_of)의 시청 단위·서비스별
	 * 가장 이른 이전 상태와 가장 늦은 이후 상태를 비교한다(있음 → 있음 아님이면 빠짐, 있음 아님·신규 → 있음이면 들어옴).
	 * 작품 변화가 있는 시청 단위이거나 그 서비스가 달 변화에 나오는 경우만 이유로 센다.
	 */
	private static void addAvailabilityReasons(List<PlanChangeSummary.Reason> reasons, PlanSnapshot previous,
			PlanSnapshot current, PlanChangeEvidence evidence, Set<Long> changedUnitIds, List<Month> changedBefore,
			List<Month> changedAfter) {
		// 1. 달 변화에 나오는 서비스를 모은다(이전·새 쪽 모두)
		Set<Long> monthServiceIds = new HashSet<>();
		for (Month month : changedBefore) {
			for (Product product : month.products()) {
				monthServiceIds.addAll(product.ottServiceIds());
			}
		}
		for (Month month : changedAfter) {
			for (Product product : month.products()) {
				monthServiceIds.addAll(product.ottServiceIds());
			}
		}
		Map<Long, Unit> knownUnits = new HashMap<>();
		for (Unit unit : previous.units()) {
			knownUnits.put(unit.watchUnitId(), unit);
		}
		for (Unit unit : current.units()) {
			knownUnits.put(unit.watchUnitId(), unit);
		}

		// 2. 이전 플랜 이후의 변경을 (시청 단위, 서비스)별로 모은다. 키는 시청 단위 → 서비스 순으로 정렬해 결과 순서를 고정한다
		Map<Long, Map<Long, List<AvailabilityShift>>> shifts = new TreeMap<>();
		for (AvailabilityShift shift : evidence.availabilityShifts()) {
			if (!shift.changedAt().isAfter(previous.dataAsOf())) {
				continue;
			}
			Map<Long, List<AvailabilityShift>> byService = shifts.get(shift.watchUnitId());
			if (byService == null) {
				byService = new TreeMap<>();
				shifts.put(shift.watchUnitId(), byService);
			}
			List<AvailabilityShift> list = byService.get(shift.ottServiceId());
			if (list == null) {
				list = new ArrayList<>();
				byService.put(shift.ottServiceId(), list);
			}
			list.add(shift);
		}

		// 3. 가장 이른 이전 상태와 가장 늦은 이후 상태로 빠짐·들어옴을 가른다
		List<ShiftCandidate> left = new ArrayList<>();
		List<ShiftCandidate> joined = new ArrayList<>();
		for (Map.Entry<Long, Map<Long, List<AvailabilityShift>>> unitEntry : shifts.entrySet()) {
			Unit unit = knownUnits.get(unitEntry.getKey());
			if (unit == null) {
				continue; // 두 플랜 어디에도 없는 시청 단위는 제목을 알 수 없어 건너뛴다
			}
			for (Map.Entry<Long, List<AvailabilityShift>> serviceEntry : unitEntry.getValue().entrySet()) {
				long serviceId = serviceEntry.getKey();
				if (!changedUnitIds.contains(unit.watchUnitId()) && !monthServiceIds.contains(serviceId)) {
					continue;
				}
				List<AvailabilityShift> list = serviceEntry.getValue();
				list.sort(Comparator.comparing(AvailabilityShift::changedAt));
				boolean wasAvailable = list.get(0).beforeAvailable();
				boolean isAvailable = list.get(list.size() - 1).afterAvailable();
				if (wasAvailable && !isAvailable) {
					left.add(new ShiftCandidate(unit, serviceId));
				} else if (!wasAvailable && isAvailable) {
					joined.add(new ShiftCandidate(unit, serviceId));
				}
			}
		}

		// 4. 종류마다 이유 한 줄로 묶는다(첫 건 + 외 N건)
		if (!left.isEmpty()) {
			ShiftCandidate first = left.get(0);
			String description = PlanChangeMessages.titleLeftService(labelOf(first.unit()),
					serviceNameOf(evidence, first.ottServiceId()), left.size() - 1);
			reasons.add(new PlanChangeSummary.Reason(PlanChangeReasonType.TITLE_LEFT_SERVICE, description,
					first.unit().watchUnitId(), first.ottServiceId()));
		}
		if (!joined.isEmpty()) {
			ShiftCandidate first = joined.get(0);
			String description = PlanChangeMessages.titleJoinedService(labelOf(first.unit()),
					serviceNameOf(evidence, first.ottServiceId()), joined.size() - 1);
			reasons.add(new PlanChangeSummary.Reason(PlanChangeReasonType.TITLE_JOINED_SERVICE, description,
					first.unit().watchUnitId(), first.ottServiceId()));
		}
	}

	/** MARKED_WATCHED: 이전 플랜에서 겹치는 달에 배정됐거나 배정 안 됨이었던 시청 단위 중 다 봤음 체크가 이어진 것 */
	private static void addWatchedReason(List<PlanChangeSummary.Reason> reasons, PlanSnapshot previous,
			PlanChangeEvidence evidence, Set<LocalDate> overlapMonths) {
		List<Unit> watched = new ArrayList<>();
		for (Unit unit : previous.units()) {
			if (!evidence.watchedUnitIds().contains(unit.watchUnitId())) {
				continue;
			}
			if (unit.placement() == null || overlapMonths.contains(unit.placement().month())) {
				watched.add(unit);
			}
		}
		if (watched.isEmpty()) {
			return;
		}
		watched.sort(Comparator.comparingLong(Unit::watchUnitId));
		Unit first = watched.get(0);
		String description = PlanChangeMessages.markedWatched(labelOf(first), watched.size() - 1);
		reasons.add(new PlanChangeSummary.Reason(PlanChangeReasonType.MARKED_WATCHED, description, first.watchUnitId(),
				null));
	}

	// 요약 문장

	/** 요약 한 줄. 겹치는 달 변화가 있으면 그 첫 달, 없으면 첫 작품 변화를 말하고 나머지는 "(외 N건)"으로 센다 */
	private static String buildSummary(List<Month> changedBefore, List<Month> changedAfter,
			List<PlanChangeSummary.TitleChange> titleChanges, PlanChangeEvidence evidence) {
		int total = changedAfter.size() + titleChanges.size();
		if (!changedAfter.isEmpty()) {
			Month before = changedBefore.get(0);
			Month after = changedAfter.get(0);
			String sentence = PlanChangeMessages.monthSummary(after.month().getMonthValue(),
					serviceNames(before.products(), evidence), serviceNames(after.products(), evidence));
			return PlanChangeMessages.withOthers(sentence, total - 1);
		}
		PlanChangeSummary.TitleChange change = titleChanges.get(0);
		Integer previousMonth = null;
		String previousService = "";
		if (change.previous() != null) {
			previousMonth = YearMonth.parse(change.previous().month()).getMonthValue();
			previousService = serviceNameOf(evidence, change.previous().ottServiceId());
		}
		Integer currentMonth = null;
		String currentService = "";
		if (change.current() != null) {
			currentMonth = YearMonth.parse(change.current().month()).getMonthValue();
			currentService = serviceNameOf(evidence, change.current().ottServiceId());
		}
		String sentence = PlanChangeMessages.titleSummary(change.changeType(),
				PlanChangeMessages.titleLabel(change.title(), change.seasonNumber()), previousMonth, currentMonth,
				previousService, currentService);
		return PlanChangeMessages.withOthers(sentence, total - 1);
	}

	/**
	 * 상품 목록을 중복 없는 이름 목록으로 바꾼다. 단품은 서비스 이름을, 번들(서비스가 여럿)이거나 이름을 모르면 상품 이름을
	 * 그대로 쓴다(번들은 Task 076에서 생긴다).
	 */
	private static List<String> serviceNames(List<Product> products, PlanChangeEvidence evidence) {
		List<String> names = new ArrayList<>();
		for (Product product : products) {
			String name = null;
			if (product.ottServiceIds().size() == 1) {
				name = evidence.serviceNames().get(product.ottServiceIds().get(0));
			}
			if (name == null) {
				name = product.productName();
			}
			if (!names.contains(name)) {
				names.add(name);
			}
		}
		return names;
	}

	private static String serviceNameOf(PlanChangeEvidence evidence, long ottServiceId) {
		String name = evidence.serviceNames().get(ottServiceId);
		if (name == null) {
			return "서비스 " + ottServiceId;
		}
		return name;
	}

	private static String labelOf(Unit unit) {
		return PlanChangeMessages.titleLabel(unit.title(), unit.seasonNumber());
	}
}
