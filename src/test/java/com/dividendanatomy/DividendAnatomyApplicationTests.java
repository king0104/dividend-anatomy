package com.dividendanatomy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 실제 OCI MySQL(터널 필요)에 의존하지 않도록 "test" 프로파일(H2 인메모리,
 * application-test.properties)로 전체 컨텍스트를 띄운다.
 */
@SpringBootTest
@ActiveProfiles("test")
class DividendAnatomyApplicationTests {

	@Test
	void contextLoads() {
	}

}
