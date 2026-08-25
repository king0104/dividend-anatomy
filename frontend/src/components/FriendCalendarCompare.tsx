import { useEffect, useMemo, useState } from "react";
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { getKrwDividends } from "../api/portfolio";
import type { KrwConvertedEntry } from "../api/types";
import { buildMonthlyCashFlow, type Selection } from "../monthlyBucket";
import { loadMyPortfolio } from "../myPortfolio";

interface Props {
  friendSelections: Selection[];
  friendGoalKrw: number;
  onClose: () => void;
  onBuildMine: () => void;
}

const MONTH_NAMES = ["1월", "2월", "3월", "4월", "5월", "6월", "7월", "8월", "9월", "10월", "11월", "12월"];

function formatKrw(value: number): string {
  return `${Math.round(value).toLocaleString("ko-KR")}원`;
}

async function fetchEntries(selections: Selection[]): Promise<Record<string, KrwConvertedEntry[]>> {
  const pairs = await Promise.all(
    selections.map((s) =>
      getKrwDividends(s.symbol)
        .then((res) => [s.symbol, res.entries] as const)
        .catch(() => [s.symbol, [] as KrwConvertedEntry[]] as const),
    ),
  );
  return Object.fromEntries(pairs);
}

export default function FriendCalendarCompare({ friendSelections, friendGoalKrw, onClose, onBuildMine }: Props) {
  const mine = useMemo(() => loadMyPortfolio(), []);
  const [friendEntries, setFriendEntries] = useState<Record<string, KrwConvertedEntry[]> | null>(null);
  const [myEntries, setMyEntries] = useState<Record<string, KrwConvertedEntry[]> | null>(null);

  useEffect(() => {
    fetchEntries(friendSelections).then(setFriendEntries);
  }, [friendSelections]);

  useEffect(() => {
    if (mine) {
      fetchEntries(mine.selections).then(setMyEntries);
    }
  }, [mine]);

  const friendResult = useMemo(() => {
    if (!friendEntries) return null;
    return buildMonthlyCashFlow(friendSelections, friendEntries, "posttax");
  }, [friendEntries, friendSelections]);

  const myResult = useMemo(() => {
    if (!mine || !myEntries) return null;
    return buildMonthlyCashFlow(mine.selections, myEntries, "posttax");
  }, [mine, myEntries]);

  if (!friendResult) {
    return <div className="mx-auto max-w-md px-6 py-10 text-center text-slate-400">불러오는 중...</div>;
  }

  const friendTotal = friendResult.buckets.reduce((sum, b) => sum + b.totalKrw, 0);
  const myTotal = myResult ? myResult.buckets.reduce((sum, b) => sum + b.totalKrw, 0) : null;

  const chartData = MONTH_NAMES.map((label, idx) => ({
    label,
    친구: friendResult.buckets[idx]?.totalKrw ?? 0,
    나: myResult?.buckets[idx]?.totalKrw ?? 0,
  }));

  return (
    <div className="mx-auto flex max-w-md flex-col gap-5 px-6 py-10">
      <h1 className="text-center text-xl font-bold text-slate-900">친구와 배당 캘린더 비교</h1>

      <div className="grid grid-cols-2 gap-3">
        <div className="rounded-xl border border-blue-100 bg-blue-50 px-3 py-3 text-center">
          <p className="text-xs text-blue-500">친구 ({friendSelections.map((s) => s.symbol).join(", ")})</p>
          <p className="mt-1 text-lg font-bold text-blue-700">{formatKrw(friendTotal / 12)}</p>
          <p className="text-xs text-blue-500">월 평균(최근 1년)</p>
          {friendGoalKrw > 0 && <p className="mt-1 text-xs text-blue-400">목표 월 {formatKrw(friendGoalKrw)}</p>}
        </div>
        <div className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-3 text-center">
          {mine ? (
            <>
              <p className="text-xs text-slate-500">나 ({mine.selections.map((s) => s.symbol).join(", ")})</p>
              <p className="mt-1 text-lg font-bold text-slate-800">
                {myTotal !== null ? formatKrw(myTotal / 12) : "..."}
              </p>
              <p className="text-xs text-slate-500">월 평균(최근 1년)</p>
            </>
          ) : (
            <>
              <p className="text-xs text-slate-500">아직 내 포트폴리오가 없어요</p>
              <button
                type="button"
                onClick={onBuildMine}
                className="mt-2 rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-semibold text-white"
              >
                나도 만들어보기
              </button>
            </>
          )}
        </div>
      </div>

      <div className="h-56 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={chartData} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
            <XAxis dataKey="label" tick={{ fontSize: 11 }} />
            <YAxis
              tickFormatter={(value: number) =>
                value >= 10_000 ? `${Math.round(value / 10_000)}만` : String(value)
              }
              tick={{ fontSize: 11 }}
              width={48}
            />
            <Tooltip formatter={(value) => `${Number(value).toLocaleString("ko-KR")}원`} />
            <Legend wrapperStyle={{ fontSize: 12 }} />
            <Bar dataKey="친구" fill="#93c5fd" radius={[3, 3, 0, 0]} />
            {mine && <Bar dataKey="나" fill="#2563eb" radius={[3, 3, 0, 0]} />}
          </BarChart>
        </ResponsiveContainer>
      </div>

      <div className="text-center text-xs text-slate-400">
        <p>과거 데이터 기준 시뮬레이션이며 투자 조언이 아닙니다.</p>
        <p>친구가 공유한 링크로만 볼 수 있고, 서버에는 아무것도 저장되지 않아요.</p>
      </div>

      <button type="button" onClick={onClose} className="text-center text-sm text-slate-400 underline">
        닫기
      </button>
    </div>
  );
}
