package com.travbook.payments.application.usecase;
import com.travbook.payments.application.port.in.PaymentReports;
import com.travbook.payments.application.port.out.FinancialReportStore;
import com.travbook.payments.domain.model.FinancialSnapshot;
import java.time.Clock;
import java.util.UUID;
public final class PaymentReportService implements PaymentReports {
    private final FinancialReportStore store;private final Clock clock;
    public PaymentReportService(FinancialReportStore store,Clock clock){this.store=store;this.clock=clock;}
    public FinancialSnapshot forCustomer(UUID customerId){return store.read(customerId,clock.instant());}
}
