import { useState } from "react";

interface Props {
  symbols: string[];
}

/**
 * 기획서 2장 대응 원칙 — "실행 CTA는 특정 증권사 추천 없이 '내가 쓰는
 * 증권사에서 검색'". 특정 증권사·URL을 걸지 않고 티커를 클립보드에
 * 복사해주는 것으로 대신한다 — 어떤 링크도 필요 없어 추천처럼 보일
 * 여지 자체가 없다.
 */
export default function BrokerSearchCta({ symbols }: Props) {
  const [status, setStatus] = useState<"idle" | "copied" | "failed">("idle");
  const text = symbols.join(", ");

  async function handleClick() {
    try {
      await navigator.clipboard.writeText(text);
      setStatus("copied");
    } catch {
      setStatus("failed");
    }
    window.setTimeout(() => setStatus("idle"), 3000);
  }

  return (
    <div className="flex flex-col items-center gap-1">
      <button
        type="button"
        onClick={handleClick}
        className="rounded-xl border border-slate-300 px-5 py-2 text-sm font-medium text-slate-700"
      >
        내가 쓰는 증권사에서 검색해보기
      </button>
      {status === "copied" && (
        <p className="text-xs text-slate-400">
          '{text}' 복사했어요 — 증권사 앱에서 붙여넣어 검색해보세요.
        </p>
      )}
      {status === "failed" && (
        <p className="text-xs text-slate-400">복사에 실패했어요. 직접 적어서 검색해보세요: {text}</p>
      )}
    </div>
  );
}
