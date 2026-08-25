import { useState } from "react";

interface Props {
  onSubmit: (monthlyGoalKrw: number) => void;
}

const RANGE = { min: 30_000, max: 1_000_000, step: 10_000, defaultValue: 300_000 };

function formatKrw(value: number): string {
  return `${value.toLocaleString("ko-KR")}원`;
}

export default function GoalInputStep({ onSubmit }: Props) {
  const [monthlyGoalKrw, setMonthlyGoalKrw] = useState(RANGE.defaultValue);

  return (
    <div className="mx-auto flex max-w-md flex-col gap-6 px-6 py-10">
      <div className="text-center">
        <h1 className="text-xl font-bold text-slate-900">월에 배당을 얼마나 받고 싶으세요?</h1>
        <p className="mt-1 text-sm text-slate-500">회원가입 없이 바로 확인할 수 있어요</p>
      </div>

      <div className="flex flex-col gap-2">
        <div className="text-center text-2xl font-bold text-blue-700">{formatKrw(monthlyGoalKrw)}</div>
        <input
          type="range"
          min={RANGE.min}
          max={RANGE.max}
          step={RANGE.step}
          value={monthlyGoalKrw}
          onChange={(event) => setMonthlyGoalKrw(Number(event.target.value))}
          className="w-full"
        />
        <div className="flex justify-between text-xs text-slate-400">
          <span>{formatKrw(RANGE.min)}</span>
          <span>{formatKrw(RANGE.max)}</span>
        </div>
      </div>

      <button
        type="button"
        onClick={() => onSubmit(monthlyGoalKrw)}
        className="rounded-xl bg-blue-600 py-3 text-base font-semibold text-white"
      >
        종목 담으러 가기
      </button>
    </div>
  );
}
