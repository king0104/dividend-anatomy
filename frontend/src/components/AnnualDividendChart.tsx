import { useEffect, useState } from "react";
import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { getDividendCuts } from "../api/dividendCuts";
import { buildYearlyDividendTrend, type YearlyDividend } from "../yearlyDividendTrend";

interface Props {
  symbol: string;
}

function formatUsd(value: number): string {
  return `$${value.toFixed(2)}`;
}

export default function AnnualDividendChart({ symbol }: Props) {
  const [data, setData] = useState<YearlyDividend[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setData(null);
    setError(null);
    getDividendCuts(symbol)
      .then((res) => setData(buildYearlyDividendTrend(res.comparisons)))
      .catch((err: Error) => setError(err.message));
  }, [symbol]);

  if (error) {
    return <p className="text-xs text-red-600">연도별 배당금을 불러오지 못했어요: {error}</p>;
  }

  if (!data) {
    return <p className="text-xs text-slate-400">불러오는 중...</p>;
  }

  const hasIncomplete = data.some((d) => d.incomplete);

  return (
    <div className="flex flex-col gap-2">
      <div className="h-48 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
            <XAxis dataKey="year" tick={{ fontSize: 11 }} />
            <YAxis tickFormatter={formatUsd} tick={{ fontSize: 11 }} width={48} />
            <Tooltip
              formatter={(value, _name, item) => [
                formatUsd(Number(value)),
                item?.payload?.incomplete ? "연환산(데이터 불완전)" : "연환산 주당 배당금",
              ]}
            />
            <Bar dataKey="annualizedAmount" radius={[4, 4, 0, 0]}>
              {data.map((d) => (
                <Cell key={d.year} fill={d.incomplete ? "#cbd5e1" : "#2563eb"} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
      <p className="text-xs text-slate-400">
        각 연도 말 시점 기준, 직전 12개월 주당 배당금 합계(분할 조정 완료)예요.
      </p>
      {hasIncomplete && (
        <p className="text-xs text-amber-700">
          회색 막대는 데이터 불완전: 그 해엔 직전 12개월 배당 이력이 다 확보되지 않았어요.
        </p>
      )}
    </div>
  );
}
