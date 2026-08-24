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

const STREAK_COVERAGE_NOTE = "이 숫자는 회사의 실제 연속 배당 증가 역사가 아니라, 우리 DB가 보유한 배당 이력 범위 안에서 계산된 값입니다. DB 커버리지가 특정 연도부터 시작하는 종목은 실제보다 짧게 나올 수 있습니다.";

function formatStreakCell(t) {
    if (t.streakStatus === "INSUFFICIENT_DATA" || t.streakYears == null) {
        return '<span class="muted">판정 불가</span>';
    }
    return `<span title="${STREAK_COVERAGE_NOTE}">${t.streakYears}년 ⓘ</span>`;
}

loadTickers();
