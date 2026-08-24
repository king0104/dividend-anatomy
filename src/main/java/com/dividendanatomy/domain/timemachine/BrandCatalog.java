package com.dividendanatomy.domain.timemachine;

import java.util.List;

/**
 * 타임머신 시뮬레이터 화면 1(브랜드 선택)에 노출할 8개 브랜드 큐레이션
 * (배당연습장 기획서 14-3절). 8개짜리 고정 큐레이션이라 DB 테이블/리포지토리
 * 없이 정적 목록으로 둔다.
 *
 * CUT 2개는 실제 배당 삭감 사례여야 한다 — MMM은 2024년 Solventum
 * 스핀오프로 실제 배당이 삭감된 사례로 이미 이 프로젝트의 배당 삭감
 * 탐지 기능이 잡아낸 종목이다(docs/decisions/10-universe-selection.md).
 * GE는 2009년·2017~2018년 두 차례 대규모 배당 삭감으로 잘 알려진
 * 사례이며, 지금 유니버스엔 없어 신규 수집이 필요하다.
 */
public final class BrandCatalog {

    public static final List<Brand> ALL = List.of(
            new Brand("KO", "코카콜라", "/logos/ko.png", BrandCategory.SUCCESS),
            new Brand("JNJ", "존슨앤드존슨", "/logos/jnj.png", BrandCategory.SUCCESS),
            new Brand("MCD", "맥도날드", "/logos/mcd.png", BrandCategory.SUCCESS),
            new Brand("AAPL", "애플", "/logos/aapl.png", BrandCategory.SUCCESS),
            new Brand("MSFT", "마이크로소프트", "/logos/msft.png", BrandCategory.SUCCESS),
            new Brand("NKE", "나이키", "/logos/nke.png", BrandCategory.SUCCESS),
            new Brand("MMM", "3M", "/logos/mmm.png", BrandCategory.CUT),
            new Brand("GE", "제너럴일렉트릭", "/logos/ge.png", BrandCategory.CUT));

    private BrandCatalog() {
    }
}
