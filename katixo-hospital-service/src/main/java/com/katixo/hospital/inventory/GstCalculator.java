package com.katixo.hospital.inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * GST split for pharmacy sales. Indian retail MRP is GST-inclusive, so the
 * taxable value is back-computed and the tax is split into CGST+SGST for an
 * intra-state sale (the normal hospital case) or IGST for inter-state.
 *
 * <p>Rounding is HALF_UP to 2 dp; for intra-state the SGST leg absorbs any
 * 1-paisa rounding residue so CGST+SGST always equals the total tax exactly.
 */
public final class GstCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWO = new BigDecimal("2");

    private GstCalculator() {
    }

    public record GstAmounts(
            BigDecimal taxableValue,
            BigDecimal cgst,
            BigDecimal sgst,
            BigDecimal igst,
            BigDecimal taxTotal,
            BigDecimal grossTotal) {
    }

    /** Splits a GST-inclusive amount (e.g. MRP × qty) at the given rate. */
    public static GstAmounts fromInclusive(BigDecimal inclusiveAmount, BigDecimal gstRatePercent,
                                           boolean interState) {
        BigDecimal gross = scale(inclusiveAmount == null ? BigDecimal.ZERO : inclusiveAmount);
        BigDecimal rate = gstRatePercent == null ? BigDecimal.ZERO : gstRatePercent;

        if (rate.signum() == 0) {
            return new GstAmounts(gross, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, gross);
        }

        BigDecimal taxable = gross.multiply(HUNDRED)
                .divide(HUNDRED.add(rate), 2, RoundingMode.HALF_UP);
        BigDecimal tax = gross.subtract(taxable);

        if (interState) {
            return new GstAmounts(taxable, BigDecimal.ZERO, BigDecimal.ZERO, scale(tax), scale(tax), gross);
        }
        BigDecimal cgst = tax.divide(TWO, 2, RoundingMode.HALF_UP);
        BigDecimal sgst = tax.subtract(cgst); // absorbs the rounding residue
        return new GstAmounts(taxable, cgst, sgst, BigDecimal.ZERO, scale(tax), gross);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
