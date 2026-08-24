import { useEffect, useState } from "react";
import { motion } from "framer-motion";
import { getBrands } from "../api/timemachine";
import type { Brand } from "../api/types";

interface Props {
  onSelect: (brand: Brand) => void;
}

function LogoOrInitials({ brand }: { brand: Brand }) {
  const [failed, setFailed] = useState(false);
  if (failed) {
    return (
      <div className="flex h-20 w-20 items-center justify-center rounded-full bg-slate-100 text-lg font-bold text-slate-500">
        {brand.symbol.slice(0, 4)}
      </div>
    );
  }
  return (
    <img
      src={brand.logoPath}
      alt={brand.displayName}
      className="h-20 w-20 rounded-full object-contain"
      onError={() => setFailed(true)}
    />
  );
}

export default function BrandCardGrid({ onSelect }: Props) {
  const [brands, setBrands] = useState<Brand[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getBrands()
      .then(setBrands)
      .catch((err: Error) => setError(err.message));
  }, []);

  // 카드 탭 → 0.2초 확대 애니메이션이 보이고 나서 화면 전환(기획서 15-2).
  function handleTap(brand: Brand) {
    window.setTimeout(() => onSelect(brand), 200);
  }

  return (
    <div className="mx-auto flex max-w-md flex-col items-center gap-1 px-6 py-10 text-center">
      <h1 className="text-2xl font-bold text-slate-900">궁금한 회사를 골라보세요</h1>
      <p className="mb-8 text-sm text-slate-500">그동안 배당을 꾸준히 준 회사들이에요</p>

      {error && <p className="text-sm text-red-600">브랜드 목록을 불러오지 못했어요: {error}</p>}

      <div className="grid grid-cols-2 gap-4">
        {brands.map((brand) => (
          <motion.button
            key={brand.symbol}
            type="button"
            onClick={() => handleTap(brand)}
            whileTap={{ scale: 1.15 }}
            transition={{ duration: 0.2 }}
            className="flex flex-col items-center gap-2 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"
          >
            <LogoOrInitials brand={brand} />
            <span className="text-sm font-medium text-slate-800">{brand.displayName}</span>
          </motion.button>
        ))}
      </div>
    </div>
  );
}
