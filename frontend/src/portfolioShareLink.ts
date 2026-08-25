// 기획서 7장 "친구 캘린더 비교 — 선택적 공개". 회원가입·서버 저장 없이
// 순수하게 URL 하나로 공유한다 — 포트폴리오 내용을 쿼리 파라미터에
// 인코딩해서 링크에 담고, 서버에는 아무것도 남기지 않는다. 링크를
// 실제로 보내는 것 자체가 "선택적 공개"다.
import type { Selection } from "./monthlyBucket";

const SHARE_PARAM = "shared";
const GOAL_PARAM = "goal";

export function encodeSelections(selections: Selection[]): string {
  return selections.map((s) => `${s.symbol}:${s.quantity}`).join(",");
}

export function decodeSelections(raw: string): Selection[] {
  return raw
    .split(",")
    .map((pair) => {
      const [symbol, qtyStr] = pair.split(":");
      const quantity = Number(qtyStr);
      if (!symbol || !Number.isFinite(quantity) || quantity <= 0) return null;
      return { symbol, quantity };
    })
    .filter((s): s is Selection => s !== null);
}

export function buildShareUrl(selections: Selection[], monthlyGoalKrw: number): string {
  const url = new URL(window.location.href);
  url.search = "";
  url.searchParams.set(SHARE_PARAM, encodeSelections(selections));
  url.searchParams.set(GOAL_PARAM, String(Math.round(monthlyGoalKrw)));
  return url.toString();
}

export interface SharedPortfolio {
  selections: Selection[];
  monthlyGoalKrw: number;
}

/** 현재 페이지 URL에 공유 파라미터가 있으면 파싱해 돌려준다. */
export function parseSharedFromLocation(search: string = window.location.search): SharedPortfolio | null {
  const params = new URLSearchParams(search);
  const sharedRaw = params.get(SHARE_PARAM);
  if (!sharedRaw) return null;

  const selections = decodeSelections(sharedRaw);
  if (selections.length === 0) return null;

  const goalRaw = params.get(GOAL_PARAM);
  const monthlyGoalKrw = goalRaw ? Number(goalRaw) : 0;

  return { selections, monthlyGoalKrw: Number.isFinite(monthlyGoalKrw) ? monthlyGoalKrw : 0 };
}
