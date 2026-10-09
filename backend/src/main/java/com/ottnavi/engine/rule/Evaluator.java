package com.ottnavi.engine.rule;

import com.ottnavi.engine.model.Assignment;
import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.OttProduct;
import com.ottnavi.engine.model.OttProductCondition;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.Priority;
import com.ottnavi.engine.model.Selection;
import com.ottnavi.engine.model.UnitReason;
import com.ottnavi.engine.model.UnitResult;
import com.ottnavi.engine.model.WatchUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 구독 조합 하나가 주어졌을 때 작품을 어느 달에 보는지 배정하고 그 결과를 평가한다(PRD 5.4 배정 규칙·목적함수 값).
 *
 * <p>사용법: 입력 하나로 한 번 만들고({@code new Evaluator(input)}) 조합마다 {@link #evaluate(Selection)}만 부른다.
 * 정확해(039)는 조합을 최대 약 210만 번(128³) 평가하므로, 매번 달라지지 않는 준비물은 생성자에서 한 번만 만든다.
 * 생성 후에는 상태가 바뀌지 않아 여러 스레드가 같은 Evaluator를 동시에 불러도 된다.
 *
 * <h2>비트마스크를 쓰는 이유</h2>
 * 비유: 상품 7개 앞에 전등 스위치 7개가 한 줄로 있다고 생각하면 된다. "이 작품을 볼 수 있는 상품"은 켜진 스위치 모양 하나,
 * "이번 달 구독한 상품"도 켜진 스위치 모양 하나다. 두 모양을 겹쳐서 같이 켜진 스위치가 하나라도 있으면 이번 달에 볼 수 있다.
 * 컴퓨터는 이 "스위치 줄"을 정수 하나의 비트(0/1)로 저장하고, 겹쳐 보기(AND, {@code &})를 한 번의 연산으로 끝낸다.
 * <ul>
 *   <li>단순한 방법: 단위마다 "시청 가능 상품 ID 집합"을 돌며 "이번 달 선택 집합"에 있는지 해시로 찾는다.</li>
 *   <li>문제점: 단위 30개 × 3개월 × 상품 몇 개의 해시 조회와 Long 박싱이 평가 한 번마다 생기고, 이걸 210만 번 반복한다.</li>
 *   <li>이 방식: 상품을 ID 오름차순으로 0~31번 자리에 고정하고, 단위의 시청 가능 상품과 달별 선택을 {@code int} 하나로 미리 바꿔 둔다.
 *       그러면 "이번 달에 볼 수 있나"는 {@code (달 마스크 & 단위 마스크) != 0} 한 번으로 끝난다.</li>
 * </ul>
 * 예: 상품 ID가 [3, 7, 9]면 자리 0·1·2에 놓인다(오른쪽 끝이 0번 자리). 어떤 단위를 7·9로 볼 수 있으면 {@code 0b110},
 * 이번 달 3·7을 선택했으면 {@code 0b011}이고, 겹치면 {@code 0b010}이라 0이 아니므로 볼 수 있다. 켜진 가장 낮은 자리(1번)가 상품 7이라 기록 상품도 7이다(D9).
 * 한계: {@code int}는 32비트라 상품은 최대 32개까지다(MVP 단품 7개, 2단계도 수십 개 이하로 예상). 넘으면 생성자가 막고, 그때는 {@code long}(64개)으로 바꾼다.
 */
public final class Evaluator {

	private static final int MONTH_COUNT = Selection.MONTH_COUNT; // 플랜은 항상 3개월
	private static final int MAX_PRODUCT_COUNT = Integer.SIZE;    // int 비트마스크로 담을 수 있는 상품 수(32)

	/**
	 * 긴 시즌을 나눠 볼 때 마지막 조각을 뺀 각 달 조각의 최소 시간(분). 120분은 영화 한 편 정도다.
	 * 사용자 결정 2026-10-09(결정 4, PRD 5.4·안내서에 반영 필요): 한 달에 몇 분만 찔끔 보는 배정을 막는다.
	 * 한계: 월 시청 가능 시간 M이 120분 미만이면 어느 달도 120분을 채울 수 없어 긴 시즌(필요 시간 &gt; M)은 시작할 수 없다(항상 TIME).
	 */
	public static final int MIN_LONG_SEASON_SEGMENT_MINUTES = 120;

	private final int monthlyWatchMinutes;  // 월 시청 가능 시간(분)

	// 상품 준비물: 배열 위치 i = 비트 i. ID 오름차순이라 가장 낮은 비트가 곧 가장 작은 상품 ID다(D9에 쓴다)
	private final long[] productIds;        // 상품 ID 오름차순
	private final int[] monthlyPrices;      // productIds와 같은 위치의 월 가격
	private final int freeMask;             // FREE 상품 비트. 선택과 무관하게 모든 달에 볼 수 있고 비용 0
	private final int subscribedMask;       // SUBSCRIBED 상품 비트. 0번째 달은 선택과 무관하게 볼 수 있고 비용 0(D2)

	// 시청 단위 준비물: PlanInput이 이미 배정 순서로 정렬해 두었으므로 그 순서 그대로 쓴다(D8)
	private final List<WatchUnit> units;    // 배정 순서로 정렬된 시청 단위
	private final int[] watchableMasks;     // 단위별 시청 가능 상품 비트
	// 정렬 기준 ①(WatchUnit.ASSIGNMENT_ORDER) 덕분에 시청 가능 상품이 있는 단위가 앞에, 없는 단위가 뒤에 모여 있다
	private final int watchableUnitCount;   // 앞쪽 "시청 가능 상품이 있는 단위" 수. 배정 루프는 여기까지만 돈다
	// 미배정 결과는 조합이 달라도 내용이 같아서(단위 ID + 이유 코드) 미리 만들어 두고 재사용한다(불변 record라 공유해도 안전)
	private final List<UnitResult> fixedResults; // 뒤쪽 단위들의 결과(NO_PROVIDER/UNKNOWN). 조합과 무관하게 정해진다
	private final UnitResult[] budgetResults;    // BUDGET 미배정 결과(뒤쪽 단위 자리는 null)
	private final UnitResult[] timeResults;      // TIME 미배정 결과(뒤쪽 단위 자리는 null)

	/** 입력 하나에 대한 평가 준비물(상품 위치·비트마스크·고정 결과)을 한 번 만든다. */
	public Evaluator(PlanInput input) {
		if (input == null) {
			throw new IllegalArgumentException("계산 입력은 null일 수 없다");
		}
		List<OttProduct> products = input.products();
		if (products.size() > MAX_PRODUCT_COUNT) {
			throw new IllegalArgumentException("상품은 최대 " + MAX_PRODUCT_COUNT + "개까지 평가할 수 있다(int 비트마스크 한계): " + products.size());
		}
		this.monthlyWatchMinutes = input.monthlyWatchMinutes();

		List<OttProduct> sortedProducts = new ArrayList<>(products);
		sortedProducts.sort(Comparator.comparingLong(OttProduct::id));
		this.productIds = new long[sortedProducts.size()];
		this.monthlyPrices = new int[sortedProducts.size()];
		int free = 0;
		int subscribed = 0;
		for (int bit = 0; bit < sortedProducts.size(); bit++) {
			OttProduct product = sortedProducts.get(bit);
			productIds[bit] = product.id();
			monthlyPrices[bit] = product.monthlyPrice();
			if (product.condition() == OttProductCondition.FREE) {
				free |= 1 << bit;
			} else if (product.condition() == OttProductCondition.SUBSCRIBED) {
				subscribed |= 1 << bit;
			}
		}
		this.freeMask = free;
		this.subscribedMask = subscribed;

		this.units = input.units();
		int unitCount = units.size();
		this.watchableMasks = new int[unitCount];
		this.budgetResults = new UnitResult[unitCount];
		this.timeResults = new UnitResult[unitCount];
		List<UnitResult> fixed = new ArrayList<>();
		int watchableCount = 0;
		for (int i = 0; i < unitCount; i++) {
			WatchUnit unit = units.get(i);
			// PlanInput이 시청 가능 상품 ID가 상품 목록에 있음을 이미 보장하므로 위치는 항상 찾아진다
			watchableMasks[i] = toMask(unit.watchableProductIds());
			if (watchableMasks[i] == 0) {
				fixed.add(fixedResultOf(unit));
				continue;
			}
			// 정렬 기준 ①이 지켜졌다면 시청 가능 상품이 없는 단위 뒤에 시청 가능 상품이 있는 단위가 올 수 없다
			if (!fixed.isEmpty()) {
				throw new IllegalStateException("시청 단위가 배정 순서로 정렬돼 있지 않다(시청 가능 상품이 없는 단위가 맨 뒤여야 한다): watchUnitId=" + unit.id());
			}
			watchableCount++;
			budgetResults[i] = UnitResult.unscheduled(unit.id(), UnitReason.BUDGET);
			timeResults[i] = UnitResult.unscheduled(unit.id(), UnitReason.TIME);
		}
		this.watchableUnitCount = watchableCount;
		this.fixedResults = List.copyOf(fixed);
	}

	/**
	 * 구독 조합 하나를 평가한다. 예산은 검사하지 않는다(예산 안의 조합만 넘기는 것은 Solver의 일, D7).
	 * 선택 상품 ID가 입력 상품 목록에 없으면 IllegalArgumentException을 던진다.
	 */
	public Evaluation evaluate(Selection selection) {
		if (selection == null) {
			throw new IllegalArgumentException("구독 조합은 null일 수 없다");
		}

		// 달별 "지금 볼 수 있는 상품" 마스크와 비용을 먼저 구한다
		int[] availableMasks = new int[MONTH_COUNT];
		int totalCost = 0;
		for (int month = 0; month < MONTH_COUNT; month++) {
			int selectedMask = toMask(selection.productIdsOf(month));
			totalCost += monthlyCost(month, selectedMask);
			// PRD 5.4 구독 상태 반영: FREE는 모든 달 상시 시청 가능(D1), SUBSCRIBED는 0번째 달에 선택하지 않아도 시청 가능(D2)
			availableMasks[month] = selectedMask | freeMask | (month == 0 ? subscribedMask : 0);
		}

		// 달별 남은 시청 시간. 서비스와 무관하게 그 달 전체에서 센다(PRD 5.4 긴 시즌)
		int[] remainingMinutes = {monthlyWatchMinutes, monthlyWatchMinutes, monthlyWatchMinutes};
		List<UnitResult> results = new ArrayList<>(units.size());
		int mustCompleted = 0;
		int score = 0;
		// PRD 5.4 배정 규칙 1: 정렬된 순서대로 한 단위씩 배정한다(정렬은 PlanInput 생성 때 끝남).
		// 시청 가능 상품이 없는 뒤쪽 단위는 시간을 쓰지 않으므로 루프에서 빼고, 끝에 미리 만든 결과를 순서대로 붙인다
		for (int i = 0; i < watchableUnitCount; i++) {
			UnitResult result = assign(i, availableMasks, remainingMinutes);
			results.add(result);
			if (result.scheduled()) {
				// PRD 5.4 목적함수 ①·②: MUST는 완주 수로, WANT 2·MAYBE 1은 점수로 센다(D5)
				Priority priority = units.get(i).priority();
				if (priority == Priority.MUST) {
					mustCompleted++;
				} else {
					score += priority.score();
				}
			}
		}
		// PRD 5.4 이유 코드 판정 순서 NO_PROVIDER → UNKNOWN: 시청 가능 상품이 하나도 없으면 조합과 무관하게 정해진다
		results.addAll(fixedResults);
		return new Evaluation(selection, mustCompleted, score, totalCost, results);
	}

	/** 시청 가능 상품이 있는 i번째 단위를 배정하고 결과를 돌려준다. 배정하면 remainingMinutes를 줄인다. */
	private UnitResult assign(int unitIndex, int[] availableMasks, int[] remainingMinutes) {
		int watchableMask = watchableMasks[unitIndex];
		// 달 3개를 비트 3개로: 비트 m이 1이면 m번째 달에 이 단위를 볼 수 있는 상품이 있다(예: 0b011 = 0·1번째 달)
		int watchableMonths = 0;
		for (int month = 0; month < MONTH_COUNT; month++) {
			if ((availableMasks[month] & watchableMask) != 0) {
				watchableMonths |= 1 << month;
			}
		}
		// 이유 코드 BUDGET(TECH 1절, 도출): 시청 가능 상품이 있지만 어느 달에도 선택되지 않았다
		if (watchableMonths == 0) {
			return budgetResults[unitIndex];
		}

		WatchUnit unit = units.get(unitIndex);
		UnitResult scheduled = unit.requiredMinutes() > monthlyWatchMinutes
				? assignLongSeason(unit, watchableMask, watchableMonths, availableMasks, remainingMinutes)
				: assignWithinMonth(unit, watchableMask, watchableMonths, availableMasks, remainingMinutes);
		// 이유 코드 TIME(TECH 1절, 도출): 볼 수 있는 달은 있으나 시간이 모자라 3개월 안에 다 보지 못한다
		return scheduled != null ? scheduled : timeResults[unitIndex];
	}

	/**
	 * 한 달 안에 볼 수 있는 단위를 나누지 않고 한 달에 통째로 배정한다. 못 하면 null.
	 * PRD 5.4 배정 규칙 2: 볼 수 있는 가장 이른 달부터, 규칙 4(D3): 남은 시간이 모자라면 나누지 않고 다음에 볼 수 있는 달로 넘어간다.
	 */
	private UnitResult assignWithinMonth(WatchUnit unit, int watchableMask, int watchableMonths,
			int[] availableMasks, int[] remainingMinutes) {
		int requiredMinutes = unit.requiredMinutes();
		for (int month = 0; month < MONTH_COUNT; month++) {
			if (isWatchableMonth(watchableMonths, month) && remainingMinutes[month] >= requiredMinutes) {
				remainingMinutes[month] -= requiredMinutes;
				Assignment assignment = new Assignment(month, recordedProductId(availableMasks[month] & watchableMask), requiredMinutes);
				// PRD 5.4 배정 규칙 5: 다 본 달에 점수를 준다
				return UnitResult.scheduled(unit.id(), month, List.of(assignment));
			}
		}
		return null;
	}

	/**
	 * 한 달 시청 가능 시간보다 긴 시즌을 연속된 달에 나눠 배정한다. 못 하면 null이고 remainingMinutes는 그대로다.
	 *
	 * <p>PRD 5.4 배정 규칙 3(긴 시즌, B안): 서비스와 상관없이, 그 시즌을 볼 수 있는 상품이 선택된 연속된 달에 이어서 나눠 본다.
	 * 가장 이른 시작 달부터 시도하고, 시작 달에서 시청 가능한 달이 끊기기 전까지 각 달의 남은 시간을 채운다.
	 *
	 * <p>추가 규칙(사용자 결정 2026-10-09, 안내서 7절 결정 1·PRD 11절 34번에 반영됨): <b>실제로 시청하는 달도 연속</b>이어야 한다.
	 * 시청 가능한 달이 이어져 있어도 중간 달의 남은 시간이 0이라 0분 배정이 되면, 이어 보기가 한 달 끊긴 것으로 보고 그 시작 달은 실패로 친다.
	 * (예: 0·1·2번째 달 모두 볼 수 있지만 1번째 달이 이미 꽉 찼으면 0번째·2번째 달에 나눠 보는 것은 허용하지 않는다.)
	 *
	 * <p>결정 4(사용자 결정 2026-10-09, PRD 5.4·안내서에 반영 필요): 시즌을 끝내는 마지막 조각을 뺀 각 달 조각은
	 * {@value #MIN_LONG_SEASON_SEGMENT_MINUTES}분 이상이어야 한다. 시작 달의 남은 시간이 120분 미만이면 그 달에서 시작하지 않고 다음 시작 달을 시도하고,
	 * 이어 가는 달의 남은 시간이 잔여 분보다 적으면서 120분 미만이면 이어 보기가 끊긴 것으로 보고(결정 1과 같은 처리) 그 시작 달은 실패로 친다.
	 * 마지막 조각은 120분보다 작아도 된다(예: 잔여 30분). 결정 1의 0분 배정 금지는 이 규칙에 포함된다.
	 *
	 * <p>되돌리기(D4) 방식: 단순한 방법은 남은 시간 배열을 복제해 시험 배정하고 성공하면 원래 배열에 옮겨 적는 것인데, 시작 달마다 배열과 목록을 새로 만든다.
	 * 여기서는 먼저 남은 시간을 바꾸지 않고 "끝까지 볼 수 있는지"만 계산하고, 성공한 경우에만 같은 계산을 다시 하며 실제로 줄인다.
	 * 실패하면 아무것도 바꾸지 않았으므로 일부 배정 시간을 따로 풀어 줄 필요가 없다(규칙 6: 미배정이면 일부 배정 시간은 풀어 준다).
	 */
	private UnitResult assignLongSeason(WatchUnit unit, int watchableMask, int watchableMonths,
			int[] availableMasks, int[] remainingMinutes) {
		int requiredMinutes = unit.requiredMinutes();
		for (int startMonth = 0; startMonth < MONTH_COUNT; startMonth++) {
			if (!isWatchableMonth(watchableMonths, startMonth)) {
				continue;
			}
			int completedMonth = findCompletedMonth(startMonth, requiredMinutes, watchableMonths, remainingMinutes);
			if (completedMonth < 0) {
				continue;
			}
			// 성공이 확인됐으니 시작 달부터 다 본 달까지 실제로 배정한다(시험 계산과 같은 순서·같은 값)
			List<Assignment> assignments = new ArrayList<>(completedMonth - startMonth + 1);
			int leftMinutes = requiredMinutes;
			for (int month = startMonth; month <= completedMonth; month++) {
				int minutes = Math.min(remainingMinutes[month], leftMinutes);
				remainingMinutes[month] -= minutes;
				leftMinutes -= minutes;
				// D9: 이 달에 이 시즌을 볼 수 있는 상품이 여럿이면 ID가 가장 작은 상품으로 기록한다
				assignments.add(new Assignment(month, recordedProductId(availableMasks[month] & watchableMask), minutes));
			}
			// PRD 5.4 배정 규칙 5: 3개월에 걸쳐도 다 본 달 한 번만 점수
			return UnitResult.scheduled(unit.id(), completedMonth, assignments);
		}
		// PRD 5.4 배정 규칙 6: 3개월 안에 끝나지 않으면 배정하지 않는다
		return null;
	}

	/** startMonth부터 연속으로 이어 볼 때 다 보는 달을 돌려준다. 남은 시간은 바꾸지 않는다. 못 보면 -1. */
	private int findCompletedMonth(int startMonth, int requiredMinutes, int watchableMonths, int[] remainingMinutes) {
		int leftMinutes = requiredMinutes;
		// 시청 가능한 달이 끊기면(비트가 0이면) 거기서 멈춘다: 선택된 달이 연속이어야 나눠 볼 수 있다(PRD 5.4 긴 시즌)
		for (int month = startMonth; month < MONTH_COUNT && isWatchableMonth(watchableMonths, month); month++) {
			int remaining = remainingMinutes[month];
			// 결정 4: 마지막 조각이 아닌데(남은 시간 < 잔여 분) 120분 미만이면 이 시작 달은 실패(0분이면 결정 1과 같다).
			// 시작 달은 필요 시간 > M ≥ 남은 시간이라 항상 마지막 조각이 아니므로, 시작 달이 120분 미만이면 다음 시작 달을 시도하는 셈이다
			if (remaining < leftMinutes && remaining < MIN_LONG_SEASON_SEGMENT_MINUTES) {
				return -1;
			}
			int minutes = Math.min(remaining, leftMinutes);
			leftMinutes -= minutes;
			if (leftMinutes == 0) {
				return month;
			}
		}
		return -1;
	}

	/** 달 비트 묶음에서 month번째 달을 볼 수 있는지 확인한다. */
	private static boolean isWatchableMonth(int watchableMonths, int month) {
		return (watchableMonths & (1 << month)) != 0;
	}

	/**
	 * 볼 수 있는 상품 비트 중 ID가 가장 작은 상품의 ID를 돌려준다(D9).
	 * 비트 i = ID 오름차순 i번째 상품이므로, 가장 낮은 켜진 비트(오른쪽 끝에서 0이 몇 개 이어지는지)가 곧 가장 작은 ID다.
	 */
	private long recordedProductId(int watchableAvailableMask) {
		return productIds[Integer.numberOfTrailingZeros(watchableAvailableMask)];
	}

	/**
	 * 한 달 동안 선택 상품에 드는 비용을 구한다(PRD 5.4 목적함수 ③, 구독 상태 반영).
	 * FREE는 항상 0원, SUBSCRIBED는 0번째 달만 0원이다(이미 낸 돈). 1·2번째 달의 SUBSCRIBED는 유지하면 가격이 든다.
	 * 켜진 비트만 돌므로 상품 수가 아니라 선택된 상품 수만큼(MVP 최대 7번) 더한다.
	 */
	private int monthlyCost(int month, int selectedMask) {
		int paidMask = selectedMask & ~freeMask;
		if (month == 0) {
			paidMask &= ~subscribedMask;
		}
		int cost = 0;
		while (paidMask != 0) {
			cost += monthlyPrices[Integer.numberOfTrailingZeros(paidMask)];
			paidMask &= paidMask - 1; // 가장 낮은 켜진 비트를 끈다
		}
		return cost;
	}

	/** 상품 ID 집합을 비트마스크로 바꾼다. 상품 목록에 없는 ID가 있으면 IllegalArgumentException. */
	private int toMask(Set<Long> ids) {
		int mask = 0;
		for (long productId : ids) {
			// 상품 수가 적어(MVP 7개) 정렬된 배열의 이진 탐색이 해시 조회보다 가볍다
			int bit = Arrays.binarySearch(productIds, productId);
			if (bit < 0) {
				throw new IllegalArgumentException("선택 상품 ID가 입력 상품 목록에 없다: productId=" + productId);
			}
			mask |= 1 << bit;
		}
		return mask;
	}

	/** 시청 가능 상품이 없는 단위의 고정 결과를 만든다(PRD 5.4 이유 코드 NO_PROVIDER → UNKNOWN). */
	private static UnitResult fixedResultOf(WatchUnit unit) {
		// "있음"이 하나도 없을 때: "모름"이 섞여 있으면 UNKNOWN, 모두 "없음"이면 NO_PROVIDER
		UnitReason reason = unit.isProviderUnknown() ? UnitReason.UNKNOWN : UnitReason.NO_PROVIDER;
		return UnitResult.unscheduled(unit.id(), reason);
	}
}
