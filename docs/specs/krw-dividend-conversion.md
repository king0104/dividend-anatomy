# 환율 환산 실수령액 (KRW Dividend Conversion)

PROJECT.md 5.6절. [[us-withholding-tax]](5.5절)가 계산한 세후 USD
금액을 "배당 지급일 기준" 환율로 원화 환산한다. 신규 데이터
파이프라인이 필요한 작업 — `docs/decisions/07-fx-data-source.md`에서
Twelve Data forex 지원을 실제 API 호출로 검증 완료(기존
`TwelveDataClient` 그대로 재사용 가능, 실제 커버리지는 2007-10-11부터).

## 0. 스코프

- **USD→KRW 한 방향만 다룬다.** 프로젝트 유니버스가 전부 미국
  배당킹이라 다른 통화쌍은 스코프 밖([[us-withholding-tax]] 0절과
  같은 이유).
- **[[us-withholding-tax]]가 이미 계산한 `NetDividendEntry`(세전
  `grossAmount`, 세후 `netAmount`, 둘 다 USD·소수 2자리 반올림된 값)를
  입력으로 받는다.** 이 지표는 세금 계산을 다시 하지 않고, 그 결과에
  환율만 곱한다 — 두 서비스를 조합하는 상위 계층이다.
- **환율 적용 시점은 `payDate`(지급일)로 고정한다**(CLAUDE.md
  "환율 적용 시점은 배당 지급일 기준"). `exDividendDate`나
  `recordDate`로 대체하지 않는다 — `payDate`가 없으면 "환산 불가"로
  명시 처리한다(4절), 다른 날짜로 조용히 대체하지 않는다.
- **2007-10-11 이전 지급 건은 원천적으로 환율 데이터가 없다**
  ([[07-fx-data-source]]). 이건 버그가 아니라 실제 데이터 한계이므로
  "환율 데이터 없음"으로 명시 표시한다(4절) — KO 94건 중
  2007-10-11 이전 지급분(2003~2007년경, 약 18건 예상)은 항상 이
  경로를 탈 것으로 예상.

## 1. 계산식

### 1.1 입력 변수

| 변수 | 정의 |
|---|---|
| `netEntry` | [[us-withholding-tax]]의 `NetDividendEntry`(`exDividendDate`, `grossAmount`, `netAmount`, `type`) |
| `payDate` | 해당 지급 건의 `DividendPayment.payDate` — null일 수 있음 |
| `rate(d)` | 날짜 `d`에 대해 "가장 가까운 값" 원칙으로 조회한 USD→KRW 환율 — `ExchangeRateRepository.findTopByFromCurrencyAndToCurrencyAndDateLessThanEqualOrderByDateDesc("USD", "KRW", d)`. 없으면 조회 실패 |

### 1.2 원화 환산

```
payDate가 null이면:
    → 환산 불가, status = PAY_DATE_MISSING

payDate가 있는데 rate(payDate) 조회 실패(그 날짜 이전에 저장된 환율이 하나도 없음)면:
    → 환산 불가, status = NO_RATE_DATA_AVAILABLE

둘 다 있으면:
    grossAmountKrw = netEntry.grossAmount * rate(payDate)   // 세전 원화
    netAmountKrw   = netEntry.netAmount   * rate(payDate)   // 세후 원화
    status = CONVERTED
```

- `netEntry.grossAmount`/`netAmount`는 이미 [[us-withholding-tax]]에서
  소수 2자리로 반올림된 "최종" USD 값이다 — 이 지표는 그 위에 환율을
  곱하는 별도 계산 단계로 취급하고, USD 반올림 전 원시값으로
  거슬러 올라가 다시 계산하지 않는다(두 지표가 독립적으로
  명세·검증된다는 프로젝트 원칙 — [[us-withholding-tax]] 자체 스펙이
  이미 "이 값이 최종"이라고 확정했으므로).
- 곱셈은 `MathContext.DECIMAL64`로 계산한다(프로젝트 전체 관례).

## 2. 기준 시점

- **`payDate` 기준**(0절, 1.1절) — `exDividendDate`/`recordDate`가
  아니다. CLAUDE.md에 이미 고정된 원칙을 그대로 구현하는 것뿐,
  새로 정하는 게 아니다.
- 환율 조회는 "가장 가까운 값" 원칙(프로젝트 전체 관례,
  `PriceBarRepository.findTopByTickerAndDateLessThanEqualOrderByDateDesc`와
  동일한 패턴)을 그대로 따른다 — `payDate` 이전·근처의 가장 최근
  환율.
- `asOf` 파라미터 없음 — [[special-dividend-disclosure]],
  [[us-withholding-tax]]와 동일하게 종목의 전체 지급 이력을 한 번에
  보여주는 목록형 지표.

## 3. 반올림 방향

- **원화(KRW)는 소수점 없는 정수 단위로 반올림한다 —
  `RoundingMode.HALF_UP`, `scale=0`.** 이유: 원화는 최소 단위가
  "원"이고 소수 원 단위는 실제 통화 관례에 없다(달러의 센트처럼
  소수 2자리를 쓰는 미국 통화 관례와 다름 — [[us-withholding-tax]]의
  `scale=2`는 이 지표에 그대로 적용하지 않는다).
- USD 쪽 값(`grossAmount`, `netAmount`)은 이미 [[us-withholding-tax]]에서
  반올림된 값을 그대로 통과시키며, 이 지표에서 다시 반올림하지 않는다.

## 4. 예외 케이스

| 상황 | 처리 |
|---|---|
| 티커 자체가 존재하지 않음 | `NoSuchElementException` — [[us-withholding-tax]]가 이미 던지는 예외를 그대로 전파 |
| `Ticker.currency != "USD"` | `IllegalStateException` — [[us-withholding-tax]]가 이미 던지는 예외를 그대로 전파(이 지표가 그 서비스를 조합하므로 자동으로 상속) |
| 지급 이력이 0건 | 에러 아님 — 빈 목록 |
| `payDate`가 null | 이 지급 건만 `status=PAY_DATE_MISSING`, `exchangeRate`/`grossAmountKrw`/`netAmountKrw` 전부 `null`. 다른 지급 건 계산에는 영향 없음(지급 건 단위로 독립 판정) |
| 해당 `payDate` 이전에 저장된 환율이 전혀 없음(2007-10-11 이전, 또는 아직 수집 안 한 구간) | `status=NO_RATE_DATA_AVAILABLE`, 필드 전부 `null`. `payDate` 존재 여부와 별개의 실패 사유이므로 상태를 구분해서 노출(조용히 하나로 뭉뚱그리지 않음, CLAUDE.md) |
| 환율 데이터가 있지만 아주 오래돼서(`payDate`와 실제 조회된 환율 날짜 사이 간격이 큼) 부정확할 가능성 | 이 스펙에서는 간격 크기와 무관하게 "가장 가까운 값"을 그대로 쓴다 — 프로젝트 전체가 이미 채택한 원칙(가격도 동일)이라 이 지표만 다른 기준을 두지 않는다 |
| 특별배당이 섞여 있음 | [[us-withholding-tax]]와 동일 — 정기/특별 구분 없이 전부 환산 대상([[us-withholding-tax]] 0절 그대로 상속) |

## 5. CLAUDE.md 정합성 체크

- ✅ `BigDecimal`만 사용(ArchUnit 강제)
- ✅ 환율 적용 시점 "배당 지급일 기준"을 그대로 구현, 다른 시점으로
  대체하지 않음
- ✅ 세금은 이미 [[us-withholding-tax]]에서 마지막 단계로 적용된
  값을 입력받을 뿐, 이 지표가 세금 계산을 다시 하지 않음
- ✅ 서비스 계층은 DB만 읽음 — 환율 데이터는 별도 수집 계층
  (`ingestion.twelvedata`)에서만 외부 API 호출, 이 지표의 서비스는
  `ExchangeRateRepository`만 읽음
- ✅ 데이터 불완전 시 조용히 넘어가지 않음 — `payDate` 누락과 환율
  데이터 없음을 서로 다른 상태로 명시 구분해서 노출
- ✅ 투자 판단/추천 문구 없음
