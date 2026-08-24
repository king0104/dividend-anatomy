const HIGH_YIELD_WARNING_THRESHOLD = 6.0; // %p — PROJECT.md 2절 알트리아(8.2%) 예시 참고, 보수적으로 6%

async function loadTickers() {
    const statusEl = document.getElementById("status");
    try {
        const data = await fetchJson("/api/tickers");
        renderTickers(data.tickers);
        statusEl.style.display = "none";
        document.getElementById("ticker-table").style.display = "table";
    } catch (e) {
        statusEl.textContent = `종목 목록을 불러오지 못했습니다: ${e.message}`;
        statusEl.classList.add("error-text");
    }
}

function renderTickers(tickers) {
    const tbody = document.getElementById("ticker-tbody");
    tbody.innerHTML = "";

    tickers.forEach((t) => {
        const tr = document.createElement("tr");
        tr.className = "clickable";
        tr.addEventListener("click", () => {
            window.location.href = `/detail.html?symbol=${encodeURIComponent(t.symbol)}`;
        });

        const yieldCell = formatYieldCell(t);
        const streakCell = formatStreakCell(t);

        tr.innerHTML = `
            <td><strong>${t.symbol}</strong></td>
            <td>${t.name}</td>
            <td>${t.currentPrice != null ? formatUsd(t.currentPrice) : '<span class="muted">가격 데이터 없음</span>'}</td>
            <td>${yieldCell}</td>
            <td>${streakCell}</td>
            <td>${t.regularPaymentsPerYear != null ? `연 ${t.regularPaymentsPerYear}회` : "-"}</td>
        `;
        tbody.appendChild(tr);
    });
}

function formatYieldCell(t) {
    if (t.currentYieldPercent == null) {
        return '<span class="muted">계산 불가</span>';
    }
    let html = formatPercent(t.currentYieldPercent);
    if (t.currentYieldPercent >= HIGH_YIELD_WARNING_THRESHOLD) {
        html += ' <span class="badge badge-warning">⚠️ 확인 필요</span>';
    }
    if (!t.dataComplete) {
        html += ' <span class="badge badge-incomplete">데이터 불완전</span>';
    }
    return html;
}

function formatStreakCell(t) {
    if (t.streakStatus === "INSUFFICIENT_DATA" || t.streakYears == null) {
        return '<span class="muted">판정 불가</span>';
    }
    return `${t.streakYears}년`;
}

loadTickers();
