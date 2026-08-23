# 특별배당 내역 표시 (Special Dividend Disclosure)

PROJECT.md 5.3절 "이상치 제거"의 남은 범위. 정기/특별배당 **분류 알고리즘**
자체는 이미 `docs/decisions/04-dividend-classification.md`로 해소됐다 —
Massive가 내려주는 `dividend_type`(`CD`/`SC`)을 그대로 신뢰하고,
`MassiveDividendMapper`가 이미 `DividendType.REGULAR`/`SPECIAL`로 분류해
저장한다. 이 스펙은 **이미 분류된 데이터를 조회해서 보여주는** 것만
다룬다 — 새 계산 로직은 없다.

## 0. 스코프

- 분류 로직 재구현 안 함 ([[04-dividend-classification]] 그대로 신뢰).
- 이 스펙이 다루는 건 "이 종목의 지급 이력 중 어떤 게 특별배당으로
  분류돼서 정기 지표(TTM/CAGR/변동성/삭감탐지) 계산에서 제외됐는가"를
  조회해서 보여주는 것뿐이다.
- 계산 로직이 없으므로 CLAUDE.md "테스트 없는 계산 로직 금지" 규칙의
  대상은 아니지만, 매퍼(정렬·필터링)에는 여전히 테스트를 둔다.

## 1. 계산식 (조회 로직)

계산이 아니라 조회+매핑이다. 입출력만 정의한다.

### 1.1 입력 변수

| 변수 | 정의 |
|---|---|
| `symbol` | 조회 대상 티커 심볼 |
| `payments` | 해당 티커의 `DividendPayment` 전체 (`REGULAR` + `SPECIAL` 둘 다), ex-dividend date 오름차순 |

### 1.2 응답 구성

```
allPayments = DividendPaymentRepository에서 해당 ticker의 전체 지급 이력 조회
  (findByTickerOrderByExDividendDateAsc — 신규 리포지토리 메서드, type 필터 없음)

각 지급 건마다:
  isExcluded = (payment.type == SPECIAL)
  exclusionReason = isExcluded
      ? "데이터 제공자(Massive) 분류 기준상 특별배당 — 정기 배당 지표 계산에서 제외됨"
      : null
```

- 정기/특별 각각의 **건수**도 요약으로 함께 노출한다
  (`regularCount`, `specialCount`) — 화면에서 "총 N건 중 M건이
  특별배당으로 제외됨"을 바로 보여줄 수 있게.
- 개별 지급 건의 순서를 바꾸거나 재계산하지 않는다 — DB에 저장된 값
  그대로 노출(raw amount, 분할 미조정 — `docs/decisions/03-split-adjustment.md`
  와 동일 원칙. 이 지표는 "분류 근거 확인"이 목적이라 분할 조정된
  금액을 보여줄 필요가 없다. 필요하면 화면에 "raw, 분할 미조정" 라벨).

## 2. 기준 시점

- 특정 시점(`asOf`) 파라미터가 없다 — 이 종목의 **전체 지급 이력**을
  그대로 보여주는 목록형 지표라, 다른 지표들과 달리 "기준일" 개념이
  없다.
- 정렬 기준은 프로젝트 전체와 동일하게 **ex-dividend date** 오름차순.

## 3. 반올림 방향

- 반올림 대상 없음 — `amount`는 DB에 저장된 `BigDecimal` 값을 그대로
  노출한다 (scale=6, 원본 그대로). 비율·백분율 계산이 없으므로 이 항목은
  해당 사항 없음.

## 4. 예외 케이스

| 상황 | 처리 |
|---|---|
| 티커 자체가 존재하지 않음 | `NoSuchElementException` — 기존 서비스들과 동일한 예외 패턴 재사용 |
| 지급 이력이 0건 | 에러 아님 — 빈 목록 + `regularCount=0`, `specialCount=0` 반환 |
| 특별배당이 하나도 없음(전부 REGULAR) | 정상 케이스 — `specialCount=0`, 목록의 모든 항목이 `isExcluded=false` |
| `DividendPayment.type`이 `SPECIAL`도 `REGULAR`도 아닌 값 | 발생 불가 — enum이라 컴파일 타임에 막히고, DB 저장 전 `MassiveDividendMapper.classify()`가 알 수 없는 코드에서 이미 예외를 던짐([[04-dividend-classification]]) |
| 스키마에 원본 제공자 코드(`CD`/`SC`)가 없어서 "근거"가 종목별로 다르지 못하고 고정 문구임 | 사용자 결정: 스키마 변경 없이 진행. 고정 문구로 충분 — 배포 후 실제로 사용자가 "왜"를 더 궁금해하면 그때 `rawProviderType` 컬럼 추가 재검토 |

## 5. CLAUDE.md 정합성 체크

- ✅ 서비스 계층은 DB만 읽음 — 외부 API 호출 없음
- ✅ 특별배당은 정기 배당과 분리해서 다룸 — 이 지표 자체가 그 분리를
  "보이게" 만드는 목적
- ✅ 계산 로직 없음(순수 조회+매핑)이므로 BigDecimal 연산 자체가 없음 —
  단, 노출하는 `amount` 필드는 원본이 이미 BigDecimal
- ✅ 투자 판단/추천 문구 없음 — "제외됨/근거"만 보여주고 "위험하다"는
  말하지 않음
