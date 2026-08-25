// 기획서 7장 "다음 배당 카운트다운" — 과거 지급 간격의 평균으로 다음
// 지급일을 추정한다. PROJECT.md 6절의 "정확한 지급일은 배당 선언 시
// 발표되므로, 모르면 근사값 + '예상' 표시" 원칙을 그대로 따른다. 금액도
// 오르거나 내린다고 예측하지 않고 마지막 실제 지급액을 그대로 반복
// 가정한다 — CLAUDE.md의 미래 예측 문구 금지 원칙.
import type { KrwConvertedEntry } from "./api/types";
import { pickAmountKrw, type Selection, type TaxMode } from "./monthlyBucket";

export interface NextDividendEstimate {
  symbol: string;
  estimatedExDate: Date;
  estimatedAmountKrw: number | null;
}

const DAY_MS = 24 * 60 * 60 * 1000;
const MAX_GAP_SAMPLES = 4;

function estimateForSymbol(
  symbol: string,
  entries: KrwConvertedEntry[],
  taxMode: TaxMode,
  now: Date,
): NextDividendEstimate | null {
  if (entries.length < 2) return null;

  const sorted = [...entries].sort((a, b) => a.exDividendDate.localeCompare(b.exDividendDate));
  const recentDates = sorted.slice(-1 - MAX_GAP_SAMPLES).map((e) => new Date(e.exDividendDate));
  const gaps: number[] = [];
  for (let i = 1; i < recentDates.length; i++) {
    gaps.push((recentDates[i].getTime() - recentDates[i - 1].getTime()) / DAY_MS);
  }
  if (gaps.length === 0) return null;
  const avgGapDays = gaps.reduce((sum, g) => sum + g, 0) / gaps.length;
  if (avgGapDays <= 0) return null;

  let estimatedExDate = new Date(recentDates[recentDates.length - 1].getTime());
  while (estimatedExDate.getTime() <= now.getTime()) {
    estimatedExDate = new Date(estimatedExDate.getTime() + avgGapDays * DAY_MS);
  }

  let estimatedAmountKrw: number | null = null;
  for (let i = sorted.length - 1; i >= 0; i--) {
    const amount = pickAmountKrw(sorted[i], taxMode);
    if (amount !== null) {
      estimatedAmountKrw = amount;
      break;
    }
  }

  return { symbol, estimatedExDate, estimatedAmountKrw };
}

/** 담은 종목 중 가장 가까운 다음 배당(D-day 최솟값) 하나를 고른다. */
export function nextUpcomingDividend(
  selections: Selection[],
  entriesBySymbol: Record<string, KrwConvertedEntry[]>,
  taxMode: TaxMode,
  now: Date = new Date(),
): (NextDividendEstimate & { quantity: number }) | null {
  const estimates = selections
    .map((s) => {
      const estimate = estimateForSymbol(s.symbol, entriesBySymbol[s.symbol] ?? [], taxMode, now);
      return estimate ? { ...estimate, quantity: s.quantity } : null;
    })
    .filter((e): e is NextDividendEstimate & { quantity: number } => e !== null);

  if (estimates.length === 0) return null;

  return estimates.reduce((soonest, current) =>
    current.estimatedExDate < soonest.estimatedExDate ? current : soonest,
  );
}

export function daysUntil(target: Date, now: Date = new Date()): number {
  const startOfNow = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const startOfTarget = new Date(target.getFullYear(), target.getMonth(), target.getDate());
  return Math.round((startOfTarget.getTime() - startOfNow.getTime()) / DAY_MS);
}
