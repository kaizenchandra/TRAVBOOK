package com.travbook.booking.adapter.in.web;
import com.travbook.booking.application.port.in.*;
import com.travbook.booking.domain.model.*;
import com.travbook.platform.security.AuthenticatedIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.*;
import java.util.*;
@RestController
public class CheckoutController {
    private final BookingIntents bookings;private final BookingCheckouts checkouts;private final BookingCancellations cancellations;
    public CheckoutController(BookingIntents bookings,BookingCheckouts checkouts,BookingCancellations cancellations){this.bookings=bookings;this.checkouts=checkouts;this.cancellations=cancellations;}
    public record StartRequest(@NotNull Checkout.Scenario scenario,@AssertTrue boolean consent) {}
    public record Ack(UUID id,UUID bookingId,Instant createdAt,String mode,String statusUrl) {}
    public record CheckoutView(UUID id,UUID bookingId,String mode,String strategy,Checkout.Scenario scenario,Instant createdAt,Instant updatedAt,long version,
                               Checkout.Status status,Checkout.Step step,Checkout.Payment paymentStatus,Checkout.Reservation reservationStatus,String paymentReference,String supplierReference,String failureCode,int attempts,boolean terminal,BookingController.MoneyView total){
        static CheckoutView of(BookingCheckouts.View view){if(view==null)return null;var c=view.checkout();var total=c.quote().terms().total();return new CheckoutView(c.id(),c.bookingId(),"MOCK","AUTHORIZE_RESERVE_CAPTURE",c.scenario(),c.createdAt(),c.updatedAt(),c.version(),c.status(),c.step(),c.payment(),c.reservation(),c.paymentReference(),c.supplierReference(),c.failureCode(),view.attempts(),c.terminal(),new BookingController.MoneyView(total.amount().toPlainString(),total.currency()));}
    }
    public record DetailsV3(BookingController.Summary booking,UUID offerId,int quoteVersion,Instant quoteExpiresAt,List<BookingController.TravellerView> travellers,BookingController.WorkflowView workflow,String inventoryStatus,CheckoutView checkout,CancellationController.View cancellation) {}
    public record SummaryV3(UUID id,UUID quoteId,Instant createdAt,Instant updatedAt,long version,String mode,Booking.Status status,String failureCode,String origin,String destination,OffsetDateTime departure,int passengers,BookingController.MoneyView total,CheckoutView checkout,CancellationController.View cancellation) {
        static SummaryV3 of(Booking b,CheckoutView c,CancellationController.View cancellation){var s=BookingController.Summary.of(b);return new SummaryV3(s.id(),s.quoteId(),s.createdAt(),s.updatedAt(),s.version(),s.mode(),s.status(),s.failureCode(),s.origin(),s.destination(),s.departure(),s.passengers(),s.total(),c,cancellation);}
    }
    public record PageV3(List<SummaryV3> items,UUID nextCursor) {}
    @PostMapping("/api/v2/bookings/{id}/checkout")
    public ResponseEntity<Ack> start(Authentication auth,@PathVariable UUID id,@Valid @RequestBody StartRequest request,@RequestHeader("Idempotency-Key") String key,HttpServletRequest servlet){
        var c=checkouts.start(AuthenticatedIdentity.from(auth),id,request.scenario(),request.consent(),key,UUID.fromString((String)servlet.getAttribute("correlationId")));
        String url="/api/v2/bookings/"+id;return ResponseEntity.accepted().location(URI.create(url)).body(new Ack(c.id(),id,c.createdAt(),"MOCK",url));
    }
    @GetMapping("/api/v3/bookings/{id}")
    public DetailsV3 get(Authentication auth,@PathVariable UUID id){
        var identity=AuthenticatedIdentity.from(auth);var d=BookingController.Details.of(bookings.get(identity,id));
        return new DetailsV3(d.booking(),d.offerId(),d.quoteVersion(),d.quoteExpiresAt(),d.travellers(),d.workflow(),d.inventoryStatus(),CheckoutView.of(checkouts.get(identity,id)),CancellationController.View.of(cancellations.get(identity,id)));
    }
    @GetMapping("/api/v3/me/bookings")
    public PageV3 list(Authentication auth,@RequestParam(required=false) UUID after,@RequestParam(defaultValue="10") int size){
        var identity=AuthenticatedIdentity.from(auth);var page=bookings.list(identity,after,size);
        return new PageV3(page.items().stream().map(b->SummaryV3.of(b,CheckoutView.of(checkouts.get(identity,b.id())),CancellationController.View.of(cancellations.get(identity,b.id())))).toList(),page.nextCursor());
    }

    public record Details(BookingController.Summary booking,UUID offerId,int quoteVersion,Instant quoteExpiresAt,List<BookingController.TravellerView> travellers,BookingController.WorkflowView workflow,String inventoryStatus,CheckoutView checkout) {}
    public record Summary(UUID id,UUID quoteId,Instant createdAt,Instant updatedAt,long version,String mode,Booking.Status status,String failureCode,String origin,String destination,OffsetDateTime departure,int passengers,BookingController.MoneyView total,CheckoutView checkout) {
        static Summary of(SummaryV3 s){return new Summary(s.id(),s.quoteId(),s.createdAt(),s.updatedAt(),s.version(),s.mode(),s.status(),s.failureCode(),s.origin(),s.destination(),s.departure(),s.passengers(),s.total(),s.checkout());}
    }
    public record Page(List<Summary> items,UUID nextCursor) {}
    @GetMapping("/api/v2/bookings/{id}")
    public Details legacyGet(Authentication auth,@PathVariable UUID id){
        var d=get(auth,id);cancellations.requireLegacyReadable(AuthenticatedIdentity.from(auth),id);
        return new Details(d.booking(),d.offerId(),d.quoteVersion(),d.quoteExpiresAt(),d.travellers(),d.workflow(),d.inventoryStatus(),d.checkout());
    }
    @GetMapping("/api/v2/me/bookings")
    public Page legacyList(Authentication auth,@RequestParam(required=false) UUID after,@RequestParam(defaultValue="10") int size){
        var p=list(auth,after,size);for(var b:p.items())cancellations.requireLegacyReadable(AuthenticatedIdentity.from(auth),b.id());
        return new Page(p.items().stream().map(Summary::of).toList(),p.nextCursor());
    }
}
