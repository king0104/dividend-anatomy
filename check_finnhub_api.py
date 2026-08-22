#!/usr/bin/env python3
"""
Finnhub API 검증 스크립트 (KO / 코카콜라)

Java 프로젝트를 만들기 전에, Finnhub API가 실제로 필요한 데이터를
필요한 형태로 주는지 빠르게 확인한다. 표준 라이브러리만 사용한다
(외부 패키지 설치 없이 바로 실행 가능).

사용법:
    export FINNHUB_TOKEN=your_token_here
    python3 check_finnhub_api.py

확인 항목:
    1. 배당 이력    - GET /stock/dividend
    2. 주가 시계열  - GET /stock/candle
    3. 기본 재무 지표 - GET /stock/metric
"""

import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, datetime, timedelta

SYMBOL = "KO"
BASE_URL = "https://finnhub.io/api/v1"


def get_token():
    token = os.environ.get("FINNHUB_TOKEN")
    if not token:
        print("ERROR: 환경변수 FINNHUB_TOKEN 이 설정되어 있지 않습니다.", file=sys.stderr)
        print('  예: export FINNHUB_TOKEN="your_token_here"', file=sys.stderr)
        sys.exit(1)
    return token


def call_api(path, params, token):
    """Finnhub API를 호출하고 (http_status, parsed_body)를 반환한다."""
    query = dict(params)
    query["token"] = token
    url = f"{BASE_URL}{path}?{urllib.parse.urlencode(query)}"
    req = urllib.request.Request(url, headers={"User-Agent": "finnhub-check/1.0"})
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            body = resp.read().decode("utf-8")
            return resp.status, json.loads(body)
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        try:
            parsed = json.loads(raw)
        except json.JSONDecodeError:
            parsed = raw
        return e.code, parsed
    except urllib.error.URLError as e:
        return None, {"error": str(e)}


def section(title):
    print("\n" + "=" * 72)
    print(title)
    print("=" * 72)


def pretty(obj, limit=3):
    if isinstance(obj, list):
        shown = obj[:limit]
        out = json.dumps(shown, indent=2, ensure_ascii=False)
        if len(obj) > limit:
            out += f"\n... (총 {len(obj)}건 중 {limit}건만 표시)"
        return out
    return json.dumps(obj, indent=2, ensure_ascii=False)


def check_nulls(rows, fields):
    """각 필드별로 null(None)인 레코드 수를 센다."""
    counts = {f: 0 for f in fields}
    for row in rows:
        for f in fields:
            if row.get(f) is None:
                counts[f] += 1
    return counts


# ------------------------------------------------------------------
# 1. 배당 이력
# ------------------------------------------------------------------
def check_dividends(token):
    section("1) 배당 이력 (Dividend History) - GET /stock/dividend")

    today = date.today()
    from_date = today.replace(year=today.year - 6)  # 여유있게 6년치 요청

    status, data = call_api(
        "/stock/dividend",
        {"symbol": SYMBOL, "from": from_date.isoformat(), "to": today.isoformat()},
        token,
    )
    print(f"요청 기간: {from_date.isoformat()} ~ {today.isoformat()}")
    print(f"HTTP status: {status}")

    if status != 200:
        print(f"에러 응답: {data}")
        return "안 됨", f"HTTP {status} - {data}"

    if not isinstance(data, list):
        print(f"예상치 못한 응답 형식: {data}")
        return "안 됨", "리스트가 아닌 응답"

    if len(data) == 0:
        print("응답이 빈 배열입니다. (배당 이력 없음 또는 심볼/기간 문제)")
        return "안 됨", "빈 배열"

    # Finnhub dividend 필드: symbol, date(ex-div date), amount, adjustedAmount,
    # payDate, recordDate, declarationDate, currency
    key_fields = ["date", "payDate", "amount"]
    null_counts = check_nulls(data, key_fields)

    years = set()
    for row in data:
        d = row.get("date")
        if d:
            try:
                years.add(int(str(d)[:4]))
            except ValueError:
                pass

    print(f"레코드 수: {len(data)}건")
    print(f"커버 연도: {sorted(years)} (총 {len(years)}년치)")
    print(f"null 필드 카운트: {null_counts}")
    print("\n샘플 응답:")
    print(pretty(data, 3))

    has_all_key_fields = all(c == 0 for c in null_counts.values())

    if has_all_key_fields and len(years) >= 3:
        return "됨", f"{len(years)}년치, ex-date/payDate/amount 모두 존재"
    elif null_counts["date"] == 0 and null_counts["amount"] == 0:
        missing = [f for f, c in null_counts.items() if c > 0]
        return "부분적", f"ex-date/amount는 있으나 일부 필드 null: {missing}"
    else:
        return "부분적", f"일부 핵심 필드 null 존재: {null_counts}"


# ------------------------------------------------------------------
# 2. 주가 시계열
# ------------------------------------------------------------------
def check_candles(token):
    section("2) 주가 시계열 (최소 3년 일별 종가) - GET /stock/candle")

    now = datetime.now()
    to_ts = int(time.mktime(now.timetuple()))
    from_dt = now - timedelta(days=365 * 3 + 30)  # 3년 + 여유 버퍼
    from_ts = int(time.mktime(from_dt.timetuple()))

    status, data = call_api(
        "/stock/candle",
        {"symbol": SYMBOL, "resolution": "D", "from": from_ts, "to": to_ts},
        token,
    )
    print(f"요청 기간(unix): {from_ts} ~ {to_ts}  "
          f"({from_dt.date().isoformat()} ~ {now.date().isoformat()})")
    print(f"HTTP status: {status}")

    if status == 403:
        print(f"응답: {data}")
        print("\n주의: Finnhub는 무료(free) 플랜에서 미국 주식 /stock/candle "
              "엔드포인트 접근을 차단하는 경우가 많습니다. 403은 '유료 플랜 필요'를 의미할 수 있습니다.")
        return "안 됨", "HTTP 403 - 무료 플랜에서 접근 제한 (유료 구독 필요 가능성)"

    if status != 200:
        print(f"에러 응답: {data}")
        return "안 됨", f"HTTP {status} - {data}"

    if not isinstance(data, dict):
        print(f"예상치 못한 응답 형식: {data}")
        return "안 됨", "딕셔너리가 아닌 응답"

    s = data.get("s")
    print(f"응답 status 필드(s): {s}")

    if s == "no_data":
        print(f"전체 응답: {data}")
        return "안 됨", "s == 'no_data' (해당 기간 데이터 없음)"

    if s != "ok":
        print(f"전체 응답: {data}")
        return "안 됨", f"s == '{s}' (정상 아님)"

    closes = data.get("c") or []
    timestamps = data.get("t") or []
    print(f"일별 종가(c) 개수: {len(closes)}")
    print(f"타임스탬프(t) 개수: {len(timestamps)}")

    if timestamps:
        first_date = datetime.fromtimestamp(timestamps[0]).date().isoformat()
        last_date = datetime.fromtimestamp(timestamps[-1]).date().isoformat()
        span_days = (datetime.fromtimestamp(timestamps[-1]) - datetime.fromtimestamp(timestamps[0])).days
        print(f"실제 데이터 범위: {first_date} ~ {last_date} (약 {span_days/365:.1f}년)")
    else:
        span_days = 0

    sample_preview = {
        "c (종가) 샘플": closes[:3],
        "t (타임스탬프) 샘플": timestamps[:3],
        "s": s,
    }
    print("\n샘플 응답:")
    print(pretty(sample_preview, 3))

    if len(closes) > 0 and None in closes:
        return "부분적", "종가 배열에 null 값 포함"

    if span_days >= 365 * 3 - 10:  # 약간의 오차 허용
        return "됨", f"{span_days/365:.1f}년치 일별 종가 확인"
    elif len(closes) > 0:
        return "부분적", f"데이터는 오나 3년에 못 미침 (약 {span_days/365:.1f}년)"
    else:
        return "안 됨", "종가 데이터 없음"


# ------------------------------------------------------------------
# 3. 기본 재무 지표 (배당성향 등)
# ------------------------------------------------------------------
def check_financials(token):
    section("3) 기본 재무 지표 (배당성향 등) - GET /stock/metric")

    status, data = call_api(
        "/stock/metric",
        {"symbol": SYMBOL, "metric": "all"},
        token,
    )
    print(f"HTTP status: {status}")

    if status != 200:
        print(f"에러 응답: {data}")
        return "안 됨", f"HTTP {status} - {data}"

    if not isinstance(data, dict) or "metric" not in data:
        print(f"예상치 못한 응답 형식: {data}")
        return "안 됨", "'metric' 키 없음"

    metric = data.get("metric") or {}
    if not metric:
        print("metric 객체가 비어 있습니다.")
        return "안 됨", "metric 객체 비어있음"

    # 관심 지표: 배당성향, 배당수익률, 주당배당금 등
    fields_of_interest = [
        "payoutRatioTTM",
        "payoutRatioAnnual",
        "dividendYieldIndicatedAnnual",
        "dividendPerShareTTM",
        "dividendPerShareAnnual",
        "currentDividendYieldTTM",
        "epsTTM",
        "peTTM",
    ]

    present = {}
    missing_or_null = []
    for f in fields_of_interest:
        if f in metric:
            val = metric[f]
            present[f] = val
            if val is None:
                missing_or_null.append(f)
        else:
            missing_or_null.append(f + " (키 자체 없음)")

    print(f"metric 객체 내 전체 키 개수: {len(metric)}")
    print(f"\n관심 지표 존재 여부:")
    print(pretty(present))
    if missing_or_null:
        print(f"\n누락되었거나 null인 지표: {missing_or_null}")

    payout_ratio_found = any(
        metric.get(k) is not None for k in ("payoutRatioTTM", "payoutRatioAnnual")
    )

    if payout_ratio_found and len(missing_or_null) <= 2:
        return "됨", "배당성향 및 대부분 관심 지표 존재"
    elif payout_ratio_found:
        return "부분적", f"배당성향은 있으나 일부 지표 누락/null: {missing_or_null}"
    else:
        return "안 됨", "payoutRatioTTM / payoutRatioAnnual 모두 없거나 null"


# ------------------------------------------------------------------
def main():
    token = get_token()

    results = {}
    results["배당 이력"] = check_dividends(token)
    results["주가 시계열"] = check_candles(token)
    results["기본 재무 지표"] = check_financials(token)

    section("요약")
    for name, (verdict, detail) in results.items():
        print(f"- {name}: [{verdict}] {detail}")


if __name__ == "__main__":
    main()
