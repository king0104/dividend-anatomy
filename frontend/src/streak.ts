// 기획서 7장 "완화된 DCA 스트릭 — 확인만 해도 인정". 실제로 매달 사야
// 인정되는 진짜 DCA가 아니라, 포트폴리오 결과 화면을 하루 한 번이라도
// 열어보면 그 날짜가 스트릭으로 쌓인다. 회원가입이 없으므로 기기별
// localStorage에만 남는다 — 다른 기기·브라우저와 공유되지 않는다.
export interface StreakState {
  streakDays: number;
  lastCheckInDate: string; // YYYY-MM-DD
}

const STORAGE_KEY = "dividend-playground:streak";

function todayString(now: Date): string {
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, "0");
  const d = String(now.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

function daysBetween(a: string, b: string): number {
  const dayMs = 24 * 60 * 60 * 1000;
  return Math.round((new Date(b).getTime() - new Date(a).getTime()) / dayMs);
}

/** 저장된 상태와 오늘 날짜를 받아 다음 상태를 계산하는 순수 함수. */
export function nextStreakState(stored: StreakState | null, now: Date = new Date()): StreakState {
  const today = todayString(now);

  if (!stored) {
    return { streakDays: 1, lastCheckInDate: today };
  }
  if (stored.lastCheckInDate === today) {
    return stored;
  }
  const gap = daysBetween(stored.lastCheckInDate, today);
  const streakDays = gap === 1 ? stored.streakDays + 1 : 1;
  return { streakDays, lastCheckInDate: today };
}

function readStoredState(): StreakState | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (typeof parsed.streakDays === "number" && typeof parsed.lastCheckInDate === "string") {
      return parsed;
    }
    return null;
  } catch {
    return null;
  }
}

/** 오늘 체크인을 기록하고(이미 했으면 그대로) 최신 스트릭 상태를 반환한다. */
export function recordCheckIn(now: Date = new Date()): StreakState {
  const next = nextStreakState(readStoredState(), now);
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  } catch {
    // 프라이빗 브라우징 등으로 저장이 막혀도 화면 표시 자체는 계속 동작해야 한다.
  }
  return next;
}
