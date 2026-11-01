package com.travbook.payments.domain.model;
import java.math.BigDecimal;
import java.time.Instant;
public record FinancialSnapshot(BigDecimal captured,BigDecimal refunded,BigDecimal pendingRefund,BigDecimal authorized,
                                long accountCount,long captureCount,long refundCount,long integrityIssues,Instant asOf){
    public BigDecimal netCaptured(){return captured.subtract(refunded);}
}
