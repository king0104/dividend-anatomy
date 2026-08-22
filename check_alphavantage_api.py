#!/usr/bin/env python3
"""
Alpha Vantage API 검증 스크립트 (KO / 코카콜라)

Massive(주가 시계열 2년 제한), Twelve Data(배당/분할 유료 전용)에 이어 네 번째
후보. docs/decisions/01-data-source.md 참고 — 공개 데모 키(IBM 한정)로는 이미
DIVIDENDS/SPLITS/TIME_SERIES_DAILY 필드 구조까지 확인했고, 이 스크립트는 실제
발급받은 무료 키로 KO를 대상으로 검증한다. 표준 라이브러리만 사용한다.

사용법:
    export ALPHAVANTAGE_TOKEN=your_token_here
    python3 check_alphavantage_api.py

확인 항목:
    1. 배당 이력    - function=DIVIDENDS
    2. 주가 시계열  - function=TIME_SERIES_DAILY (outputsize=full)
    3. 분할 이력    - function=SPLITS

주의: Alpha Vantage는 요청 자체가 막히면(요율 제한, 잘못된 키 등) HTTP 상태
코드는 200을 주면서 본문에 "Error Message"/"Note"/"Information" 키로 에러를
담아 보낸다. 이 스크립트는 이 세 키를 명시적으로 확인한다.
"""

import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime

SYMBOL = "KO"
BASE_URL = "https://www.alphavantage.co/query"


def get_token():
    token = os.environ.get("ALPHAVANTAGE_TOKEN")
    if not token:
        print("ERROR: 환경변수 ALPHAVANTAGE_TOKEN 이 설정되어 있지 않습니다.", file=sys.stderr)
        print('  예: export ALPHAVANTAGE_TOKEN="your_token_here"', file=sys.stderr)
        sys.exit(1)
    return token


def call_api(params, token):
    query = dict(params)
    query["apikey"] = token
    url = f"{BASE_URL}?{urllib.parse.urlencode(query)}"
    req = urllib.request.Request(url, headers={"User-Agent": "alphavantage-check/1.0"})
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            result = resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        try:
            parsed = json.loads(raw)
        except json.JSONDecodeError:
            parsed = raw
        result = e.code, parsed
    except urllib.error.URLError as e:
        result = None, {"error": str(e)}
    time.sleep(15)  # 무료 플랜 초당/분당 요청 제한 대응
    return result


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
    counts = {f: 0 for f in fields}
    for row in rows:
        for f in fields:
            if row.get(f) is None:
                counts[f] += 1
    return counts


def embedded_error(data):
    """Alpha Vantage는 HTTP 200이면서 본문에 에러를 담아 보낸다."""
    if not isinstance(data, dict):
        return None
    for key in ("Error Message", "Note", "Information"):
        if key in data:
            return f"{key}: {data[key]}"
    return None


# ------------------------------------------------------------------
# 1. 배당 이력
# ------------------------------------------------------------------
def check_dividends(token):
    section("1) 배당 이력 (Dividend History) - function=DIVIDENDS")

    status, data = call_api({"function": "DIVIDENDS", "symbol": SYMBOL}, token)
    print(f"HTTP status: {status}")

    err = embedded_error(data)
    if err:
        print(f"응답 내 에러/안내 메시지: {err}")
        print(f"전체 응답: {data}")
        return "안 됨", err

    if not isinstance(data, dict) or "data" not in data:
        print(f"예상치 못한 응답 형식: {data}")
        return "안 됨", "'data' 키 없음"

    rows = data.get("data") or []
    if len(rows) == 0:
        print(f"전체 응답: {data}")
        return "안 됨", "data 빈 배열"

    key_fields = ["ex_dividend_date", "declaration_date", "record_date", "payment_date", "amount"]
    null_counts = check_nulls(rows, key_fields)

    years = set()
    for row in rows:
        d = row.get("ex_dividend_date")
        if d:
            try:
                years.add(int(str(d)[:4]))
            except ValueError:
                pass

    print(f"레코드 수: {len(rows)}건")
    print(f"커버 연도: {sorted(years)} (총 {len(years)}년치)")
    print(f"null 필드 카운트: {null_counts}")
    print("\n샘플 응답:")
    print(pretty(rows, 3))

    if all(c == 0 for c in null_counts.values()) and len(years) >= 3:
        return "됨", f"{len(years)}년치, 핵심 필드(ex/declaration/record/payment date, amount) 모두 존재"
    elif null_counts["ex_dividend_date"] == 0 and null_counts["amount"] == 0:
        missing = [f for f, c in null_counts.items() if c > 0]
        return "부분적", f"ex_dividend_date/amount는 있으나 일부 필드 null: {missing}"
    else:
        return "부분적", f"일부 핵심 필드 null 존재: {null_counts}"


# ------------------------------------------------------------------
# 2. 주가 시계열
# ------------------------------------------------------------------
def check_price_series(token):
    section("2) 주가 시계열 (일별 종가, outputsize=full) - function=TIME_SERIES_DAILY")

    status, data = call_api(
        {"function": "TIME_SERIES_DAILY", "symbol": SYMBOL, "outputsize": "full"},
        token,
    )
    print(f"HTTP status: {status}")

    err = embedded_error(data)
    if err:
        print(f"응답 내 에러/안내 메시지: {err}")
        return "안 됨", err

    if not isinstance(data, dict) or "Time Series (Daily)" not in data:
        print(f"예상치 못한 응답 형식: {list(data.keys()) if isinstance(data, dict) else data}")
        return "안 됨", "'Time Series (Daily)' 키 없음"

    series = data["Time Series (Daily)"]
    if not series:
        return "안 됨", "빈 시계열"

    dates = sorted(series.keys())
    first_date, last_date = dates[0], dates[-1]
    span_days = (
        datetime.strptime(last_date, "%Y-%m-%d") - datetime.strptime(first_date, "%Y-%m-%d")
    ).days

    null_close = sum(1 for d in dates if series[d].get("4. close") is None)

    print(f"바(bar) 개수: {len(dates)}")
    print(f"실제 데이터 범위: {first_date} ~ {last_date} (약 {span_days/365:.1f}년)")
    print(f"종가(4. close) null 개수: {null_close}")
    print("\n샘플 응답 (최신 3건):")
    sample = {d: series[d] for d in dates[-3:]}
    print(pretty(sample, 3))

    if null_close > 0:
        return "부분적", f"종가에 null {null_close}건 존재"

    if span_days >= 365 * 5 - 10:
        return "됨", f"약 {span_days/365:.1f}년치 일별 종가 확인 (5년 이상)"
    elif span_days >= 365 * 3 - 10:
        return "됨", f"약 {span_days/365:.1f}년치 일별 종가 확인 (3년 이상)"
    else:
        return "부분적", f"데이터는 오나 3년에 못 미침 (약 {span_days/365:.1f}년)"


# ------------------------------------------------------------------
# 3. 분할 이력
# ------------------------------------------------------------------
def check_splits(token):
    section("3) 분할 이력 (Stock Splits) - function=SPLITS")

    status, data = call_api({"function": "SPLITS", "symbol": SYMBOL}, token)
    print(f"HTTP status: {status}")

    err = embedded_error(data)
    if err:
        print(f"응답 내 에러/안내 메시지: {err}")
        return "안 됨", err

    if not isinstance(data, dict) or "data" not in data:
        print(f"예상치 못한 응답 형식: {data}")
        return "안 됨", "'data' 키 없음"

    rows = data.get("data") or []
    print(f"레코드 수: {len(rows)}건")

    if len(rows) == 0:
        print("data가 빈 배열입니다. (Massive 검증에서 KO는 2012-08-13 1:2 분할"
              " 1건이 확인됐으므로, 여기서도 최소 1건은 나와야 정상)")
        print(f"전체 응답: {data}")
        return "부분적", "빈 배열 (Massive 결과와 대조 필요 — 실제로는 1건 있어야 함)"

    key_fields = ["effective_date", "split_factor"]
    null_counts = check_nulls(rows, key_fields)
    print(f"null 필드 카운트: {null_counts}")
    print("\n샘플 응답:")
    print(pretty(rows, 3))

    if all(c == 0 for c in null_counts.values()):
        return "됨", f"{len(rows)}건, 핵심 필드 모두 존재"
    else:
        return "부분적", f"일부 핵심 필드 null 존재: {null_counts}"


# ------------------------------------------------------------------
def main():
    token = get_token()

    results = {}
    results["배당 이력"] = check_dividends(token)
    results["주가 시계열"] = check_price_series(token)
    results["분할 이력"] = check_splits(token)

    section("요약")
    for name, (verdict, detail) in results.items():
        print(f"- {name}: [{verdict}] {detail}")


if __name__ == "__main__":
    main()
