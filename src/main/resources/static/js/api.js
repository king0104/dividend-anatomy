/**
 * 공용 fetch 헬퍼. 계산 로직 없음 — 이미 서버가 계산한 JSON을
 * 그대로 받아오는 것뿐이라 CLAUDE.md의 계산 로직 테스트 규칙
 * 대상이 아니다.
 */
async function fetchJson(url) {
    const response = await fetch(url);
    if (!response.ok) {
        let message = `요청 실패 (HTTP ${response.status})`;
        try {
            const body = await response.json();
            if (body && body.message) {
                message = body.message;
            }
        } catch (ignored) {
            // 본문이 JSON이 아니면 기본 메시지 사용
        }
        throw new Error(message);
    }
    return response.json();
}

function todayIso() {
    return new Date().toISOString().slice(0, 10);
}

function formatPercent(value) {
    if (value === null || value === undefined) {
        return "-";
    }
    return `${value.toFixed(2)}%`;
}

function formatUsd(value) {
    if (value === null || value === undefined) {
        return "-";
    }
    return `$${value.toFixed(2)}`;
}

function formatKrw(value) {
    if (value === null || value === undefined) {
        return "-";
    }
    return `${Math.round(value).toLocaleString("ko-KR")}원`;
}

function qs(name) {
    return new URLSearchParams(window.location.search).get(name);
}
