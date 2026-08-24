package com.dividendanatomy.web.timemachine;

import com.dividendanatomy.domain.timemachine.Brand;
import com.dividendanatomy.domain.timemachine.BrandCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 타임머신 시뮬레이터 화면 1(브랜드 선택)용 고정 큐레이션 목록. 계산 없는 정적 데이터라 별도 Response/Mapper 없이 그대로 반환한다. */
@RestController
@RequestMapping("/api/timemachine")
public class BrandCatalogController {

    @GetMapping("/brands")
    public List<Brand> getBrands() {
        return BrandCatalog.ALL;
    }
}
