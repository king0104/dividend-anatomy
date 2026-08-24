// 기획서 19-3 생활환산 매핑 테이블. 표시용 위트 요소일 뿐 도메인 금액 계산이
// 아니라서(백엔드가 이미 반올림까지 끝낸 KRW 정수를 나누기만 함) CLAUDE.md의
// BigDecimal/테스트 규칙 대상이 아니다.

interface LifeCostItem {
  label: string;
  unitPriceKrw: number;
  minDifferenceKrw: number;
}

// 차액 규모가 큰 항목부터 확인해 첫 번째로 맞는 티어를 고른다.
// 기획서 19-3: 10만원 미만 아메리카노 / 10만~100만원 치킨 / 100만원 이상 통신비·구독료.
// 100만원 이상 티어는 통신비 하나로 대표한다(둘 다 예시일 뿐 구분 기준이 없음).
const ITEMS: LifeCostItem[] = [
  { label: "통신비(1개월)", unitPriceKrw: 55_000, minDifferenceKrw: 1_000_000 },
  { label: "치킨", unitPriceKrw: 20_000, minDifferenceKrw: 100_000 },
  { label: "아메리카노", unitPriceKrw: 4_500, minDifferenceKrw: 0 },
];

export interface LifeCostResult {
  label: string;
  quantity: number;
}

export function lifeCostConversion(differenceKrw: number): LifeCostResult {
  const item = ITEMS.find((candidate) => differenceKrw >= candidate.minDifferenceKrw) ?? ITEMS[ITEMS.length - 1];
  const quantity = Math.max(0, Math.floor(differenceKrw / item.unitPriceKrw));
  return { label: item.label, quantity };
}
