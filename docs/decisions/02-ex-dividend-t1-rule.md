# 02. 배당락일(ex-dividend date) T+1 규칙 — SEC 원문 확인

**날짜**: 2026-08-23

## 왜 확인이 필요했나

CLAUDE.md에 이미 "배당락일은 T+1 규칙을 따른다"고 적어뒀지만, 이건 Day 1에
일반 상식 수준으로 적어둔 것이었고 원문을 직접 확인하지 않았다. 배당
수익률 계산의 핵심 전제("배당락일 전날 종가 기준으로 보유해야 배당을
받는다")가 틀리면 이 프로젝트의 모든 계산이 틀어지므로, 구현 전에 SEC
원문으로 확정해둔다.

## 결론

**2024-05-28 T+1 결제 전환 이후, 미국 주식의 배당락일(ex-dividend date)은
기준일(record date)과 "같은 날"이다.** T+2 시절에는 배당락일이 기준일의
1영업일 *전*이었다.

```
보유 여부 판정: 배당락일 "전날" 종가 기준으로 보유하고 있어야 배당을 받는다
              = 기준일(record date) 하루 전 영업일 종가 기준
              (T+1 체제에서는 배당락일 = 기준일이므로, 결과적으로
               "기준일 전날"과 "배당락일 전날"이 같은 날을 가리킨다)
```

## 원문 근거

**Securities Exchange Act Release No. 34-99871; File No. SR-NYSE-2024-19**
(NYSE가 2024-03-25 SEC에 제출, 2024-04-05 Federal Register 공고).
`https://www.federalregister.gov/documents/2024/04/05/2024-07219/` —
PDF 원문을 직접 파싱해 확보(WebFetch가 SEC.gov 자체는 rate-limit/bot
차단, federalregister.gov의 public-inspection PDF는 접근 가능했음).

개정 전 NYSE Rule 235:

> "transactions in stocks shall be ex-dividend or ex-rights on the
> business day preceding the record date fixed by the corporation or the
> date of the closing of transfer books"

개정 내용 (T+1 반영):

> "The Exchange proposes to delete the phrase 'the business day
> preceding,' such that the rule would provide that these transactions
> would be ex-dividend or ex-rights on the record date."

전환 시점 관련 명시:

> "With the implementation of the T+1 settlement cycle ... the
> ex-dividend date for 'normal' distributions will be the same business
> day as the record date. Accordingly, the Exchange proposes that
> Wednesday, May 29, 2024 would be the first date to which the proposed
> rules described herein would apply (i.e., the first record date to
> which the new ex-dividend date rationale will be applied)."

전환 구간 예시 표 (문서 원문 그대로):

| Record Date | Ex-Dividend Date (전환 당시 규칙) |
|---|---|
| 2024-05-24 (금) | 2024-05-23 (구 규칙 — T+2 시절 방식) |
| 2024-05-28 (화) | 2024-05-24 (구 규칙 — 아직 적용 전) |
| 2024-05-29 (수) | 2024-05-29 (신 규칙 — 기준일과 동일, 최초 적용일) |

즉 **2024-05-29 이후 기준일(record date)부터** "배당락일 = 기준일" 규칙이
실제로 적용된다. 그 이전 배당(2024-05-28 이전 기준일)은 T+2 시절 규칙
그대로다.

## 구현에 대한 함의

- Massive(배당 이력 제공자)가 주는 배당 데이터에 `ex_dividend_date`와
  `record_date`가 각각 필드로 있는지, 아니면 하나만 있는지 확인 필요 —
  둘 다 있다면 **2024-05-29 이후 배당은 두 값이 같아야 정상**이고, 다르면
  데이터 이상으로 로그를 남긴다 (CLAUDE.md "데이터 불완전 표시" 원칙).
- 2024-05-29 이전 배당 이력을 계산에 포함할 경우, 그 구간은 "배당락일 =
  기준일 1영업일 전" 규칙을 별도로 적용해야 한다 — 전체 이력에 단일
  규칙을 소급 적용하면 안 된다. (다만 이 프로젝트는 가격 데이터가
  실질적으로 2~6년 구간이라 대부분 T+1 구간에 들어올 가능성이 높다 —
  실제 데이터 확인 시 재검토.)
- "인터넷 자료에 T+2 시절 규칙이 섞여 있다"([[00-ai-harness]] 참고)는
  우려가 실제로 맞았다 — AI 학습 데이터 대부분이 2024-05-28 이전
  기준이므로, 이 문서가 없었다면 구 규칙으로 구현했을 가능성이 높다.

## 남은 것

- Nasdaq, 각 거래소(NYSE American, NYSE Arca 등)도 각자 별도 규칙 개정을
  제출했다 (검색 중 확인된 파일: SR-NYSE American, SR-NYSEArca-2024 등).
  NYSE 본소 규칙과 실질적으로 동일한 내용으로 보이나, 이 프로젝트가
  다루는 종목이 어느 거래소 소속이든 실무상 결과는 동일 — 별도 원문 확인은
  생략.
- 특별배당(special dividend)의 배당락일 처리가 이 규칙과 동일한지는 확인
  안 함 — 특별배당은 어차피 CLAUDE.md 규칙상 정기 배당과 분리해서 다루므로
  구현 시점에 별도 확인.
