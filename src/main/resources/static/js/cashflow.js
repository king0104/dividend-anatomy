let tickerList = [];

async function init() {
    const statusEl = document.getElementById("status");
    try {
        const data = await fetchJson("/api/tickers");
        tickerList = data.tickers;
        renderQuantityTable();
        statusEl.style.display = "none";
        document.getElementById("quantity-table").style.display = "table";
    } catch (e) {
        statusEl.textContent = `종목 목록을 불러오지 못했습니다: ${e.message}`;
        statusEl.classList.add("error-text");
    }
    document.getElementById("calc-btn").addEventListener("click", calculate);
}

function renderQuantityTable() {
    const tbody = document.getElementById("quantity-tbody");
    tbody.innerHTML = tickerList.map((t) => `
        <tr>
            <td><strong>${t.symbol}</strong></td>
            <td>${t.name}</td>
            <td><input type="number" min="0" value="0" data-symbol="${t.symbol}" class="qty-input"></td>
        </tr>
    `).join("");
}

/** 최근 12개월(이번 달 포함) 버킷을 오래된 순으로 만든다. */
function buildMonthBuckets() {
    const buckets = [];
    const now = new Date();
    for (let i = 11; i >= 0; i--) {
        const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
        buckets.push({ year: d.getFullYear(), month: d.getMonth() + 1, total: 0, contributions: [] });
    }
    return buckets;
}

function bucketKey(year, month) {
    return `${year}-${String(month).padStart(2, "0")}`;
}

async function calculate() {
    const taxMode = document.querySelector('input[name="tax"]:checked').value;
    const currencyMode = document.querySelector('input[name="currency"]:checked').value;

    const quantities = Array.from(document.querySelectorAll(".qty-input"))
        .map((el) => ({ symbol: el.dataset.symbol, qty: Number(el.value) }))
        .filter((q) => q.qty > 0);

    const buckets = buildMonthBuckets();
    const bucketIndex = new Map(buckets.map((b, idx) => [bucketKey(b.year, b.month), idx]));
    const earliestAllowed = new Date(buckets[0].year, buckets[0].month - 1, 1);

    const skipped = []; // 환율 데이터 없음 등으로 합계에서 빠진 건 — 화면에 표시해야 함(CLAUDE.md: 조용히 넘어가지 않는다)

    for (const { symbol, qty } of quantities) {
        try {
            const entries = await fetchEntriesForTicker(symbol, currencyMode);
            for (const entry of entries) {
                const exDate = new Date(entry.exDividendDate);
                if (exDate < earliestAllowed) {
                    continue;
                }
                const key = bucketKey(exDate.getFullYear(), exDate.getMonth() + 1);
                const idx = bucketIndex.get(key);
                if (idx === undefined) {
                    continue;
                }
                const amount = pickAmount(entry, taxMode, currencyMode);
                if (amount == null) {
                    skipped.push({ symbol, exDividendDate: entry.exDividendDate, status: entry.status });
                    continue;
                }
                const contribution = amount * qty;
                buckets[idx].total += contribution;
                buckets[idx].contributions.push({ symbol, amount: contribution });
            }
        } catch (e) {
            console.warn(`${symbol} 지급 이력을 가져오지 못함: ${e.message}`);
        }
    }

    renderChart(buckets, currencyMode);
    renderDataQualityNotice(skipped);
}

function renderDataQualityNotice(skipped) {
    const el = document.getElementById("data-quality-notice");
    if (skipped.length === 0) {
        el.style.display = "none";
        el.innerHTML = "";
        return;
    }
    const items = skipped
        .map((s) => `<li>${s.symbol} — ${s.exDividendDate} (${s.status ?? "계산 불가"})</li>`)
        .join("");
    el.style.display = "block";
    el.innerHTML = `
        <span class="badge badge-incomplete">데이터 불완전</span>
        <span class="muted">아래 ${skipped.length}건은 데이터가 부족해 합계에서 제외됐습니다:</span>
        <ul class="muted">${items}</ul>
    `;
}

async function fetchEntriesForTicker(symbol, currencyMode) {
    if (currencyMode === "krw") {
        const data = await fetchJson(`/api/tickers/${symbol}/krw-dividends`);
        return data.entries;
    }
    const data = await fetchJson(`/api/tickers/${symbol}/net-dividends`);
    return data.entries;
}

function pickAmount(entry, taxMode, currencyMode) {
    if (currencyMode === "krw") {
        if (entry.status !== "CONVERTED") {
            return null;
        }
        return taxMode === "posttax" ? entry.netAmountKrw : entry.grossAmountKrw;
    }
    return taxMode === "posttax" ? entry.netAmount : entry.grossAmount;
}

function renderChart(buckets, currencyMode) {
    const chartEl = document.getElementById("chart");
    const maxValue = Math.max(...buckets.map((b) => b.total), 1);
    const monthNames = ["1월", "2월", "3월", "4월", "5월", "6월", "7월", "8월", "9월", "10월", "11월", "12월"];

    chartEl.innerHTML = buckets.map((b, idx) => {
        const heightPercent = (b.total / maxValue) * 100;
        return `
            <div class="chart-bar-wrap" data-idx="${idx}">
                <div class="chart-bar" style="height:${heightPercent}%"></div>
                <div class="chart-label">${monthNames[b.month - 1]}</div>
            </div>
        `;
    }).join("");

    chartEl.querySelectorAll(".chart-bar-wrap").forEach((el) => {
        el.addEventListener("click", () => showMonthDetail(buckets, Number(el.dataset.idx), currencyMode, el));
    });
}

function showMonthDetail(buckets, idx, currencyMode, clickedEl) {
    document.querySelectorAll(".chart-bar-wrap").forEach((el) => el.classList.remove("selected"));
    clickedEl.classList.add("selected");

    const bucket = buckets[idx];
    const detailEl = document.getElementById("month-detail");
    detailEl.style.display = "block";
    detailEl.className = "card";

    if (bucket.contributions.length === 0) {
        detailEl.innerHTML = `<h3>${bucket.year}년 ${bucket.month}월</h3><p class="muted">이 달엔 지급된 배당이 없습니다.</p>`;
        return;
    }

    const format = currencyMode === "krw" ? formatKrw : formatUsd;
    const sorted = [...bucket.contributions].sort((a, b) => b.amount - a.amount);
    const rows = sorted.map((c) => `<tr><td>${c.symbol}</td><td>${format(c.amount)}</td></tr>`).join("");

    detailEl.innerHTML = `
        <h3>${bucket.year}년 ${bucket.month}월 — 합계 ${format(bucket.total)}</h3>
        <table><thead><tr><th>종목</th><th>금액</th></tr></thead><tbody>${rows}</tbody></table>
    `;
}

init();
