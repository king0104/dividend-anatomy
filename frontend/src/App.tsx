import { useState } from "react";
import BrandCardGrid from "./components/BrandCardGrid";
import InputStep from "./components/InputStep";
import ResultScreen from "./components/ResultScreen";
import ShareCard from "./components/ShareCard";
import type { Brand, InvestMode, TimeMachineSimulationResponse } from "./api/types";

type Step = "brand" | "input" | "result";

interface InputValues {
  investMode: InvestMode;
  amountKrw: number;
  periodYears: number;
}

export default function App() {
  const [step, setStep] = useState<Step>("brand");
  const [brand, setBrand] = useState<Brand | null>(null);
  const [inputValues, setInputValues] = useState<InputValues | null>(null);
  const [shareResult, setShareResult] = useState<TimeMachineSimulationResponse | null>(null);

  function handleBrandSelect(selected: Brand) {
    setBrand(selected);
    setStep("input");
  }

  function handleInputSubmit(values: InputValues) {
    setInputValues(values);
    setStep("result");
  }

  return (
    <div className="min-h-screen bg-white">
      {step === "brand" && <BrandCardGrid onSelect={handleBrandSelect} />}

      {step === "input" && brand && (
        <InputStep brand={brand} onSubmit={handleInputSubmit} />
      )}

      {step === "result" && brand && inputValues && (
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
    </div>
  );
}
