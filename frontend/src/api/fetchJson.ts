// 기존 static/js/api.js의 fetchJson 패턴(응답 실패 시 body.message 파싱) 재사용.
// dev에서는 vite.config.ts의 /api 프록시, 배포본에서는 vercel.json의 rewrite가
// 각각 실제 백엔드로 라우팅하므로 여기선 항상 상대경로만 쓴다.
export async function fetchJson<T>(url: string): Promise<T> {
  const response = await fetch(url);
  if (!response.ok) {
    let message = `요청 실패 (HTTP ${response.status})`;
    try {
      const body = await response.json();
      if (body && typeof body.message === "string") {
        message = body.message;
      }
    } catch {
      // 본문이 JSON이 아니면 기본 메시지 사용
    }
    throw new Error(message);
  }
  return response.json();
}
