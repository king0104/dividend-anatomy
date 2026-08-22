package com.dividendanatomy.ingestion.massive;

import java.util.List;

/** splits/dividends 응답이 공통으로 갖는 형태 — 페이지네이션 공통 처리를 위한 인터페이스. */
public interface MassivePaginatedResponse<T> {
    List<T> results();

    String nextUrl();
}
