// 백엔드 레코드와 1:1 대응.
// Brand: com.dividendanatomy.domain.timemachine.Brand
// TimeMachineSimulationResponse: com.dividendanatomy.web.timemachine.TimeMachineSimulationResponse

export type BrandCategory = "SUCCESS" | "CUT";

export interface Brand {
  symbol: string;
  displayName: string;
  logoPath: string;
  category: BrandCategory;
}

export type InvestMode = "LUMP_SUM" | "MONTHLY";

export interface YearlySnapshot {
  checkpointDate: string;
  reinvestValueKrw: number;
  noReinvestValueKrw: number;
}

export interface TimeMachineSimulationResponse {
  tickerSymbol: string;
  requestedPeriodYears: number;
  actualPeriodYears: number;
  dataComplete: boolean;
  finalValueReinvestKrw: number;
  finalValueNoReinvestKrw: number;
  differenceKrw: number;
  totalReturnPercent: number;
  yearlySeries: YearlySnapshot[];
}

// DividendSafetyScoreResponse: com.dividendanatomy.web.safety.DividendSafetyScoreResponse
export type SafetyBand = "GREEN" | "YELLOW" | "RED";

export interface SafetyIndicator {
  name: string;
  value: number;
  subScore: number;
}

export interface DividendSafetyScoreResponse {
  tickerSymbol: string;
  available: boolean;
  fiscalDateEnding: string | null;
  indicators: SafetyIndicator[] | null;
  totalScore: number | null;
  band: SafetyBand | null;
}

// TickerSummaryResponse: com.dividendanatomy.web.ticker.TickerSummaryResponse
export type DividendIncreaseStreakStatus = "CALCULATED" | "INSUFFICIENT_DATA";

export interface TickerSummaryResponse {
  symbol: string;
  name: string;
  currency: string;
  currentPrice: number;
  regularPaymentsPerYear: number | null;
  currentYieldPercent: number | null;
  dataComplete: boolean;
  streakStatus: DividendIncreaseStreakStatus;
  streakYears: number | null;
}

export interface TickerListResponse {
  tickers: TickerSummaryResponse[];
}

// KrwDividendConversionResponse: com.dividendanatomy.web.fx.KrwDividendConversionResponse
export type FxConversionStatus = "CONVERTED" | "PAY_DATE_MISSING" | "NO_RATE_DATA_AVAILABLE";

export interface KrwConvertedEntry {
  exDividendDate: string;
  grossAmountUsd: number;
  netAmountUsd: number;
  status: FxConversionStatus;
  exchangeRate: number | null;
  grossAmountKrw: number | null;
  netAmountKrw: number | null;
}

export interface KrwDividendConversionResponse {
  tickerSymbol: string;
  entries: KrwConvertedEntry[];
}
