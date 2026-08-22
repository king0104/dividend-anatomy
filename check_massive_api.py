#!/usr/bin/env python3
"""
Massive API 검증 스크립트 (KO / 코카콜라)

Finnhub 무료 플랜에서 /stock/dividend, /stock/candle 이 모두 403으로 막혀
(docs/decisions/01-data-source.md 참고) 대안으로 Massive(구 Polygon.io,
2025-10-30 리브랜딩)를 검증한다. 표준 라이브러리만 사용한다.

베이스 URL: 리브랜딩 후 신규 도메인은 api.massive.com 이지만, 기존
api.polygon.io 도 당분간 동일하게 동작한다(엔드포인트/인증 방식 변경 없음).
필요 시 환경변수 MASSIVE_BASE_URL 로 덮어쓸 수 있다.

사용법:
    export POLYGON_TOKEN=your_token_here
    python3 check_massive_api.py

확인 항목:
    1. 배당 이력    - GET /v3/reference/dividends
    2. 주가 시계열  - GET /v2/aggs/ticker/{ticker}/range/1/day/{from}/{to} (adjusted=true)
    3. 분할 이력    - GET /v3/reference/splits
"""

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, datetime, timedelta

SYMBOL = "KO"
DEFAULT_BASE_URL = "https://api.massive.com"


def get_token():
    token = os.environ.get("POLYGON_TOKEN")
    if not token:
        print("ERROR: 환경변수 POLYGON_TOKEN 이 설정되어 있지 않습니다.", file=sys.stderr)
        print('  예: export POLYGON_TOKEN="your_token_here"', file=sys.stderr)
        sys.exit(1)
    return token


def get_base_url():
    return os.environ.get("MASSIVE_BASE_URL", DEFAULT_BASE_URL)


def call_api(base_url, path, params, token):
    """Massive/Polygon API를 호출하고 (http_status, parsed_body)를 반환한다."""
    query = dict(params)
    query["apiKey"] = token
    url = f"{base_url}{path}?{urllib.parse.urlencode(query)}"
    req = urllib.request.Request(url, headers={"User-Agent": "massive-check/1.0"})
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
    counts = {f: 0 for f in fields}
    for row in rows:
        for f in fields:
            if row.get(f) is None:
                counts[f] += 1
    return counts


def is_free_plan_blocked(status, data):
    if status == 403:
        return True
    if status == 200 and isinstance(data, dict) and data.get("status") == "NOT_AUTHORIZED":
        return True
    return False


# ------------------------------------------------------------------
# 1. 배당 이력
# ------------------------------------------------------------------
def check_dividends(base_url, token):
    section("1) 배당 이력 (Dividend History) - GET /v3/reference/dividends")

    status, data = call_api(
        base_url,
        "/v3/reference/dividends",
        {"ticker": SYMBOL, "limit": 1000, "order": "asc", "sort": "ex_dividend_date"},
        token,
    )
    print(f"HTTP status: {status}")

    if is_free_plan_blocked(status, data):
        print(f"응답: {data}")
        print("\n주의: 403 또는 NOT_AUTHORIZED는 무료(Basic) 플랜에서 해당 엔드포인트/"
              "데이터 범위 접근이 제한됨을 의미할 수 있습니다.")
        return "안 됨", f"HTTP {status} - 무료 플랜 접근 제한 가능성"

    if status != 200:
        print(f"에러 응답: {data}")
        return "안 됨", f"HTTP {status} - {data}"

    if not isinstance(data, dict) or "results" not in data:
        print(f"예상치 못한 응답 형식: {data}")
        return "안 됨", "'results' 키 없음"

    rows = data.get("results") or []
    if len(rows) == 0:
        print(f"전체 응답: {data}")
        return "안 됨", "results 빈 배열"

    # Polygon/Massive dividend 필드:
    # cash_amount, currency, declaration_date, dividend_type, ex_dividend_date,
    # frequency, pay_date, record_date, ticker
    key_fields = ["ex_dividend_date", "pay_date", "cash_amount", "frequency"]
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
    if data.get("next_url"):
        print(f"참고: next_url 존재 → 서버에 더 많은 레코드가 있음 (페이지네이션 미적용, {len(rows)}건만 확인)")
    print("\n샘플 응답:")
    print(pretty(rows, 3))

    has_ex_and_amount = null_counts["ex_dividend_date"] == 0 and null_counts["cash_amount"] == 0
    has_all_key_fields = all(c == 0 for c in null_counts.values())

    if has_all_key_fields and len(years) >= 3:
        return "됨", f"{len(years)}년치, ex_dividend_date/pay_date/cash_amount/frequency 모두 존재"
    elif has_ex_and_amount:
        missing = [f for f, c in null_counts.items() if c > 0]
        return "부분적", f"ex_dividend_date/cash_amount는 있으나 일부 필드 null: {missing}"
    else:
        return "부분적", f"일부 핵심 필드 null 존재: {null_counts}"


# ------------------------------------------------------------------
# 2. 주가 시계열
# ------------------------------------------------------------------
def check_price_series(base_url, token):
    section("2) 주가 시계열 (최소 3년, adjusted=true) - GET /v2/aggs/ticker/.../range/1/day/...")

    today = date.today()
    from_date = today - timedelta(days=365 * 3 + 30)  # 3년 + 여유 버퍼

    path = f"/v2/aggs/ticker/{SYMBOL}/range/1/day/{from_date.isoformat()}/{today.isoformat()}"
    status, data = call_api(
        base_url,
        path,
        {"adjusted": "true", "sort": "asc", "limit": 50000},
        token,
    )
    print(f"요청 기간: {from_date.isoformat()} ~ {today.isoformat()}")
    print(f"HTTP status: {status}")

    if is_free_plan_blocked(status, data):
        print(f"응답: {data}")
        print("\n주의: 403 또는 NOT_AUTHORIZED는 무료(Basic) 플랜에서 이 기간/"
              "adjusted 옵션에 대한 접근이 제한됨을 의미할 수 있습니다.")
        return "안 됨", f"HTTP {status} - 무료 플랜 접근 제한 가능성"

    if status != 200:
        print(f"에러 응답: {data}")
        return "안 됨", f"HTTP {status} - {data}"

    if not isinstance(data, dict):
        print(f"예상치 못한 응답 형식: {data}")
        return "안 됨", "딕셔너리가 아닌 응답"

    api_status = data.get("status")
    result_count = data.get("resultsCount")
    print(f"응답 status 필드: {api_status}, resultsCount: {result_count}")

    bars = data.get("results") or []
    if len(bars) == 0:
        print(f"전체 응답: {data}")
        return "안 됨", f"results 없음 (status={api_status})"

    # bar 필드: t(ms), o, h, l, c, v, vw, n
    null_counts = check_nulls(bars, ["t", "c"])
    timestamps = [b["t"] for b in bars if b.get("t") is not None]

    if timestamps:
        first_date = datetime.fromtimestamp(min(timestamps) / 1000).date().isoformat()
        last_date = datetime.fromtimestamp(max(timestamps) / 1000).date().isoformat()
        span_days = (
            datetime.fromtimestamp(max(timestamps) / 1000)
            - datetime.fromtimestamp(min(timestamps) / 1000)
        ).days
    else:
        first_date = last_date = None
        span_days = 0

    print(f"바(bar) 개수: {len(bars)}")
    print(f"실제 데이터 범위: {first_date} ~ {last_date} (약 {span_days/365:.1f}년)")
    print(f"null 필드 카운트 (t, c): {null_counts}")
    print("\n샘플 응답 (최대 3건):")
    print(pretty(bars, 3))

    if null_counts["c"] > 0:
        return "부분적", f"종가(c)에 null {null_counts['c']}건 존재"

    if span_days >= 365 * 3 - 10:
        return "됨", f"{span_days/365:.1f}년치 일별 종가(adjusted) 확인"
    elif len(bars) > 0:
        return "부분적", f"데이터는 오나 3년에 못 미침 (약 {span_days/365:.1f}년)"
    else:
        return "안 됨", "종가 데이터 없음"


# ------------------------------------------------------------------
# 3. 분할 이력
# ------------------------------------------------------------------
def check_splits(base_url, token):
    section("3) 분할 이력 (Stock Splits) - GET /v3/reference/splits")

    status, data = call_api(
        base_url,
        "/v3/reference/splits",
        {"ticker": SYMBOL, "limit": 1000, "order": "asc", "sort": "execution_date"},
        token,
    )
    print(f"HTTP status: {status}")

    if is_free_plan_blocked(status, data):
        print(f"응답: {data}")
        print("\n주의: 403 또는 NOT_AUTHORIZED는 무료(Basic) 플랜에서 접근이 "
              "제한됨을 의미할 수 있습니다.")
        return "안 됨", f"HTTP {status} - 무료 플랜 접근 제한 가능성"

    if status != 200:
        print(f"에러 응답: {data}")
        return "안 됨", f"HTTP {status} - {data}"

    if not isinstance(data, dict) or "results" not in data:
        print(f"예상치 못한 응답 형식: {data}")
        return "안 됨", "'results' 키 없음"

    rows = data.get("results") or []
    print(f"레코드 수: {len(rows)}건")

    if len(rows) == 0:
        print("results가 빈 배열입니다. (KO는 실제로 최근 수십 년간 분할 이력이 "
              "없을 수 있음 — 빈 배열 자체가 오류는 아닐 수 있음)")
        print(f"전체 응답: {data}")
        return "부분적", "빈 배열 (엔드포인트는 정상 응답, 실제 분할 이력 부재 가능성 있음 — 다른 종목으로 재확인 권장)"

    key_fields = ["execution_date", "split_from", "split_to"]
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
    base_url = get_base_url()
    print(f"베이스 URL: {base_url}  (환경변수 MASSIVE_BASE_URL로 변경 가능)")

    results = {}
    results["배당 이력"] = check_dividends(base_url, token)
    results["주가 시계열"] = check_price_series(base_url, token)
    results["분할 이력"] = check_splits(base_url, token)

    section("요약")
    for name, (verdict, detail) in results.items():
        print(f"- {name}: [{verdict}] {detail}")


if __name__ == "__main__":
    main()
