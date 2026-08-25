import { fetchJson } from "./fetchJson";
import type { DividendSafetyScoreResponse } from "./types";

export function getSafetyScore(symbol: string): Promise<DividendSafetyScoreResponse> {
  return fetchJson<DividendSafetyScoreResponse>(`/api/tickers/${symbol}/safety-score`);
}
