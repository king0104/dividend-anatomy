package com.dividendanatomy.domain.timemachine;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 시뮬레이션 대상 기간 중 한 해의 체크포인트. checkpointDate는 그 해의
 * 끝(또는 오늘까지의 마지막 해라면 오늘)이고, dividendPerShare는 직전
 * 체크포인트 이후 그 해에 받은 배당(분할 조정 완료)의 합이다.
 */
public record YearlyCheckpoint(LocalDate checkpointDate, BigDecimal dividendPerShare, BigDecimal price) {
}
