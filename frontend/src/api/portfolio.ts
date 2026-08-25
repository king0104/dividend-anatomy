import { fetchJson } from "./fetchJson";
import type { KrwDividendConversionResponse, TickerListResponse, TickerSummaryResponse } from "./types";

export function getTickers(): Promise<TickerSummaryResponse[]> {
  return fetchJson<TickerListResponse>("/api/tickers").then((data) => data.tickers);
}

export function getKrwDividends(symbol: string): Promise<KrwDividendConversionResponse> {
  return fetchJson<KrwDividendConversionResponse>(`/api/tickers/${symbol}/krw-dividends`);
}
