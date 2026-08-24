import { useEffect, useState } from "react";
import { simulate, todayIso } from "../api/timemachine";
import type { Brand, InvestMode, TimeMachineSimulationResponse } from "../api/types";
import { lifeCostConversion } from "../lifeCostConversion";
import GrowthChart from "./GrowthChart";

interface Props {
  brand: Brand;
  investMode: InvestMode;
  amountKrw: number;
  periodYears: number;
  onShare: (result: TimeMachineSimulationResponse) => void;
}

function formatKrw(value: number): string {
  return `${Math.round(value).toLocaleString("ko-KR")}원`;
}

export default function ResultScreen({ brand, investMode, amountKrw, periodYears, onShare }: Props) {
  const [result, setResult] = useState<TimeMachineSimulationResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setResult(null);
    setError(null);
    simulate({ symbol: brand.symbol, investMode, amountKrw, periodYears, asOf: todayIso() })
      .then(setResult)
      .catch((err: Error) => setError(err.message));
  }, [brand.symbol, investMode, amountKrw, periodYears]);

  if (error) {
    return (
      <div className="mx-auto max-w-md px-6 py-10 text-center text-red-600">
        결과를 불러오지 못했어요: {error}
      </div>
    );
  }

  if (!result) {
    return <div className="mx-auto max-w-md px-6 py-10 text-center text-slate-400">계산하는 중...</div>;
  }

  const lifeCost = lifeCostConversion(result.differenceKrw);

  return (
    <div className="mx-auto flex max-w-md flex-col gap-5 px-6 py-10">
      {!result.dataComplete && (
        <p className="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800">
          데이터 불완전: 요청하신 {result.requestedPeriodYears}년 대신 실제로 확인 가능한{" "}
          {result.actualPeriodYears}년 데이터로 계산했어요.
        </p>
      )}

      <h1 className="text-center text-2xl font-bold leading-snug text-blue-700">
        재투자를 했다면, 지금 {formatKrw(result.finalValueReinvestKrw)}이 됐을 거예요
      </h1>

      <div className="rounded-xl bg-slate-100 px-4 py-3 text-center text-sm font-medium text-slate-700">
        그냥 현금으로 받았을 때보다 {formatKrw(result.differenceKrw)} 더 많아요
      </div>

      <div className="rounded-xl bg-blue-50 px-4 py-3 text-center text-sm font-medium text-blue-700">
        총 {result.totalReturnPercent.toFixed(2)}% 늘었어요
      </div>

      {lifeCost.quantity > 0 && (
        <div className="rounded-xl border border-slate-200 px-4 py-3 text-center text-sm text-slate-600">
          이 차이만큼이면 {lifeCost.label} {lifeCost.quantity.toLocaleString("ko-KR")}개
        </div>
      )}

      <GrowthChart yearlySeries={result.yearlySeries} />

      <div className="text-center text-xs text-slate-400">
        <p>과거 데이터 기준 시뮬레이션이며 투자 조언이 아닙니다.</p>
        <p>환율은 계산 시점 기준입니다.</p>
      </div>

      <button
        type="button"
        onClick={() => onShare(result)}
        className="rounded-xl bg-slate-900 py-3 text-base font-semibold text-white"
      >
        결과 공유하기
      </button>

      <a
        href="#"
        onClick={(event) => event.preventDefault()}
        className="text-center text-sm text-slate-400 underline"
      >
        이런 회사들, 더 찾아볼래요?
      </a>
    </div>
  );
}
