const symbol = qs("symbol");

async function loadDetail() {
    if (!symbol) {
        document.getElementById("ticker-title").textContent = "종목을 지정해주세요 (예: detail.html?symbol=KO)";
        return;
    }
    document.getElementById("ticker-title").textContent = symbol;

    const asOf = todayIso();
    const results = await Promise.allSettled([
        fetchJson(`/api/tickers/${symbol}/yield-decomposition?asOf=${asOf}`),
        fetchJson(`/api/tickers/${symbol}/growth-deceleration?asOf=${asOf}`),
        fetchJson(`/api/tickers/${symbol}/dividend-cuts`),
        fetchJson(`/api/tickers/${symbol}/volatility?asOf=${asOf}`),
        fetchJson(`/api/tickers/${symbol}/special-dividends`),
    ]);
    const [yieldR, growthR, cutR, volR, specialR] = results;

    const yieldData = renderYieldDecomposition(yieldR);
    const growthData = renderGrowth(growthR);
    const cutData = renderCuts(cutR);
    renderVolatility(volR);
    renderSpecialDividends(specialR);

    renderFlags(yieldData, growthData, cutData);
}

function renderYieldDecomposition(result) {
    const el = document.getElementById("yield-decomposition-section");
    if (result.status !== "fulfilled") {
        el.textContent = `데이터 없음: ${result.reason.message}`;
        return null;
    }
    const d = result.value;
    const c = d.actual;
    if (!c) {
        el.textContent = "이 구간은 계산할 수 없습니다 (배당 또는 가격 데이터 부족).";
        return null;
    }

    let dataQualityBadges = "";
    if (d.dataQuality) {
        if (!d.dataQuality.ttmCompleteAtT0 || !d.dataQuality.ttmCompleteAtT1) {
            dataQualityBadges += '<span class="badge badge-incomplete">TTM 데이터 불완전</span>';
        }
        if (d.dataQuality.priceFallbackUsedAtT0 || d.dataQuality.priceFallbackUsedAtT1) {
            dataQualityBadges += '<span class="badge badge-incomplete">가격 근접값 사용</span>';
        }
    }

    const dominant = Math.abs(c.priceContributionPercent) >= Math.abs(c.dividendContributionPercent) ? "가격" : "배당";
    el.className = "card";
    el.innerHTML = `
        <p class="muted">${d.t0} → ${d.t1}</p>
        <div class="contribution-bar-row">
            <span class="label">주가 기여</span>
            ${contributionBarHtml(c.priceContributionPercent)}
        </div>
        <div class="contribution-bar-row">
            <span class="label">배당 기여</span>
            ${contributionBarHtml(c.dividendContributionPercent)}
        </div>
        <p>이 기간 배당수익률 변화의 대부분은 <strong>${dominant}</strong> 요인에서 왔습니다.</p>
        <div>${dataQualityBadges}</div>
    `;
    return c;
}

function contributionBarHtml(percent) {
    const isPositive = percent >= 0;
    const widthPercent = Math.min(100, Math.abs(percent) * 8); // 시각적 스케일링, 계산값 아님
    return `
        <div class="contribution-bar-track">
            <div class="contribution-bar-fill ${isPositive ? "positive" : "negative"}" style="width:${widthPercent}%"></div>
        </div>
        <span class="value">${percent.toFixed(2)}%p</span>
    `;
}

function renderGrowth(result) {
    const el = document.getElementById("growth-card");
    if (result.status !== "fulfilled") {
        el.textContent = `성장률 둔화: 데이터 없음 (${result.reason.message})`;
        return null;
    }
    const d = result.value;
    const statusBadge = d.status === "DECELERATING"
        ? '<span class="badge badge-warning">⚠️ 둔화 중</span>'
        : '<span class="badge badge-status-ok">정상</span>';
    el.className = "card";
    el.innerHTML = `
        <h3>성장률 둔화 ${statusBadge}</h3>
        <p>3년 CAGR: ${d.cagrShortPercent != null ? formatPercent(d.cagrShortPercent) : "계산 불가"}
           · 10년 CAGR: ${d.cagrLongPercent != null ? formatPercent(d.cagrLongPercent) : "계산 불가"}</p>
    `;
    return d;
}

function renderCuts(result) {
    const el = document.getElementById("cut-card");
    if (result.status !== "fulfilled") {
        el.textContent = `삭감 이력: 데이터 없음 (${result.reason.message})`;
        return null;
    }
    const d = result.value;
    const comparisons = d.comparisons || [];
    const cutCount = comparisons.filter((c) => c.status === "CUT").length;
    const badge = cutCount > 0
        ? `<span class="badge badge-warning">⚠️ 삭감 ${cutCount}건</span>`
        : '<span class="badge badge-status-ok">삭감 이력 없음</span>';
    el.className = "card";
    el.innerHTML = `
        <h3>삭감 이력 ${badge}</h3>
        <p class="muted">전체 비교 ${comparisons.length}건 중 삭감 ${cutCount}건</p>
    `;
    return { cutCount, comparisons };
}

function renderVolatility(result) {
    const el = document.getElementById("volatility-card");
    if (result.status !== "fulfilled") {
        el.textContent = `변동성: 데이터 없음 (${result.reason.message})`;
        return;
    }
    const d = result.value;
    el.className = "card";
    if (d.status !== "COMPLETE") {
        el.innerHTML = `<h3>변동성</h3><p class="muted">10년치 데이터가 아직 부족해 계산할 수 없습니다.</p>`;
        return;
    }
    el.innerHTML = `
        <h3>변동성</h3>
        <p>연평균 배당 증감률 ${formatPercent(d.meanGrowthRatePercent)} (표준편차 ±${d.standardDeviationPercent.toFixed(2)}%p)</p>
    `;
}

function renderSpecialDividends(result) {
    const el = document.getElementById("special-dividend-section");
    if (result.status !== "fulfilled") {
        el.textContent = `데이터 없음: ${result.reason.message}`;
        return;
    }
    const d = result.value;
    const specials = d.entries.filter((e) => e.excluded);
    el.className = "card";
    if (specials.length === 0) {
        el.innerHTML = `<p class="muted">특별배당 이력 없음 (전체 ${d.regularCount}건 모두 정기 배당)</p>`;
        return;
    }
    const rows = specials.map((e) => `
        <tr>
            <td>${e.exDividendDate}</td>
            <td>${formatUsd(e.amount)}</td>
            <td class="muted">${e.exclusionReason}</td>
        </tr>
    `).join("");
    el.innerHTML = `
        <p class="muted">정기 배당 ${d.regularCount}건, 특별배당 ${d.specialCount}건 — 정기 지표 계산에서 제외됨</p>
        <table>
            <thead><tr><th>배당락일</th><th>금액</th><th>제외 근거</th></tr></thead>
            <tbody>${rows}</tbody>
        </table>
    `;
}

function renderFlags(yieldContribution, growth, cutInfo) {
    const flags = [];
    if (cutInfo && cutInfo.cutCount > 0) {
        flags.push("⚠️ 삭감 이력이 있습니다.");
    }
    if (growth && growth.status === "DECELERATING") {
        flags.push("⚠️ 배당 성장률이 둔화되고 있습니다.");
    }
    if (yieldContribution
        && yieldContribution.dividendContributionPercent < 0
        && yieldContribution.priceContributionPercent < yieldContribution.dividendContributionPercent) {
        flags.push("⚠️ 최근 배당수익률 상승은 배당 증가가 아니라 주가 하락이 주된 원인입니다.");
    }

    const el = document.getElementById("flags");
    el.innerHTML = flags.map((f) => `<span class="flag">${f}</span>`).join("");
}

loadDetail();
