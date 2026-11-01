package com.travbook.pricing.adapter.in.web;
import com.travbook.pricing.application.port.in.Quotations;
import com.travbook.pricing.domain.model.*;
import com.travbook.platform.security.AuthenticatedIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;
@RestController
@RequestMapping("/api/v1/quotes")
public class QuotationController {
    private final Quotations quotes;
    public QuotationController(Quotations quotes) { this.quotes=quotes; }
    public record CreateRequest(@NotNull UUID offerId) {}
    public record AcceptRequest(@Min(1) int version) {}
    public record MoneyView(String amount,String currency) { static MoneyView of(Price p) { return new MoneyView(p.amount().toPlainString(),p.currency()); } }
    public record LegView(String origin,String destination,OffsetDateTime departure,OffsetDateTime arrival,String timezone,int durationMinutes,int stops,String flightNumber) {}
    public record QuoteView(UUID id,int version,UUID customerId,UUID offerId,String supplier,String supplierReference,String mode,String airline,
        List<LegView> legs,int passengers,String cabin,String baggage,String cancellationPolicy,MoneyView base,MoneyView taxes,MoneyView total,MoneyView previousTotal,boolean priceChanged,Instant createdAt,Instant expiresAt) {
        static QuoteView of(Quotation q) {
            var s=q.selection();
            return new QuoteView(q.id(),q.version(),q.customerId(),s.offerId(),s.supplier(),s.supplierReference(),q.mode(),s.airline(),
                s.legs().stream().map(l->new LegView(l.origin(),l.destination(),l.departure(),l.arrival(),l.timezone(),l.durationMinutes(),l.stops(),l.flightNumber())).toList(),
                s.passengers(),s.cabin(),s.baggage(),s.cancellationPolicy(),MoneyView.of(s.base()),MoneyView.of(s.taxes()),MoneyView.of(s.total()),
                MoneyView.of(s.previousTotal()),q.priceChanged(),q.createdAt(),q.expiresAt());
        }
    }
    public record AcceptanceView(UUID id,UUID quoteId,int quoteVersion,Instant acceptedAt,String mode) {
        static AcceptanceView of(QuoteAcceptance a) { return a==null?null:new AcceptanceView(a.id(),a.quoteId(),a.quoteVersion(),a.acceptedAt(),a.mode()); }
    }
    public record DetailsView(QuoteView quote,AcceptanceView acceptance,boolean expired) {}
    private UUID correlation(HttpServletRequest r) { return UUID.fromString((String)r.getAttribute("correlationId")); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public QuoteView create(Authentication auth,@Valid @RequestBody CreateRequest body,@RequestHeader("Idempotency-Key") String key,HttpServletRequest r) {
        return QuoteView.of(quotes.create(AuthenticatedIdentity.from(auth),body.offerId(),key,correlation(r)));
    }
    @GetMapping("/{id}") public DetailsView get(Authentication auth,@PathVariable UUID id) {
        var d=quotes.get(AuthenticatedIdentity.from(auth),id);
        return new DetailsView(QuoteView.of(d.quote()),AcceptanceView.of(d.acceptance()),d.expired());
    }
    @PostMapping("/{id}/acceptance") public AcceptanceView accept(Authentication auth,@PathVariable UUID id,
        @Valid @RequestBody AcceptRequest body,@RequestHeader("Idempotency-Key") String key,HttpServletRequest r) {
        return AcceptanceView.of(quotes.accept(AuthenticatedIdentity.from(auth),id,body.version(),key,correlation(r)));
    }
}

