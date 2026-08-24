import { useEffect, useRef } from "react";
import type { Brand, TimeMachineSimulationResponse } from "../api/types";

interface Props {
  brand: Brand;
  periodYears: number;
  result: TimeMachineSimulationResponse;
  onClose: () => void;
}

const SIZE = 1080;

function loadLogo(logoPath: string): Promise<HTMLImageElement | null> {
  return new Promise((resolve) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => resolve(null);
    img.src = logoPath;
  });
}

async function draw(canvas: HTMLCanvasElement, brand: Brand, periodYears: number, result: TimeMachineSimulationResponse) {
  const ctx = canvas.getContext("2d");
  if (!ctx) return;

  ctx.fillStyle = "#0f172a";
  ctx.fillRect(0, 0, SIZE, SIZE);

  const logo = await loadLogo(brand.logoPath);
  const logoCenterX = SIZE / 2;
  const logoCenterY = 300;
  const logoRadius = 140;

  ctx.save();
  ctx.beginPath();
  ctx.arc(logoCenterX, logoCenterY, logoRadius, 0, Math.PI * 2);
  ctx.closePath();
  ctx.fillStyle = "#ffffff";
  ctx.fill();
  ctx.clip();
  if (logo) {
    // 로고 원본 비율을 유지한 채(찌그러뜨리지 않고) 원 안에 맞춰 그린다 —
    // 실제 브랜드 로고는 가로세로 비율이 제각각(예: 존슨앤드존슨은 매우 넓적함).
    const padding = 0.7; // 원 지름의 70%까지만 채워서 여백 확보
    const maxSize = logoRadius * 2 * padding;
    const scale = Math.min(maxSize / logo.width, maxSize / logo.height);
    const drawWidth = logo.width * scale;
    const drawHeight = logo.height * scale;
    ctx.drawImage(
      logo,
      logoCenterX - drawWidth / 2,
      logoCenterY - drawHeight / 2,
      drawWidth,
      drawHeight,
    );
  } else {
    ctx.fillStyle = "#e2e8f0";
    ctx.fillRect(logoCenterX - logoRadius, logoCenterY - logoRadius, logoRadius * 2, logoRadius * 2);
    ctx.fillStyle = "#475569";
    ctx.font = "bold 64px system-ui, sans-serif";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText(brand.symbol.slice(0, 4), logoCenterX, logoCenterY);
  }
  ctx.restore();

  ctx.fillStyle = "#f8fafc";
  ctx.textAlign = "center";
  ctx.font = "bold 56px system-ui, sans-serif";
  ctx.fillText(`${brand.displayName}를`, SIZE / 2, 540);
  ctx.fillText(`${periodYears}년 전에 알았더라면`, SIZE / 2, 610);

  ctx.fillStyle = "#60a5fa";
  ctx.font = "bold 88px system-ui, sans-serif";
  const finalValueText = `${Math.round(result.finalValueReinvestKrw).toLocaleString("ko-KR")}원`;
  ctx.fillText(finalValueText, SIZE / 2, 760);

  ctx.fillStyle = "#cbd5e1";
  ctx.font = "40px system-ui, sans-serif";
  ctx.fillText(
    `현금으로 받았을 때보다 +${Math.round(result.differenceKrw).toLocaleString("ko-KR")}원`,
    SIZE / 2,
    830,
  );

  ctx.fillStyle = "#64748b";
  ctx.font = "28px system-ui, sans-serif";
  ctx.fillText("과거 데이터 기준 시뮬레이션이며 투자 조언이 아닙니다", SIZE / 2, 940);

  ctx.fillStyle = "#94a3b8";
  ctx.font = "bold 32px system-ui, sans-serif";
  ctx.fillText("배당연습장 · 타임머신", SIZE / 2, 1010);
}

export default function ShareCard({ brand, periodYears, result, onClose }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    draw(canvas, brand, periodYears, result);
  }, [brand, periodYears, result]);

  function handleDownload() {
    const canvas = canvasRef.current;
    if (!canvas) return;
    canvas.toBlob((blob) => {
      if (!blob) return;
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `timemachine-${brand.symbol}.png`;
      anchor.click();
      URL.revokeObjectURL(url);
    }, "image/png");
  }

  return (
    <div className="fixed inset-0 flex flex-col items-center justify-center gap-4 bg-black/70 p-6">
      <canvas ref={canvasRef} width={SIZE} height={SIZE} className="w-full max-w-sm rounded-2xl shadow-xl" />
      <div className="flex gap-3">
        <button
          type="button"
          onClick={handleDownload}
          className="rounded-xl bg-blue-600 px-5 py-2 text-sm font-semibold text-white"
        >
          이미지 다운로드
        </button>
        <button
          type="button"
          onClick={onClose}
          className="rounded-xl bg-slate-200 px-5 py-2 text-sm font-semibold text-slate-700"
        >
          닫기
        </button>
      </div>
    </div>
  );
}
