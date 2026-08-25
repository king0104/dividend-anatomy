// 기존 static/js/cashflow.js의 버킷팅/합산 로직을 TS로 포팅.
// 표시용 순수 집계 함수라(백엔드가 이미 BigDecimal로 계산·반올림한 KRW
// 정수를 곱하고 더하기만 함) CLAUDE.md의 BigDecimal/테스트 규칙 대상이
// 아니다 — lifeCostConversion.ts와 같은 판단.
import type { KrwConvertedEntry } from "./api/types";

export interface Selection {
  symbol: string;
  quantity: number;
}

export interface Contribution {
  symbol: string;
  amountKrw: number;
}

export interface MonthBucket {
  year: number;
  month: number;
  totalKrw: number;
  contributions: Contribution[];
}

export interface SkippedEntry {
  symbol: string;
  exDividendDate: string;
  status: string;
}

export type TaxMode = "pretax" | "posttax";

function pickAmountKrw(entry: KrwConvertedEntry, taxMode: TaxMode): number | null {
  return taxMode === "posttax" ? entry.netAmountKrw : entry.grossAmountKrw;
}

/** 최근 12개월(이번 달 포함) 버킷을 오래된 순으로 만든다. */
export function buildMonthBuckets(now: Date = new Date()): MonthBucket[] {
  const buckets: MonthBucket[] = [];
  for (let i = 11; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
    buckets.push({ year: d.getFullYear(), month: d.getMonth() + 1, totalKrw: 0, contributions: [] });
  }
  return buckets;
}

function bucketKey(year: number, month: number): string {
  return `${year}-${String(month).padStart(2, "0")}`;
}

/**
 * 선택한 종목·수량과 종목별 배당 이력(KRW 환산)을 받아 최근 12개월
 * 현금흐름으로 집계한다. 환산 불가(`status !== "CONVERTED"`) 항목은
 * 합계에서 빼고 `skipped`로 그대로 노출한다 — 조용히 넘어가지 않는다.
 */
export function buildMonthlyCashFlow(
  selections: Selection[],
  entriesBySymbol: Record<string, KrwConvertedEntry[]>,
  taxMode: TaxMode,
  now: Date = new Date(),
): { buckets: MonthBucket[]; skipped: SkippedEntry[] } {
  const buckets = buildMonthBuckets(now);
  const bucketIndex = new Map(buckets.map((b, idx) => [bucketKey(b.year, b.month), idx]));
  const earliestAllowed = new Date(buckets[0].year, buckets[0].month - 1, 1);
  const skipped: SkippedEntry[] = [];

  for (const { symbol, quantity } of selections) {
    const entries = entriesBySymbol[symbol] ?? [];
    for (const entry of entries) {
      const exDate = new Date(entry.exDividendDate);
      if (exDate < earliestAllowed) {
        continue;
      }
      const idx = bucketIndex.get(bucketKey(exDate.getFullYear(), exDate.getMonth() + 1));
      if (idx === undefined) {
        continue;
      }
      const pickedAmountKrw = pickAmountKrw(entry, taxMode);
      if (entry.status !== "CONVERTED" || pickedAmountKrw === null) {
        skipped.push({ symbol, exDividendDate: entry.exDividendDate, status: entry.status });
        continue;
      }
      const amountKrw = pickedAmountKrw * quantity;
      buckets[idx].totalKrw += amountKrw;
      buckets[idx].contributions.push({ symbol, amountKrw });
    }
  }

  return { buckets, skipped };
}
