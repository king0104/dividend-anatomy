import { useState } from "react";
import type { Brand, InvestMode } from "../api/types";

interface Props {
  brand: Brand;
  onSubmit: (params: { investMode: InvestMode; amountKrw: number; periodYears: number }) => void;
}

const LUMP_SUM_RANGE = { min: 100_000, max: 1_000_000, step: 100_000, defaultValue: 500_000 };
const MONTHLY_RANGE = { min: 30_000, max: 300_000, step: 10_000, defaultValue: 100_000 };
const PERIOD_OPTIONS = [3, 10, 20];

function formatKrw(value: number): string {
  return `${value.toLocaleString("ko-KR")}원`;
}

export default function InputStep({ brand, onSubmit }: Props) {
  const [investMode, setInvestMode] = useState<InvestMode>("MONTHLY");
  const [lumpSumAmount, setLumpSumAmount] = useState(LUMP_SUM_RANGE.defaultValue);
  const [monthlyAmount, setMonthlyAmount] = useState(MONTHLY_RANGE.defaultValue);
  const [periodYears, setPeriodYears] = useState(10);

  const range = investMode === "LUMP_SUM" ? LUMP_SUM_RANGE : MONTHLY_RANGE;
  const amount = investMode === "LUMP_SUM" ? lumpSumAmount : monthlyAmount;
  const setAmount = investMode === "LUMP_SUM" ? setLumpSumAmount : setMonthlyAmount;

  return (
    <div className="mx-auto flex max-w-md flex-col gap-6 px-6 py-10">
      <div className="text-center">
        <p className="text-sm text-slate-500">{brand.displayName}</p>
        <h1 className="text-xl font-bold text-slate-900">얼마씩, 얼마나 오래 넣었다고 가정할까요?</h1>
      </div>

      <div className="flex justify-center gap-2">
        {(
          [
            { mode: "MONTHLY" as const, label: "매달 조금씩 넣기" },
            { mode: "LUMP_SUM" as const, label: "한 번에 넣기" },
          ]
        ).map(({ mode, label }) => (
          <button
            key={mode}
            type="button"
            onClick={() => setInvestMode(mode)}
            className={`rounded-full px-4 py-2 text-sm font-medium ${
              investMode === mode ? "bg-slate-900 text-white" : "bg-slate-100 text-slate-600"
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      <div className="flex flex-col gap-2">
        <div className="text-center text-lg font-semibold text-slate-900">{formatKrw(amount)}</div>
        <input
          type="range"
          min={range.min}
          max={range.max}
          step={range.step}
          value={amount}
          onChange={(event) => setAmount(Number(event.target.value))}
          className="w-full"
        />
        <div className="flex justify-between text-xs text-slate-400">
          <span>{formatKrw(range.min)}</span>
          <span>{formatKrw(range.max)}</span>
        </div>
      </div>

      <div className="flex justify-center gap-2">
        {PERIOD_OPTIONS.map((years) => (
          <button
            key={years}
            type="button"
            onClick={() => setPeriodYears(years)}
            className={`rounded-full px-4 py-2 text-sm font-medium ${
              periodYears === years ? "bg-slate-900 text-white" : "bg-slate-100 text-slate-600"
            }`}
          >
            {years}년
          </button>
        ))}
      </div>

      <button
        type="button"
        onClick={() => onSubmit({ investMode, amountKrw: amount, periodYears })}
        className="rounded-xl bg-blue-600 py-3 text-base font-semibold text-white"
      >
        결과 보기
      </button>
    </div>
  );
}
