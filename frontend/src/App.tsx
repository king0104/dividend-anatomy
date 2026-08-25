import { useState } from "react";
import BrandCardGrid from "./components/BrandCardGrid";
import InputStep from "./components/InputStep";
import ResultScreen from "./components/ResultScreen";
import SafetyScoreScreen from "./components/SafetyScoreScreen";
import ShareCard from "./components/ShareCard";
import type { Brand, InvestMode, TimeMachineSimulationResponse } from "./api/types";

type Mode = "timemachine" | "safety";
type TimeMachineStep = "brand" | "input" | "result";
type SafetyStep = "brand" | "result";

interface InputValues {
  investMode: InvestMode;
  amountKrw: number;
  periodYears: number;
}

export default function App() {
  const [mode, setMode] = useState<Mode>("timemachine");

  const [timeMachineStep, setTimeMachineStep] = useState<TimeMachineStep>("brand");
  const [brand, setBrand] = useState<Brand | null>(null);
  const [inputValues, setInputValues] = useState<InputValues | null>(null);
  const [shareResult, setShareResult] = useState<TimeMachineSimulationResponse | null>(null);

  const [safetyStep, setSafetyStep] = useState<SafetyStep>("brand");
  const [safetyBrand, setSafetyBrand] = useState<Brand | null>(null);

  function handleBrandSelect(selected: Brand) {
    setBrand(selected);
    setTimeMachineStep("input");
  }

  function handleInputSubmit(values: InputValues) {
    setInputValues(values);
    setTimeMachineStep("result");
  }

  function handleSafetyBrandSelect(selected: Brand) {
    setSafetyBrand(selected);
    setSafetyStep("result");
  }

  function switchMode(next: Mode) {
    setMode(next);
    setTimeMachineStep("brand");
    setSafetyStep("brand");
  }

  return (
    <div className="min-h-screen bg-white">
      <div className="mx-auto flex max-w-md justify-center gap-2 px-6 pt-6">
        {(
          [
            { value: "timemachine" as const, label: "타임머신" },
            { value: "safety" as const, label: "배당 안전도" },
          ]
        ).map(({ value, label }) => (
          <button
            key={value}
            type="button"
            onClick={() => switchMode(value)}
            className={`rounded-full px-4 py-1.5 text-sm font-medium ${
              mode === value ? "bg-slate-900 text-white" : "bg-slate-100 text-slate-600"
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {mode === "timemachine" && (
        <>
          {timeMachineStep === "brand" && <BrandCardGrid onSelect={handleBrandSelect} />}

          {timeMachineStep === "input" && brand && (
            <InputStep brand={brand} onSubmit={handleInputSubmit} />
          )}

          {timeMachineStep === "result" && brand && inputValues && (
            <ResultScreen
              brand={brand}
              investMode={inputValues.investMode}
              amountKrw={inputValues.amountKrw}
              periodYears={inputValues.periodYears}
              onShare={setShareResult}
            />
          )}

          {shareResult && brand && inputValues && (
            <ShareCard
              brand={brand}
              periodYears={inputValues.periodYears}
              result={shareResult}
              onClose={() => setShareResult(null)}
            />
          )}
        </>
      )}

      {mode === "safety" && (
        <>
          {safetyStep === "brand" && (
            <BrandCardGrid
              onSelect={handleSafetyBrandSelect}
              title="배당 안전도를 확인할 회사를 골라보세요"
              subtitle="재무 지표로 계산한 참고 점수예요"
            />
          )}

          {safetyStep === "result" && safetyBrand && (
            <SafetyScoreScreen brand={safetyBrand} onBack={() => setSafetyStep("brand")} />
          )}
        </>
      )}
    </div>
  );
}
