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
