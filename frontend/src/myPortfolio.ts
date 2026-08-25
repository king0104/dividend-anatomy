// 친구가 보낸 공유 링크로 비교 화면을 열었을 때 "내 포트폴리오"와 견줘
// 보여주기 위해, 마지막으로 완성한 포트폴리오를 기기별 localStorage에
// 남긴다. streak.ts와 같은 이유로 회원가입 없이 기기 단위로만 남는다.
import type { Selection } from "./monthlyBucket";

const STORAGE_KEY = "dividend-playground:my-portfolio";

export interface SavedPortfolio {
  selections: Selection[];
  monthlyGoalKrw: number;
}

export function saveMyPortfolio(portfolio: SavedPortfolio): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(portfolio));
  } catch {
    // 저장 실패해도 현재 세션 동작에는 영향 없어야 한다.
  }
}

export function loadMyPortfolio(): SavedPortfolio | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed.selections) && typeof parsed.monthlyGoalKrw === "number") {
      return parsed;
    }
    return null;
  } catch {
    return null;
  }
}
