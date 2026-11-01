package com.travbook.payments.application.port.in;
import com.travbook.payments.domain.model.FinancialSnapshot;
import java.util.UUID;
/** Trusted published read contract. Callers must derive the customer from verified identity. */
public interface PaymentReports {FinancialSnapshot forCustomer(UUID customerId);}
