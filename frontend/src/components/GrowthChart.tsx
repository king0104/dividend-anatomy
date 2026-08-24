import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { YearlySnapshot } from "../api/types";

interface Props {
  yearlySeries: YearlySnapshot[];
}

function formatKrwShort(value: number): string {
  if (value >= 100_000_000) return `${(value / 100_000_000).toFixed(1)}억`;
  if (value >= 10_000) return `${Math.round(value / 10_000)}만`;
  return String(value);
}

export default function GrowthChart({ yearlySeries }: Props) {
  const data = yearlySeries.map((snapshot) => ({
    year: snapshot.checkpointDate.slice(0, 4),
    재투자: snapshot.reinvestValueKrw,
    미재투자: snapshot.noReinvestValueKrw,
  }));

  return (
    <div className="h-56 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
          <XAxis dataKey="year" tick={{ fontSize: 12 }} />
          <YAxis tickFormatter={formatKrwShort} tick={{ fontSize: 12 }} width={48} />
          <Tooltip formatter={(value) => `${Number(value).toLocaleString("ko-KR")}원`} />
          <Line type="monotone" dataKey="재투자" stroke="#2563eb" strokeWidth={2} dot={false} />
          <Line type="monotone" dataKey="미재투자" stroke="#9ca3af" strokeWidth={2} dot={false} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
