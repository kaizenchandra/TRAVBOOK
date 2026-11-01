package com.travbook.payments.application.port.out;
import com.travbook.payments.domain.model.FinancialSnapshot;
import java.time.Instant;
import java.util.UUID;
public interface FinancialReportStore {FinancialSnapshot read(UUID customerId,Instant now);}
