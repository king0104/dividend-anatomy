// 기획서 7장 "상황별 책 요약 카드" — 지표 경고 시 짧은 조언 노출.
// 저작권 리스크 때문에 특정 책의 문장을 인용하지 않는다. 아래 문구는
// docs/decisions/14-dividend-safety-score-formula.md가 이미 임계값
// 출처로 인용한 원칙(Simply Safe Dividends, Marc Lichtenfeld, 워런
// 버핏, D/E·이자보상배율 관련 통설)을 참고해 전부 새로 쓴 문장이다 —
// 원문을 요약·발췌한 게 아니라 "일반적으로 통용되는 기준"을 서술한다.
// 종목 판단이 아니라 지표 해석 원칙만 다루므로 CLAUDE.md의 투자 조언
// 금지 규칙 대상이 아니다.

export interface BookInsight {
  text: string;
}

export const BOOK_INSIGHTS: Record<string, BookInsight> = {
  PAYOUT_RATIO: {
    text: "배당성향이 60%를 넘으면 이익이 줄어들 때 배당을 유지하기 어려워진다는 게 배당 투자서들이 공통으로 꼽는 기준이에요.",
  },
  FCF_PAYOUT_RATIO: {
    text: "배당은 회계상 이익이 아니라 실제 현금으로 나가요 — 잉여현금흐름 대비 배당 비율이 70%를 넘으면 현금 여력이 빠듯하다는 신호로 보는 시각이 많아요.",
  },
  ROE: {
    text: "워런 버핏은 자기자본이익률(ROE)을 여러 해에 걸쳐 15% 이상 유지하는 걸 우량 기업의 기준 중 하나로 꼽아왔어요.",
  },
  DEBT_TO_EQUITY: {
    text: "부채비율이 1배를 넘기 시작하면 이자·원금 상환 부담이 배당으로 갈 여력을 줄일 수 있다는 게 일반적인 시각이에요.",
  },
  INTEREST_COVERAGE: {
    text: "이자보상배율이 1.5배 아래로 내려가면 영업이익으로 이자도 겨우 갚는 수준이라, 배당보다 빚 상환이 먼저인 상황일 수 있어요.",
  },
};

/** subScore(0~20)가 안전 쪽 절반(10점) 아래로 내려간 지표만 카드를 보여준다. */
export function shouldShowBookInsight(subScore: number): boolean {
  return subScore < 10;
}
