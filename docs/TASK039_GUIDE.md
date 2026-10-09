# Task 039 작업 안내서 (정확해·그리디 Solver와 플랜 유형 3종)

> 이 문서는 **구현 방법이 아니라 Solver가 지켜야 할 결과(계약)** 를 적는다. 구현 담당과 테스트 담당이 이 문서만 같이 보고 각자 작업한다. 모호한 곳을 발견하면 가정하지 말고 보고서의 "모호한 점"에 적는다.
> 근거: [PRD 5.4·11절 35번](./PRD.md), [TECH 1절](./TECH.md), [TASK038_GUIDE](./TASK038_GUIDE.md)(평가기·추천형 비교기 규칙). 038의 규칙은 여기서 다시 정하지 않는다.

## 1. 한눈에 보기

| 항목 | 내용 |
|---|---|
| 목적 | 구독 조합이 주어졌을 때 평가하는 일(038)에 이어, **월 예산 안에서 플랜 유형(추천형·절약형·간편형)별로 최선의 3개월 구독 조합을 고르는 일**을 만든다 |
| 브랜치 | `feature/b2-engine-solvers`(구현), `feature/b2-engine-solvers-tests`(테스트, 구현 코드를 보지 않고 작성) |
| 만드는 것 | `ExactSolver`, `GreedySolver`, `SolverSelector`(정확해 → 그리디 전환), `PlanObjective`(유형별 비교기: 절약형·간편형 구현), `PlanTypeSolver`(3종을 한 번에). 껍데기(시그니처)는 이미 코드에 있다 |
| 만들지 않는 것 | 입력 변환(`plan` 서비스, 063), `input_hash` 캐시(040·064), 번들·광고형 일반화(2단계), 계산 시간 측정(040), 세 유형 결과가 같을 때 화면에서 합치는 일(plan 서비스·프론트) |
| 패키지 | `com.ottnavi.engine.solver`(`PlanSolver`, `ExactSolver`, `GreedySolver`, `SolverSelector`, `PlanTypeSolver`), `com.ottnavi.engine.rule`(`PlanObjective` + 유형별 비교기), `com.ottnavi.engine.model`(`SolveResult`, `SolverType`, `PlanType`). ROADMAP은 `engine/PlanSolver`로 적었으나 `model`·`rule`과 같은 하위 패키지 구조에 맞춰 `solver`로 둔다 |

## 2. 계약 (코드에 있는 시그니처)

- `PlanType`: `RECOMMENDED`(추천형), `SAVER`(절약형), `SIMPLE`(간편형).
- `PlanObjective(planType, comparator)`: 유형과 그 유형의 비교기(양수 = 첫 번째가 더 좋다). 팩토리 `recommended(products)`, `saver(products, recommendedScore)`, `simple(products, recommendedScore)`, 정적 `scoreFloor(recommendedScore)`.
- `PlanSolver.solve(PlanInput, PlanObjective) → SolveResult`. `solve(PlanInput)`은 추천형 목적함수로 위임하는 기본 메서드다. `null` 입력·목적함수는 `IllegalArgumentException`.
- `SolveResult(evaluation, planType, solverType)`이고 `selection()`은 `evaluation.selection()`이다. `planType`은 푼 목적함수의 유형.
- `ExactSolver`: 인자 없는 생성자, `countCandidateCombinations(PlanInput, PlanObjective) → long`(S5).
- `GreedySolver`: 인자 없는 생성자.
- `SolverSelector(ExactSolver, GreedySolver, long maxCandidateCombinations)`: 푸는 목적함수 기준의 `countCandidateCombinations`가 상한 **초과**면 그리디, 이하면 정확해. 상한이 0 이하면 `IllegalArgumentException`.
- `PlanTypeSolver(PlanSolver)`, `solveAll(PlanInput) → Map<PlanType, SolveResult>`: 항상 세 유형을 모두 담고 `PlanType` 순서(`RECOMMENDED`, `SAVER`, `SIMPLE`)로 순회된다. 추천형을 먼저 풀고 그 결과의 `score`로 절약형·간편형 목적함수를 만든다.
- `solverType`: `ExactSolver`는 항상 `EXACT`, `GreedySolver`는 항상 `GREEDY`, `SolverSelector`는 실제로 푼 쪽. 유형마다 전환 여부가 달라질 수 있다(후보 수가 유형마다 다르다).
- 같은 입력에는 항상 같은 결과(결정적). 입력 `units` 순서가 달라도 같다(`PlanInput`이 정렬하므로 자동).
- 입력을 바꾸거나 전역 상태를 쓰지 않는다. `Evaluator`는 풀이마다 `new Evaluator(input)` 한 번 만들고 `evaluate`를 반복 호출한다(038 호출 규칙).

## 3. 플랜 유형별 목적함수 (PRD 5.4, 사전식, 위에서부터 비교)

세 유형 모두 ① 꼭 완주 수(`mustCompleted`) 큰 쪽으로 시작한다. 아래 "새 결제 건수"는 비교기 ④(추천형)가 달마다 세는 값(FREE, 0번째 달 SUBSCRIBED 제외)의 3개월 합이다. `minScore = PlanObjective.scoreFloor(추천형 점수) = ceil(추천형 점수 × 0.7)`이다(정수 계산: `(score * 7 + 9) / 10`).

| 유형 | 순서 |
|---|---|
| 추천형 | ① 꼭 완주 수↑ → ② `score`↑ → ③ `totalCost`↓ → ④ 결제 미루기 → ⑤ 달별 상품 ID 사전순 (038의 `EvaluationComparator` 그대로) |
| 절약형 | ① 꼭 완주 수↑ → ② `score >= minScore`인 쪽이 우월 → ③ `totalCost`↓ → ④ `score`↑ → ⑤ 결제 미루기 → ⑥ 달별 상품 ID 사전순 |
| 간편형 | ① 꼭 완주 수↑ → ② `score >= minScore`인 쪽이 우월 → ③ 새 결제 건수↓ → ④ `totalCost`↓ → ⑤ `score`↑ → ⑥ 결제 미루기 → ⑦ 달별 상품 ID 사전순 |

- 절약형·간편형의 ②는 "하한을 넘었는지"만 본다(둘 다 넘으면 같다). 둘 다 못 넘은 경우 ②에서는 같고, ③으로 넘어간다(점수가 모자란 것끼리 비용을 비교하는 일은 하한을 넘는 조합이 존재하는 한 결과에 영향이 없다. 추천형 조합 자체가 하한을 넘기 때문이다).
- 추천형 `score`가 0이면 `minScore`는 0이다.
- ⑥·⑤에 쓰는 "결제 미루기"와 "상품 ID 사전순"은 038의 `EvaluationComparator` ④·⑤와 같은 정의다. 구현은 이 비교기 로직을 재사용하거나 같은 규칙으로 복제한다.

## 4. 후보 조합 규칙

**S1(확정). 달별 예산.** 한 달에 고른 상품들의 **그 달 비용 합**이 `monthlyBudget` 이하여야 한다. 그 달 비용은 평가기와 같은 기준이다: FREE는 0원, 0번째 달의 SUBSCRIBED는 0원(이미 낸 돈), 그 외는 `monthlyPrice`. 예산은 달마다 따로 적용하고 3개월 합산 한도는 없다.

**S2. 빈 달 허용.** 아무 상품도 고르지 않는 달(빈 집합)은 언제나 가능하다. 그래서 후보가 하나도 없는 입력은 생기지 않는다.

**S3. 정확해의 정의(가장 중요).** `ExactSolver` 결과의 `selection`은 **예산 이하 모든 조합(달별 부분집합의 곱)을 `Evaluator`로 평가해, 해당 목적함수의 비교기로 가장 좋은 것**과 정확히 같아야 한다(`Collections.max`). 이것은 세 유형 모두에 성립한다. 지배 조합 제거는 속도를 위한 최적화이며 **추천형에서만** 쓸 수 있고(절약형·간편형은 비용·시청 가능 집합만으로 우월을 정할 수 없다) 결과를 바꾸면 안 된다. 제거할 때 동점은 038·TECH 1절 규칙(비용·시청 가능 집합이 같으면 새 결제 상품 수가 적은 쪽, 그것도 같으면 비교기 ⑤ 우선)대로 남긴다. 그러므로 테스트는 작은 입력에서 무차별 대입과 비교할 수 있다.

**S4(확정). 그리디의 정의.** `GreedySolver`는 `m = 0, 1, 2` 순서로 진행한다. m번째 달에 대해 그 달 예산 이하 모든 부분집합을 후보로 삼고, **앞선 달은 이미 고른 것으로 고정하고 뒤의 달은 빈 집합**으로 둔 조합을 `Evaluator`로 평가해 **주어진 목적함수의 비교기**로 가장 좋은 후보를 그 달의 선택으로 확정한다. 마지막에 확정된 3개 달로 최종 `Evaluation`을 만든다. 뒤의 달을 보지 못하므로 **여러 달에 걸쳐서야 완주되는 긴 시즌에서 정확해보다 나쁠 수 있다**(그리디가 나쁜 케이스 테스트의 근거).
- 그리디 결과는 같은 목적함수로 정확해보다 좋을 수 없다(`compare(exact, greedy) >= 0`). 같거나 나쁘다.

**S5(확정). 그리디 전환 기준.** 푸는 목적함수 기준으로 정확해가 훑을 3개월 조합 수 `c0 × c1 × c2`(달별 후보 수의 곱)를 센다. 추천형은 지배 조합 제거 후 개수, 절약형·간편형은 제거 없이 예산 이하 부분집합 수다. 이 값이 상한보다 **크면** 그리디, **같거나 작으면** 정확해다. 상한은 설정 값으로 받는다(R11-30 측정 전까지 잠정값).

## 5. 반드시 지켜야 할 성질 (테스트 기준)

1. **정확해 = 무차별 대입 최선**(S3): 세 유형 각각, 상품 3~4개의 여러 입력에서 비교한다.
2. **그리디**는 예산을 지키고(S1) 같은 목적함수의 정확해보다 좋지 않다(`compare(exact, greedy) >= 0`).
3. **그리디가 정확해와 다른 입력이 존재한다**(차이를 `compare`로 단언). 예: 월 시청 600분, 월 예산 10,000원, 작품 X(WANT, 1000분)는 p1(10,000원)에만, 작품 Y(WANT, 300분)는 p2(8,000원)에만 있을 때 정확해(추천형)는 0번째 달 p2, 1·2번째 달 p1로 점수 4·비용 28,000원, 그리디는 점수 2·비용 8,000원.
4. **SolverSelector**: 상한 이하면 `EXACT`, 초과면 `GREEDY`, 경계값(후보 수 = 상한)은 정확해. 상한이 0 이하면 생성자 예외.
5. **지배 제거 동점**(038 T20): 시청 가능 집합이 같은 두 상품 중 비교기 ⑤로 앞서는 쪽이 남는다(추천형).
6. **결정성**: 같은 입력을 두 번 풀면 같은 결과, 입력 단위 순서를 섞어도 같은 결과.
7. **SUBSCRIBED 시작**(0번째 달 비용 0이라 예산이 모자라도 시청 가능), **FREE 상품**(선택하지 않아도 보이고 선택은 ⑤ 때문에 불리), **시청 가능 상품이 없는 단위**(NO_PROVIDER·UNKNOWN은 조합에 영향 없음).
8. **결정 1·4**(몰아보기 연속, 한 달 최소 120분)가 Solver 선택을 바꾸는 경우: 긴 시즌을 끝내려면 연속된 달을 골라야 정확해가 그렇게 고른다.
9. **빈 입력**: 단위가 없으면 빈 조합(세 달 모두 빈 집합)이 답이고 비용 0.
10. **null 입력**은 `IllegalArgumentException`.
11. **`scoreFloor`**: 0→0, 1→1, 3→3, 5→4, 10→7, 20→14 (올림 70%).
12. **절약형**(정확해로 풀었을 때): `score >= minScore`이고 `totalCost <= 추천형 totalCost`. 꼭 완주 수는 추천형과 같다(추천형 조합 자체가 후보이므로). 그리디로 풀면 이 성질을 보장하지 않는다.
13. **간편형**(정확해로 풀었을 때): `score >= minScore`이고 새 결제 건수 `<=` 추천형의 새 결제 건수. 꼭 완주 수는 추천형과 같다. 그리디로 풀면 보장하지 않는다.
14. **`PlanTypeSolver.solveAll`**: 세 유형 모두 담고, 추천형 결과가 `solve(input)`과 같으며, 절약형·간편형은 추천형 점수 기준의 하한을 쓴다. 추천형이 그리디로 풀린 경우에도 그 결과의 점수를 하한 기준으로 쓴다.
15. 세 유형의 `selection`이 같은 입력(예: 상품 하나뿐, 예산이 넉넉하고 작품 하나)이 존재한다. 그때도 세 항목을 모두 돌려준다(합치는 일은 호출하는 쪽의 몫).

## 6. 성능 메모 (정확성 우선, 측정은 040)

- MVP 7개 단품이면 달마다 최대 128개 부분집합, 3개월 최대 약 210만 번 `evaluate`다. 추천형은 지배 제거로 줄이고, 절약형·간편형은 제거 없이 약 210만 번씩이라 세 유형을 풀면 약 3배다. 측정(040) 결과로 "한 번의 순회에서 평가를 재사용해 유형별 최선을 함께 고르는 최적화"가 필요한지 정한다. 지금은 단순하게 유형마다 따로 푼다.
- 모든 `Evaluation`을 보관하지 않는다. 지금까지의 최선 하나만 갖고 가며 비교기로 갱신한다(210만 객체 보관 금지).
- 상한 값은 설정 값으로 받는다. 코드 기본값이 필요하면 상수로 두고 `R11-30`을 주석에 적는다.

## 7. 완료 기준 (ROADMAP Task 039)

V-B: 세 유형 모두 두 Solver가 정답과 일치하는 케이스, 그리디가 정확해보다 나쁜 케이스(차이 단언), 상한 초과 시 GREEDY 전환, `solveAll`이 통과한다. `clean build`가 통과한다.
