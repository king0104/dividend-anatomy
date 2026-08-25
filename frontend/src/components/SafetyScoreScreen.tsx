import { useEffect, useState } from "react";
import { getSafetyScore } from "../api/safetyScore";
import type { Brand, DividendSafetyScoreResponse, SafetyBand } from "../api/types";
import AnnualDividendChart from "./AnnualDividendChart";

interface Props {
  brand: Brand;
  onBack: () => void;
}

const BAND_STYLE: Record<SafetyBand, { ring: string; bg: string; text: string; label: string }> = {
  GREEN: { ring: "ring-emerald-500", bg: "bg-emerald-500", text: "text-emerald-700", label: "안전" },
  YELLOW: { ring: "ring-amber-500", bg: "bg-amber-500", text: "text-amber-700", label: "보통" },
  RED: { ring: "ring-red-500", bg: "bg-red-500", text: "text-red-700", label: "주의" },
};

const INDICATOR_LABEL: Record<string, string> = {
  PAYOUT_RATIO: "배당성향",
  FCF_PAYOUT_RATIO: "FCF 대비 배당",
  ROE: "자기자본이익률(ROE)",
  DEBT_TO_EQUITY: "부채비율(D/E)",
  INTEREST_COVERAGE: "이자보상배율",
};

const INDICATOR_UNIT: Record<string, string> = {
  PAYOUT_RATIO: "%",
  FCF_PAYOUT_RATIO: "%",
  ROE: "%",
  DEBT_TO_EQUITY: "배",
  INTEREST_COVERAGE: "배",
};

export default function SafetyScoreScreen({ brand, onBack }: Props) {
  const [result, setResult] = useState<DividendSafetyScoreResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [expanded, setExpanded] = useState(false);
  const [showTrend, setShowTrend] = useState(false);

  useEffect(() => {
    setResult(null);
    setError(null);
    setExpanded(false);
    setShowTrend(false);
    getSafetyScore(brand.symbol)
      .then(setResult)
      .catch((err: Error) => setError(err.message));
  }, [brand.symbol]);

  if (error) {
    return (
      <div className="mx-auto max-w-md px-6 py-10 text-center text-red-600">
        점수를 불러오지 못했어요: {error}
      </div>
    );
  }

  if (!result) {
    return <div className="mx-auto max-w-md px-6 py-10 text-center text-slate-400">계산하는 중...</div>;
  }

  return (
    <div className="mx-auto flex max-w-md flex-col gap-5 px-6 py-10">
      <div className="text-center">
        <p className="text-sm text-slate-500">{brand.displayName}</p>
        <h1 className="text-xl font-bold text-slate-900">배당 안전도는 어느 정도일까요?</h1>
      </div>

      {!result.available && (
        <div className="rounded-lg bg-amber-50 px-3 py-2 text-center text-xs text-amber-800">
          데이터 불완전: 아직 이 종목의 재무 지표를 다 모으지 못해 점수를 계산할 수 없어요.
        </div>
      )}

      {result.available && result.totalScore !== null && result.band && (
        <button
          type="button"
          onClick={() => setExpanded((prev) => !prev)}
          className="flex flex-col items-center gap-3 rounded-2xl border border-slate-200 bg-white px-6 py-8 shadow-sm"
        >
          <div
            className={`flex h-28 w-28 items-center justify-center rounded-full text-3xl font-bold text-white ${BAND_STYLE[result.band].bg}`}
          >
            {result.totalScore}점
          </div>
          <span className={`text-base font-semibold ${BAND_STYLE[result.band].text}`}>
            {BAND_STYLE[result.band].label}
          </span>
          {result.fiscalDateEnding && (
            <span className="text-xs text-slate-400">{result.fiscalDateEnding} 재무제표 기준</span>
          )}
          <span className="text-xs text-slate-400 underline">
            {expanded ? "지표별로 접기" : "지표별로 풀어보기"}
          </span>
        </button>
      )}

      {expanded && result.indicators && (
        <div className="flex flex-col gap-3">
          {result.indicators.map((indicator) => (
            <div key={indicator.name} className="rounded-xl border border-slate-200 px-4 py-3">
              <div className="flex items-baseline justify-between">
                <span className="text-sm font-medium text-slate-700">
                  {INDICATOR_LABEL[indicator.name] ?? indicator.name}
                </span>
                <span className="text-sm font-semibold text-slate-900">
                  {indicator.value.toFixed(2)}
                  {INDICATOR_UNIT[indicator.name] ?? ""}
                </span>
              </div>
              <div className="mt-2 h-1.5 w-full rounded-full bg-slate-100">
                <div
                  className="h-1.5 rounded-full bg-slate-900"
                  style={{ width: `${(indicator.subScore / 20) * 100}%` }}
                />
              </div>
            </div>
          ))}
        </div>
      )}

      {expanded && (
        <div className="flex flex-col gap-2">
          <button
            type="button"
            onClick={() => setShowTrend((prev) => !prev)}
            className="text-center text-xs text-slate-400 underline"
          >
            {showTrend ? "연도별 배당금 추이 접기" : "연도별 배당금 추이 더 보기"}
          </button>
          {showTrend && <AnnualDividendChart symbol={brand.symbol} />}
        </div>
      )}

      <div className="text-center text-xs text-slate-400">
        <p>공개된 재무 지표를 기준으로 계산한 참고 점수이며 투자 조언이 아닙니다.</p>
      </div>

      <button
        type="button"
        onClick={onBack}
        className="text-center text-sm text-slate-400 underline"
      >
        다른 회사 보기
      </button>
    </div>
  );
}
