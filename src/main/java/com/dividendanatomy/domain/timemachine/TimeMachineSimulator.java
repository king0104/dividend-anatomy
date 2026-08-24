package com.dividendanatomy.domain.timemachine;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/**
 * "재투자를 했다면 vs 안 했다면"을 계산한다 (배당연습장 기획서 19장
 * 의사코드를 BigDecimal로 재구현). 매년 받는 배당은 그 해 체크포인트
 * 가격에 전량 재투자한다고 가정하고, 적립식(MONTHLY)이면 매달 같은
 * 금액을 그 해 체크포인트 가격으로 12번 매수한다고 가정한다 — 실제
 * 매수 시점별 가격이 아니라 연 단위로 단순화한 근사치다.
 */
public final class TimeMachineSimulator {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    private TimeMachineSimulator() {
    }

    public static TimeMachineResult simulate(
            BigDecimal startPrice,
            List<YearlyCheckpoint> yearlyCheckpoints,
            BigDecimal initialPrincipal,
            BigDecimal monthlyContribution,
            InvestMode mode) {
        if (startPrice.signum() <= 0) {
            throw new IllegalArgumentException("startPrice(%s)는 0보다 커야 한다".formatted(startPrice));
        }
        if (yearlyCheckpoints.isEmpty()) {
            throw new IllegalArgumentException("yearlyCheckpoints는 비어있을 수 없다");
        }

        BigDecimal sharesReinvest = initialPrincipal.divide(startPrice, MC);
        BigDecimal sharesNoReinvest = sharesReinvest;
        BigDecimal cashNoReinvest = BigDecimal.ZERO;
        BigDecimal totalInvested = initialPrincipal;

        List<TimeMachineResult.YearlySnapshot> yearlySeries = new ArrayList<>();
        BigDecimal lastPrice = startPrice;

        for (YearlyCheckpoint checkpoint : yearlyCheckpoints) {
            BigDecimal price = checkpoint.price();
            if (price.signum() <= 0) {
                throw new IllegalArgumentException("checkpoint price(%s)는 0보다 커야 한다".formatted(price));
            }

            BigDecimal dividendReceived = sharesReinvest.multiply(checkpoint.dividendPerShare(), MC);
            BigDecimal newShares = dividendReceived.divide(price, MC);
            sharesReinvest = sharesReinvest.add(newShares, MC);

            cashNoReinvest = cashNoReinvest.add(sharesNoReinvest.multiply(checkpoint.dividendPerShare(), MC), MC);

            if (mode == InvestMode.MONTHLY) {
                BigDecimal monthlyShares = monthlyContribution.divide(price, MC);
                BigDecimal yearlyShares = monthlyShares.multiply(TWELVE, MC);
                sharesReinvest = sharesReinvest.add(yearlyShares, MC);
                sharesNoReinvest = sharesNoReinvest.add(yearlyShares, MC);
                totalInvested = totalInvested.add(monthlyContribution.multiply(TWELVE, MC), MC);
            }

            BigDecimal reinvestValue = sharesReinvest.multiply(price, MC);
            BigDecimal noReinvestValue = sharesNoReinvest.multiply(price, MC).add(cashNoReinvest, MC);
            yearlySeries.add(new TimeMachineResult.YearlySnapshot(checkpoint.checkpointDate(), reinvestValue, noReinvestValue));

            lastPrice = price;
        }

        BigDecimal finalValueReinvest = sharesReinvest.multiply(lastPrice, MC);
        BigDecimal finalValueNoReinvest = sharesNoReinvest.multiply(lastPrice, MC).add(cashNoReinvest, MC);
        BigDecimal difference = finalValueReinvest.subtract(finalValueNoReinvest, MC);
        BigDecimal totalReturnRatio = totalInvested.signum() == 0
                ? BigDecimal.ZERO
                : finalValueReinvest.subtract(totalInvested, MC).divide(totalInvested, MC);

        return new TimeMachineResult(finalValueReinvest, finalValueNoReinvest, difference, totalReturnRatio, yearlySeries);
    }
}
