import { useEffect, useState } from "react";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { getKrwDividends } from "../api/portfolio";
import type { KrwConvertedEntry } from "../api/types";
import { buildMonthlyCashFlow, type MonthBucket, type Selection, type SkippedEntry } from "../monthlyBucket";

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
  const [buckets, setBuckets] = useState<MonthBucket[] | null>(null);
  const [skipped, setSkipped] = useState<SkippedEntry[]>([]);
  const [selectedIdx, setSelectedIdx] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setBuckets(null);
    setError(null);
    setSelectedIdx(null);

    Promise.all(
      selections.map((s) =>
        getKrwDividends(s.symbol)
          .then((res) => [s.symbol, res.entries] as const)
          .catch(() => [s.symbol, [] as KrwConvertedEntry[]] as const),
      ),
    )
      .then((pairs) => {
        const entriesBySymbol = Object.fromEntries(pairs);
        const { buckets: result, skipped: skippedResult } = buildMonthlyCashFlow(selections, entriesBySymbol);
        setBuckets(result);
        setSkipped(skippedResult);
      })
      .catch((err: Error) => setError(err.message));
  }, [selections]);

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
      <h1 className="text-center text-2xl font-bold leading-snug text-blue-700">
        최근 1년 기준, 월 평균 {formatKrw(monthlyAverageKrw)}을 받았어요
      </h1>

      <div className="rounded-xl bg-slate-100 px-4 py-3 text-center text-sm font-medium text-slate-700">
        목표(월 {formatKrw(monthlyGoalKrw)}) 대비 {achievementPercent.toFixed(0)}% 만큼이에요
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
      </div>

      <a
        href="#"
        onClick={(event) => event.preventDefault()}
        className="text-center text-sm text-slate-400 underline"
      >
        이런 회사들, 더 찾아볼래요?
      </a>

      <button type="button" onClick={onBack} className="text-center text-sm text-slate-400 underline">
        다시 담기
      </button>
    </div>
  );
}
