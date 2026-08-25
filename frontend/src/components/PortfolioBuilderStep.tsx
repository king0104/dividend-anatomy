import { useEffect, useMemo, useState } from "react";
import { getTickers } from "../api/portfolio";
import type { TickerSummaryResponse } from "../api/types";
import type { Selection } from "../monthlyBucket";

interface Props {
  onSubmit: (selections: Selection[]) => void;
}

// static/js/index.js의 HIGH_YIELD_WARNING_THRESHOLD와 동일 기준(PROJECT.md 2절
// 알트리아 8.2% 예시 참고, 보수적으로 6%)을 재사용 — 두 화면에서 다른 기준을
// 쓰면 사용자가 혼란스럽다.
const HIGH_YIELD_WARNING_THRESHOLD = 6.0;

function formatStreak(ticker: TickerSummaryResponse): string {
  if (ticker.streakStatus !== "CALCULATED" || ticker.streakYears === null) {
    return "데이터 부족";
  }
  return `${ticker.streakYears}년 연속 증가`;
}

export default function PortfolioBuilderStep({ onSubmit }: Props) {
  const [tickers, setTickers] = useState<TickerSummaryResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [quantities, setQuantities] = useState<Record<string, number>>({});

  useEffect(() => {
    getTickers()
      .then(setTickers)
      .catch((err: Error) => setError(err.message));
  }, []);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return tickers;
    return tickers.filter(
      (t) => t.symbol.toLowerCase().includes(q) || t.name.toLowerCase().includes(q),
    );
  }, [tickers, query]);

  const selections: Selection[] = Object.entries(quantities)
    .filter(([, qty]) => qty > 0)
    .map(([symbol, quantity]) => ({ symbol, quantity }));

  function setQuantity(symbol: string, quantity: number) {
    setQuantities((prev) => ({ ...prev, [symbol]: Math.max(0, quantity) }));
  }

  return (
    <div className="mx-auto flex max-w-md flex-col gap-4 px-6 py-10">
      <div className="text-center">
        <h1 className="text-xl font-bold text-slate-900">어떤 종목을, 몇 주씩 담아볼까요?</h1>
        <p className="mt-1 text-sm text-slate-500">배당킹 57종목 중에서 검색해서 담아보세요</p>
      </div>

      <input
        type="text"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder="티커 또는 회사명 검색 (예: KO, 코카콜라)"
        className="rounded-xl border border-slate-200 px-4 py-2 text-sm"
      />

      {error && <p className="text-sm text-red-600">종목 목록을 불러오지 못했어요: {error}</p>}

      <div className="flex max-h-80 flex-col gap-2 overflow-y-auto">
        {filtered.map((ticker) => (
          <div
            key={ticker.symbol}
            className="flex items-center justify-between gap-3 rounded-xl border border-slate-200 px-4 py-2"
          >
            <div>
              <div className="text-sm font-medium text-slate-800">{ticker.name}</div>
              <div className="text-xs text-slate-400">{ticker.symbol}</div>
              <div className="mt-1 flex items-center gap-1 text-xs text-slate-500">
                <span>시가배당률 {ticker.currentYieldPercent?.toFixed(2) ?? "-"}%</span>
                {ticker.currentYieldPercent !== null &&
                  ticker.currentYieldPercent >= HIGH_YIELD_WARNING_THRESHOLD && (
                    <span className="text-amber-600">⚠️ 확인 필요</span>
                  )}
              </div>
              <div className="text-xs text-slate-400">{formatStreak(ticker)}</div>
            </div>
            <input
              type="number"
              min={0}
              value={quantities[ticker.symbol] ?? 0}
              onChange={(event) => setQuantity(ticker.symbol, Number(event.target.value))}
              className="w-16 rounded-lg border border-slate-200 px-2 py-1 text-right text-sm"
            />
          </div>
        ))}
        {filtered.length === 0 && !error && (
          <p className="py-6 text-center text-sm text-slate-400">검색 결과가 없어요</p>
        )}
      </div>

      <div className="text-center text-xs text-slate-400">담은 종목 {selections.length}개</div>

      <button
        type="button"
        disabled={selections.length === 0}
        onClick={() => onSubmit(selections)}
        className="rounded-xl bg-blue-600 py-3 text-base font-semibold text-white disabled:bg-slate-200 disabled:text-slate-400"
      >
        결과 보기
      </button>
    </div>
  );
}
