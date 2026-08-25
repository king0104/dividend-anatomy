import { fetchJson } from "./fetchJson";
import type { Brand, InvestMode, TimeMachineSimulationResponse } from "./types";

export function getBrands(): Promise<Brand[]> {
  return fetchJson<Brand[]>("/api/timemachine/brands");
}

export interface SimulateParams {
  symbol: string;
  investMode: InvestMode;
  amountKrw: number;
  periodYears: number;
  asOf: string;
}

export function simulate(params: SimulateParams): Promise<TimeMachineSimulationResponse> {
  const query = new URLSearchParams({
    investMode: params.investMode,
    amountKrw: String(params.amountKrw),
    periodYears: String(params.periodYears),
    asOf: params.asOf,
  });
  return fetchJson<TimeMachineSimulationResponse>(
    `/api/tickers/${params.symbol}/timemachine?${query.toString()}`,
  );
}

export function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}
