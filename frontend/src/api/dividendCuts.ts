import { fetchJson } from "./fetchJson";
import type { DividendCutResponse } from "./types";

export function getDividendCuts(symbol: string): Promise<DividendCutResponse> {
  return fetchJson<DividendCutResponse>(`/api/tickers/${symbol}/dividend-cuts`);
}
