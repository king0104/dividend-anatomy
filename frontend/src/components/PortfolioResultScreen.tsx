import { useEffect, useMemo, useState } from "react";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { getKrwDividends } from "../api/portfolio";
import type { KrwConvertedEntry } from "../api/types";
import BrokerSearchCta from "./BrokerSearchCta";
import { buildMonthlyCashFlow, type Selection, type TaxMode } from "../monthlyBucket";
import { daysUntil, nextUpcomingDividend } from "../nextDividendEstimate";

interface Props {
  monthlyGoalKrw: number;
  selections: Selection[];
  onBack: () => void;
}

const MONTH_NAMES = ["1월", "2월", "3월", "4월", "5월", "6월", "7월", "8월", "9월", "10월", "11월", "12월"];

function formatKrw(value: number): string {
  return `${Math.round(value).toLocaleString("ko-KR")}원`;
}

export default function PortfolioResultScreen({ monthlyGoalKrw, selections, onBack }: Props) {
  const [entriesBySymbol, setEntriesBySymbol] = useState<Record<string, KrwConvertedEntry[]> | null>(null);
  const [taxMode, setTaxMode] = useState<TaxMode>("posttax");
  const [selectedIdx, setSelectedIdx] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setEntriesBySymbol(null);
    setError(null);
    setSelectedIdx(null);

    Promise.all(
      selections.map((s) =>
        getKrwDividends(s.symbol)
          .then((res) => [s.symbol, res.entries] as const)
          .catch(() => [s.symbol, [] as KrwConvertedEntry[]] as const),
      ),
    )
      .then((pairs) => setEntriesBySymbol(Object.fromEntries(pairs)))
      .catch((err: Error) => setError(err.message));
  }, [selections]);

  const { buckets, skipped } = useMemo(() => {
    if (!entriesBySymbol) return { buckets: null, skipped: [] };
    return buildMonthlyCashFlow(selections, entriesBySymbol, taxMode);
  }, [entriesBySymbol, selections, taxMode]);

  const nextDividend = useMemo(() => {
    if (!entriesBySymbol) return null;
    return nextUpcomingDividend(selections, entriesBySymbol, taxMode);
  }, [entriesBySymbol, selections, taxMode]);

  if (error) {
    return (
      <div className="mx-auto max-w-md px-6 py-10 text-center text-red-600">
        결과를 불러오지 못했어요: {error}
      </div>
    );
  }

  if (!buckets) {
    return <div className="mx-auto max-w-md px-6 py-10 text-center text-slate-400">계산하는 중...</div>;
  }

  const totalKrw = buckets.reduce((sum, b) => sum + b.totalKrw, 0);
  const monthlyAverageKrw = totalKrw / 12;
  const annualGoalKrw = monthlyGoalKrw * 12;
  const achievementPercent = annualGoalKrw > 0 ? Math.min(999, (totalKrw / annualGoalKrw) * 100) : 0;

  const chartData = buckets.map((b) => ({
    label: MONTH_NAMES[b.month - 1],
    totalKrw: b.totalKrw,
  }));

  const selectedBucket = selectedIdx !== null ? buckets[selectedIdx] : null;

  return (
    <div className="mx-auto flex max-w-md flex-col gap-5 px-6 py-10">
      <div className="flex justify-center gap-2">
        {(
          [
            { mode: "posttax" as const, label: "세후(실수령)" },
            { mode: "pretax" as const, label: "세전" },
          ]
        ).map(({ mode, label }) => (
          <button
            key={mode}
            type="button"
            onClick={() => setTaxMode(mode)}
            className={`rounded-full px-4 py-1.5 text-sm font-medium ${
              taxMode === mode ? "bg-slate-900 text-white" : "bg-slate-100 text-slate-600"
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {nextDividend && (
        <div className="rounded-xl border border-blue-100 bg-blue-50 px-4 py-3 text-center text-sm text-blue-800">
          다음 배당은 <span className="font-bold">D-{Math.max(0, daysUntil(nextDividend.estimatedExDate))}</span>,{" "}
          {nextDividend.symbol}에서
          {nextDividend.estimatedAmountKrw !== null ? (
            <> 약 {formatKrw(nextDividend.estimatedAmountKrw * nextDividend.quantity)}</>
          ) : (
            " 금액 미확인"
          )}{" "}
          예정이에요
          <div className="mt-1 text-xs text-blue-500">
            지난 지급 간격 기준 추정이며, 실제 발표일과 다를 수 있어요.
          </div>
        </div>
      )}

      <h1 className="text-center text-2xl font-bold leading-snug text-blue-700">
        최근 1년 기준, {taxMode === "posttax" ? "세후" : "세전"} 월 평균 {formatKrw(monthlyAverageKrw)}을
        받았어요
      </h1>

      <div className="rounded-xl bg-slate-100 px-4 py-3 text-center text-sm font-medium text-slate-700">
        {taxMode === "posttax" ? "세후" : "세전"} 기준, 목표(월 {formatKrw(monthlyGoalKrw)}) 대비{" "}
        {achievementPercent.toFixed(0)}% 만큼이에요
        <div className="mt-2 h-2 w-full overflow-hidden rounded-full bg-slate-200">
          <div
            className="h-2 rounded-full bg-blue-600"
            style={{ width: `${Math.min(100, achievementPercent)}%` }}
          />
        </div>
      </div>

      {skipped.length > 0 && (
        <div className="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800">
          데이터 불완전: {skipped.length}건은 환율·지급일 정보가 부족해 합계에서 제외됐어요.
        </div>
      )}

      <div className="h-56 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={chartData} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
            <XAxis dataKey="label" tick={{ fontSize: 12 }} />
            <YAxis
              tickFormatter={(value: number) =>
                value >= 10_000 ? `${Math.round(value / 10_000)}만` : String(value)
              }
              tick={{ fontSize: 12 }}
              width={48}
            />
            <Tooltip formatter={(value) => `${Number(value).toLocaleString("ko-KR")}원`} />
            <Bar
              dataKey="totalKrw"
              fill="#2563eb"
              radius={[4, 4, 0, 0]}
              onClick={(_, index) => setSelectedIdx(index)}
              cursor="pointer"
            />
          </BarChart>
        </ResponsiveContainer>
      </div>

      {selectedBucket && (
        <div className="rounded-xl border border-slate-200 px-4 py-3">
          <div className="mb-2 text-sm font-semibold text-slate-800">
            {selectedBucket.year}년 {selectedBucket.month}월 — 합계 {formatKrw(selectedBucket.totalKrw)}
          </div>
          {selectedBucket.contributions.length === 0 ? (
            <p className="text-xs text-slate-400">이 달엔 지급된 배당이 없어요.</p>
          ) : (
            <ul className="flex flex-col gap-1">
              {selectedBucket.contributions.map((c, idx) => (
                <li key={`${c.symbol}-${idx}`} className="flex justify-between text-xs text-slate-600">
                  <span>{c.symbol}</span>
                  <span>{formatKrw(c.amountKrw)}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      <div className="text-center text-xs text-slate-400">
        <p>과거 데이터 기준 시뮬레이션이며 투자 조언이 아닙니다.</p>
        <p>종목 조합은 예시가 아니라 직접 구성한 내용입니다.</p>
        <p>세금은 미국 원천징수 15%만 반영하며, 금융소득종합과세는 별도입니다.</p>
      </div>

      <div className="flex flex-col items-center gap-3 border-t border-slate-100 pt-4">
        <p className="text-sm font-medium text-slate-600">실제로 시작하려면?</p>
        <BrokerSearchCta symbols={selections.map((s) => s.symbol)} />
      </div>

      <button type="button" onClick={onBack} className="text-center text-sm text-slate-400 underline">
        다시 담기
      </button>
    </div>
  );
}
