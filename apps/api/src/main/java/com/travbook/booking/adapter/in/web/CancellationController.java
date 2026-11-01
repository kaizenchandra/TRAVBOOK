package com.travbook.booking.adapter.in.web;
import com.travbook.booking.application.port.in.BookingCancellations;
import com.travbook.booking.domain.model.*;
import com.travbook.platform.security.AuthenticatedIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.Instant;
import java.util.*;
@RestController
public class CancellationController {
    private final BookingCancellations cancellations;
    public CancellationController(BookingCancellations cancellations){this.cancellations=cancellations;}
    public enum Scope { FULL_BOOKING }
    public record QuoteRequest(@NotNull Scope scope) {}
    public record CancelRequest(@NotNull UUID quoteId,@Min(1) @Max(1) int quoteVersion,@AssertTrue boolean confirmed,@NotNull Cancellation.Scenario scenario) {}
    public record QuoteView(UUID id,UUID bookingId,int version,String mode,String policy,Instant createdAt,Instant expiresAt,BookingController.MoneyView total,BookingController.MoneyView refundAmount,BookingController.MoneyView retainedAmount){
        static QuoteView of(CancellationQuote q){return new QuoteView(q.id(),q.bookingId(),1,"MOCK",q.checkout().quote().terms().cancellationPolicy(),q.createdAt(),q.expiresAt(),money(q.checkout().quote().terms().total()),money(q.refundAmount()),money(q.retainedAmount()));}
    }
    public record View(UUID id,UUID bookingId,UUID quoteId,String mode,long version,Instant createdAt,Instant updatedAt,Cancellation.State cancellationStatus,Cancellation.RefundState refundStatus,Cancellation.Step step,
                       BookingController.MoneyView refundAmount,BookingController.MoneyView retainedAmount,String supplierReference,String refundReference,String failureCode,int attempts,boolean terminal){
        static View of(BookingCancellations.View v){if(v==null)return null;var c=v.cancellation();return new View(c.id(),c.quote().bookingId(),c.quote().id(),"MOCK",c.version(),c.createdAt(),c.updatedAt(),c.cancellationStatus(),c.refundStatus(),c.step(),money(c.quote().refundAmount()),money(c.quote().retainedAmount()),c.supplierReference(),c.refundReference(),c.failureCode(),v.attempts(),c.terminal());}
    }
    private static BookingController.MoneyView money(FlightTerms.Money m){return new BookingController.MoneyView(m.amount().toPlainString(),m.currency());}
    public record Ack(UUID id,UUID bookingId,Instant createdAt,String mode,String statusUrl) {}
    public record RefundList(List<View> items) {}
    @PostMapping("/api/v1/bookings/{id}/cancellation-quotes")
    public ResponseEntity<QuoteView> quote(Authentication auth,@PathVariable UUID id,@Valid @RequestBody QuoteRequest request,@RequestHeader("Idempotency-Key") String key,HttpServletRequest servlet){
        var q=cancellations.quote(AuthenticatedIdentity.from(auth),id,key,UUID.fromString((String)servlet.getAttribute("correlationId")));return ResponseEntity.status(201).body(QuoteView.of(q));
    }
    @PostMapping("/api/v1/bookings/{id}/cancellations")
    public ResponseEntity<Ack> cancel(Authentication auth,@PathVariable UUID id,@Valid @RequestBody CancelRequest request,@RequestHeader("Idempotency-Key") String key,HttpServletRequest servlet){
        var c=cancellations.request(AuthenticatedIdentity.from(auth),id,request.quoteId(),request.quoteVersion(),request.confirmed(),request.scenario(),key,UUID.fromString((String)servlet.getAttribute("correlationId")));String url="/api/v3/bookings/"+id;
        return ResponseEntity.accepted().location(URI.create(url)).body(new Ack(c.id(),id,c.createdAt(),"MOCK",url));
    }
    @GetMapping("/api/v1/bookings/{id}/refunds")
    public RefundList refunds(Authentication auth,@PathVariable UUID id){return new RefundList(cancellations.refunds(AuthenticatedIdentity.from(auth),id).stream().map(View::of).toList());}
}
