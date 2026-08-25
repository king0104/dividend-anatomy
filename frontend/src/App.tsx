import { useState } from "react";
import BrandCardGrid from "./components/BrandCardGrid";
import FriendCalendarCompare from "./components/FriendCalendarCompare";
import GoalInputStep from "./components/GoalInputStep";
import InputStep from "./components/InputStep";
import PortfolioBuilderStep from "./components/PortfolioBuilderStep";
import PortfolioResultScreen from "./components/PortfolioResultScreen";
import ResultScreen from "./components/ResultScreen";
import SafetyScoreScreen from "./components/SafetyScoreScreen";
import ShareCard from "./components/ShareCard";
import type { Brand, InvestMode, TimeMachineSimulationResponse } from "./api/types";
import { saveMyPortfolio } from "./myPortfolio";
import { type Selection } from "./monthlyBucket";
import { parseSharedFromLocation } from "./portfolioShareLink";

type Mode = "timemachine" | "safety" | "portfolio";
type TimeMachineStep = "brand" | "input" | "result";
type SafetyStep = "brand" | "result";
type PortfolioStep = "goal" | "build" | "result";

interface InputValues {
  investMode: InvestMode;
  amountKrw: number;
  periodYears: number;
}

export default function App() {
  const [mode, setMode] = useState<Mode>("timemachine");
  const [sharedPortfolio, setSharedPortfolio] = useState(() => parseSharedFromLocation());

  const [timeMachineStep, setTimeMachineStep] = useState<TimeMachineStep>("brand");
  const [brand, setBrand] = useState<Brand | null>(null);
  const [inputValues, setInputValues] = useState<InputValues | null>(null);
  const [shareResult, setShareResult] = useState<TimeMachineSimulationResponse | null>(null);

  const [safetyStep, setSafetyStep] = useState<SafetyStep>("brand");
  const [safetyBrand, setSafetyBrand] = useState<Brand | null>(null);

  const [portfolioStep, setPortfolioStep] = useState<PortfolioStep>("goal");
  const [monthlyGoalKrw, setMonthlyGoalKrw] = useState(0);
  const [portfolioSelections, setPortfolioSelections] = useState<Selection[]>([]);

  function handleGoalSubmit(goal: number) {
    setMonthlyGoalKrw(goal);
    setPortfolioStep("build");
  }

  function handlePortfolioSubmit(selections: Selection[]) {
    setPortfolioSelections(selections);
    setPortfolioStep("result");
    saveMyPortfolio({ selections, monthlyGoalKrw });
  }

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
    setPortfolioStep("goal");
  }

  function closeSharedView() {
    window.history.replaceState(null, "", window.location.pathname);
    setSharedPortfolio(null);
  }

  if (sharedPortfolio) {
    return (
      <div className="min-h-screen bg-white">
        <FriendCalendarCompare
          friendSelections={sharedPortfolio.selections}
          friendGoalKrw={sharedPortfolio.monthlyGoalKrw}
          onClose={closeSharedView}
          onBuildMine={() => {
            closeSharedView();
            switchMode("portfolio");
          }}
        />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-white">
      <div className="mx-auto flex max-w-md justify-center gap-2 px-6 pt-6">
        {(
          [
            { value: "timemachine" as const, label: "타임머신" },
            { value: "safety" as const, label: "배당 안전도" },
            { value: "portfolio" as const, label: "포트폴리오" },
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
              onExploreMore={() => switchMode("portfolio")}
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

      {mode === "portfolio" && (
        <>
          {portfolioStep === "goal" && <GoalInputStep onSubmit={handleGoalSubmit} />}

          {portfolioStep === "build" && <PortfolioBuilderStep onSubmit={handlePortfolioSubmit} />}

          {portfolioStep === "result" && (
            <PortfolioResultScreen
              monthlyGoalKrw={monthlyGoalKrw}
              selections={portfolioSelections}
              onBack={() => setPortfolioStep("build")}
            />
          )}
        </>
      )}
    </div>
  );
}
