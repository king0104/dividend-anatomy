// GET /api/tickers/{symbol}/dividend-cuts는 정기 배당 지급일마다 TTM(직전
// 12개월) 연환산 배당금을 이미 분할 조정까지 끝내서 내려준다
// (TtmDividendAggregationService, docs/decisions/03-split-adjustment.md).
// "연도별 배당금 추이"는 이 시계열을 새로 계산하지 않고, 그 해의 마지막
// 스냅샷 하나를 그 해 대표값으로 뽑아 표시만 한다 — 순수 집계 함수라
// monthlyBucket.ts와 같은 이유로 CLAUDE.md 계산 로직/테스트 규칙 대상이
// 아니다.
import type { CutEvent } from "./api/types";

export interface YearlyDividend {
  year: number;
  annualizedAmount: number;
  /** 그 해 마지막 스냅샷조차 TTM 창이 다 안 찼다는 뜻(주로 데이터 시작 연도) — 조용히 감추지 않고 표시. */
  incomplete: boolean;
}

export function buildYearlyDividendTrend(comparisons: CutEvent[]): YearlyDividend[] {
  const sorted = [...comparisons].sort((a, b) => a.detectedAt.localeCompare(b.detectedAt));
  const lastByYear = new Map<number, CutEvent>();

  for (const entry of sorted) {
    const year = new Date(entry.detectedAt).getFullYear();
    lastByYear.set(year, entry); // 뒤에서 덮어쓰므로 그 해의 마지막 스냅샷이 남는다
  }

  return Array.from(lastByYear.entries())
    .sort(([a], [b]) => a - b)
    .map(([year, entry]) => ({
      year,
      annualizedAmount: entry.currentTtmAmount,
      incomplete: entry.status === "INCOMPLETE",
    }));
}
