#!/usr/bin/env python3
"""
Twelve Data API 검증 스크립트 (KO / 코카콜라)

Finnhub(배당 이력/주가 시계열 403), Massive(주가 시계열 2년 제한 - 무료로는
docs/decisions/01-data-source.md 참고)에 이어 세 번째 후보로 검증한다.
표준 라이브러리만 사용한다.

사용법:
    export TWELVEDATA_TOKEN=your_token_here
    python3 check_twelvedata_api.py

확인 항목:
    1. 배당 이력    - GET /dividends
    2. 주가 시계열  - GET /time_series (interval=1day, 5~6년 요청해서 실제 깊이 확인)
    3. 분할 이력    - GET /splits

주의: Twelve Data 공식 문서(twelvedata.com/docs) 기준 dividends 엔드포인트는
`date`(배당 관련 날짜)와 `amount`만 제공하고, Massive처럼 ex_dividend_date와
pay_date를 분리해서 주지 않는다. 이 스크립트는 그 차이(필드 자체 부재 vs null)를
명시적으로 구분해서 보고한다.
"""

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, timedelta

SYMBOL = "KO"
BASE_URL = "https://api.twelvedata.com"


def get_token():
    token = os.environ.get("TWELVEDATA_TOKEN")
    if not token:
        print("ERROR: 환경변수 TWELVEDATA_TOKEN 이 설정되어 있지 않습니다.", file=sys.stderr)
        print('  예: export TWELVEDATA_TOKEN="your_token_here"', file=sys.stderr)
        sys.exit(1)
    return token


def call_api(path, params, token):
    query = dict(params)
    query["apikey"] = token
    url = f"{BASE_URL}{path}?{urllib.parse.urlencode(query)}"
    req = urllib.request.Request(url, headers={"User-Agent": "twelvedata-check/1.0"})
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
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


def check_key_presence(rows, fields):
    """필드가 아예 없는 경우(키 부재)와 있지만 null인 경우를 구분해서 센다."""
    missing_key = {f: 0 for f in fields}
    null_value = {f: 0 for f in fields}
    for row in rows:
        for f in fields:
            if f not in row:
                missing_key[f] += 1
            elif row.get(f) is None:
                null_value[f] += 1
    return missing_key, null_value


def is_error_response(status, data):
    if status != 200:
        return True
    if isinstance(data, dict) and data.get("status") == "error":
        return True
    return False


# ------------------------------------------------------------------
# 1. 배당 이력
# ------------------------------------------------------------------
def check_dividends(token):
    section("1) 배당 이력 (Dividend History) - GET /dividends")

    status, data = call_api("/dividends", {"symbol": SYMBOL}, token)
    print(f"HTTP status: {status}")

    if is_error_response(status, data):
        print(f"에러 응답: {data}")
        return "안 됨", f"HTTP {status} - {data}"

    if not isinstance(data, dict) or "dividends" not in data:
        print(f"예상치 못한 응답 형식: {data}")
        return "안 됨", "'dividends' 키 없음"

    rows = data.get("dividends") or []
    if len(rows) == 0:
        print(f"전체 응답: {data}")
        return "안 됨", "dividends 빈 배열"

    # 공식 문서 기준 필드: date, amount (pay_date 별도 없음)
    key_fields = ["date", "amount", "pay_date"]
    missing_key, null_value = check_key_presence(rows, key_fields)

    years = set()
    for row in rows:
        d = row.get("date")
        if d:
            try:
                years.add(int(str(d)[:4]))
            except ValueError:
                pass

    print(f"레코드 수: {len(rows)}건")
    print(f"커버 연도: {sorted(years)} (총 {len(years)}년치)")
    print(f"필드 자체가 없는 레코드 수: {missing_key}")
    print(f"필드는 있으나 null인 레코드 수: {null_value}")
    print("\n샘플 응답:")
    print(pretty(rows, 3))

    if missing_key["pay_date"] == len(rows):
        print("\n주의: pay_date 필드가 응답에 아예 존재하지 않습니다 "
              "(Massive는 이 필드를 제공했음 — 지급일이 필요한 계산에는 Twelve Data "
              "배당 이력만으로 부족할 수 있음).")

    has_date_and_amount = missing_key["date"] == 0 and missing_key["amount"] == 0

    if has_date_and_amount and missing_key["pay_date"] == 0 and len(years) >= 3:
        return "됨", f"{len(years)}년치, date/amount/pay_date 모두 존재"
    elif has_date_and_amount:
        return "부분적", f"date/amount는 있으나 pay_date 등 누락: missing_key={missing_key}"
    else:
        return "안 됨", f"핵심 필드(date/amount) 자체가 없음: {missing_key}"


# ------------------------------------------------------------------
# 2. 주가 시계열
# ------------------------------------------------------------------
def check_price_series(token):
    section("2) 주가 시계열 (일별 종가, 실제 확보 가능 기간 확인) - GET /time_series")

    today = date.today()
    # 문서 기준 무료 플랜 히스토리 깊이 제한이 명시돼 있지 않아, 여유 있게
    # 6년치를 요청해서 실제로 어디까지 오는지 직접 확인한다.
    from_date = today - timedelta(days=365 * 6)

    status, data = call_api(
        "/time_series",
        {
            "symbol": SYMBOL,
            "interval": "1day",
            "start_date": from_date.isoformat(),
            "end_date": today.isoformat(),
            "outputsize": 5000,
        },
        token,
    )
    print(f"요청 기간: {from_date.isoformat()} ~ {today.isoformat()} (6년 요청)")
    print(f"HTTP status: {status}")

    if is_error_response(status, data):
        print(f"에러 응답: {data}")
        return "안 됨", f"HTTP {status} - {data}"

    if not isinstance(data, dict) or "values" not in data:
        print(f"예상치 못한 응답 형식: {data}")
        return "안 됨", "'values' 키 없음"

    bars = data.get("values") or []
    if len(bars) == 0:
        print(f"전체 응답: {data}")
        return "안 됨", "values 빈 배열"

    null_counts = check_nulls = {
        f: sum(1 for b in bars if b.get(f) is None) for f in ("datetime", "close")
    }

    dates = [b["datetime"] for b in bars if b.get("datetime")]
    first_date, last_date = (min(dates), max(dates)) if dates else (None, None)

    if first_date and last_date:
        from datetime import datetime as dt
        span_days = (
            dt.strptime(last_date[:10], "%Y-%m-%d") - dt.strptime(first_date[:10], "%Y-%m-%d")
        ).days
    else:
        span_days = 0

    print(f"바(bar) 개수: {len(bars)}")
    print(f"실제 데이터 범위: {first_date} ~ {last_date} (약 {span_days/365:.1f}년)")
    print(f"null 필드 카운트 (datetime, close): {null_counts}")
    print("\n샘플 응답 (최대 3건):")
    print(pretty(bars, 3))

    if null_counts["close"] > 0:
        return "부분적", f"close에 null {null_counts['close']}건 존재"

    if span_days >= 365 * 5 - 10:
        return "됨", f"약 {span_days/365:.1f}년치 일별 종가 확인 (5년 이상)"
    elif span_days >= 365 * 3 - 10:
        return "됨", f"약 {span_days/365:.1f}년치 일별 종가 확인 (3년 이상)"
    elif len(bars) > 0:
        return "부분적", f"데이터는 오나 3년에 못 미침 (약 {span_days/365:.1f}년)"
    else:
        return "안 됨", "종가 데이터 없음"


# ------------------------------------------------------------------
# 3. 분할 이력
# ------------------------------------------------------------------
def check_splits(token):
    section("3) 분할 이력 (Stock Splits) - GET /splits")

    status, data = call_api("/splits", {"symbol": SYMBOL}, token)
    print(f"HTTP status: {status}")

    if is_error_response(status, data):
        print(f"에러 응답: {data}")
        return "안 됨", f"HTTP {status} - {data}"

    if not isinstance(data, dict) or "splits" not in data:
        print(f"예상치 못한 응답 형식: {data}")
        return "안 됨", "'splits' 키 없음"

    rows = data.get("splits") or []
    print(f"레코드 수: {len(rows)}건")

    if len(rows) == 0:
        print("splits가 빈 배열입니다. (KO는 2012년 이후 분할 이력이 없어 "
              "정상적으로 빈 배열일 수도 있음 — Massive 검증에서 2012-08-13 "
              "1:2 분할 1건만 확인됐던 것과 비교 필요)")
        print(f"전체 응답: {data}")
        return "부분적", "빈 배열 (엔드포인트 정상 동작, 실제 이력 유무는 Massive 결과와 대조 필요)"

    key_fields = ["date", "numerator", "denominator"]
    missing_key, null_value = check_key_presence(rows, key_fields)
    print(f"필드 자체가 없는 레코드 수: {missing_key}")
    print(f"필드는 있으나 null인 레코드 수: {null_value}")
    print("\n샘플 응답:")
    print(pretty(rows, 3))

    if all(c == 0 for c in missing_key.values()) and all(c == 0 for c in null_value.values()):
        return "됨", f"{len(rows)}건, 핵심 필드 모두 존재"
    else:
        return "부분적", f"일부 핵심 필드 누락/null: missing_key={missing_key}, null={null_value}"


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
