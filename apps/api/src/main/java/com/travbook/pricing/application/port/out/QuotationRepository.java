package com.travbook.pricing.application.port.out;
import com.travbook.pricing.domain.model.*;
import java.util.*;
public interface QuotationRepository {
    Optional<Quotation> replayCreation(UUID owner,String key,String fingerprint);
    Quotation create(Quotation quotation,String key,String fingerprint,UUID correlationId);
    Optional<Quotation> findOwned(UUID owner,UUID id);
    Optional<QuoteAcceptance> acceptance(UUID owner,UUID quoteId);
    QuoteAcceptance accept(UUID owner,UUID quoteId,int version,String key,String fingerprint,UUID correlationId);
}

