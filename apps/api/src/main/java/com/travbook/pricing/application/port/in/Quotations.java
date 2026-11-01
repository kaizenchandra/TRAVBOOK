package com.travbook.pricing.application.port.in;
import com.travbook.shared.domain.CustomerIdentity;
import com.travbook.pricing.domain.model.*;
import java.util.UUID;
public interface Quotations {
    record Details(Quotation quote,QuoteAcceptance acceptance,boolean expired) {}
    Quotation create(CustomerIdentity identity,UUID offerId,String key,UUID correlationId);
    Details get(CustomerIdentity identity,UUID id);
    Details forBooking(CustomerIdentity identity,UUID id,int version);
    QuoteAcceptance accept(CustomerIdentity identity,UUID id,int version,String key,UUID correlationId);
}

