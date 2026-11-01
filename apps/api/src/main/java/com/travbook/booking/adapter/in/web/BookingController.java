package com.travbook.booking.adapter.in.web;
import com.travbook.booking.application.port.in.BookingIntents;
import com.travbook.booking.application.port.in.BookingCheckouts;
import com.travbook.booking.domain.model.*;
import com.travbook.platform.security.AuthenticatedIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.*;
import java.util.*;
@RestController
public class BookingController {
    private final BookingIntents bookings;private final BookingCheckouts checkouts;
    public BookingController(BookingIntents bookings,BookingCheckouts checkouts){this.bookings=bookings;this.checkouts=checkouts;}
    public record TravellerRequest(@NotBlank @Size(max=80) String givenName,@NotBlank @Size(max=80) String familyName,@NotNull LocalDate dateOfBirth) {
        Traveller model(){return new Traveller(givenName,familyName,dateOfBirth);}
    }
    public record CreateRequest(@NotNull UUID quoteId,@Min(1) int quoteVersion,@NotNull @Size(min=1,max=18) List<@NotNull @Valid TravellerRequest> travellers) {}
    public record TravellerView(String givenName,String familyName,LocalDate dateOfBirth) {}
    public record MoneyView(String amount,String currency) {}
    public record Acknowledgement(UUID id,UUID quoteId,Instant createdAt,String mode,String statusUrl) {
        static Acknowledgement of(Booking b){return new Acknowledgement(b.id(),b.quote().quoteId(),b.createdAt(),"MOCK","/api/v1/bookings/"+b.id());}
    }
    public record Summary(UUID id,UUID quoteId,Instant createdAt,Instant updatedAt,long version,String mode,Booking.Status status,String failureCode,
                          String origin,String destination,OffsetDateTime departure,int passengers,MoneyView total) {
        static Summary of(Booking b){
            var q=b.quote();var leg=q.terms().legs().getFirst();var price=q.terms().total();
            return new Summary(b.id(),q.quoteId(),b.createdAt(),b.updatedAt(),b.version(),"MOCK",b.status(),b.failureCode(),leg.origin(),leg.destination(),leg.departure(),
                q.terms().passengers(),new MoneyView(price.amount().toPlainString(),price.currency()));
        }
    }
    public record WorkflowView(int definitionVersion,String step,String status,int attempts,Instant nextAttemptAt,boolean terminal) {}
    public record Details(Summary booking,UUID offerId,int quoteVersion,Instant quoteExpiresAt,List<TravellerView> travellers,WorkflowView workflow,
                          String reservationStatus,String inventoryStatus,String paymentStatus) {
        static Details of(BookingIntents.Details d){
            var b=d.booking();var w=d.workflow();
            return new Details(Summary.of(b),b.quote().offerId(),b.quote().version(),b.quote().expiresAt(),b.travellers().stream().map(t->new TravellerView(t.givenName(),t.familyName(),t.dateOfBirth())).toList(),
                new WorkflowView(w.definitionVersion(),"VALIDATE_OFFER",w.status(),w.attempts(),w.nextAttemptAt(),w.terminal()),d.reservationStatus(),d.inventoryStatus(),"NOT_STARTED");
        }
    }
    public record Requirements(UUID quoteId,int quoteVersion,int adults,int children,int infants,LocalDate departureDate,LocalDate lastDepartureDate,Instant expiresAt,UUID existingBookingId,String mode) {
        static Requirements of(BookingIntents.Requirements r){
            var q=r.quote();var legs=q.terms().legs();
            return new Requirements(q.quoteId(),q.version(),q.adults(),q.children(),q.infants(),legs.getFirst().departure().toLocalDate(),legs.getLast().departure().toLocalDate(),q.expiresAt(),r.existingBookingId(),"MOCK");
        }
    }
    public record Page(List<Summary> items,UUID nextCursor) {}
    @GetMapping("/api/v1/quotes/{id}/booking-requirements")
    public Requirements requirements(Authentication auth,@PathVariable UUID id,@RequestParam(defaultValue="1") int quoteVersion){
        return Requirements.of(bookings.requirements(AuthenticatedIdentity.from(auth),id,quoteVersion));
    }
    @PostMapping("/api/v1/bookings")
    public ResponseEntity<Acknowledgement> create(Authentication auth,@Valid @RequestBody CreateRequest request,@RequestHeader("Idempotency-Key") String key,HttpServletRequest servlet){
        var booking=bookings.create(AuthenticatedIdentity.from(auth),request.quoteId(),request.quoteVersion(),request.travellers().stream().map(t->t.model()).toList(),key,UUID.fromString((String)servlet.getAttribute("correlationId")));
        return ResponseEntity.accepted().location(URI.create("/api/v1/bookings/"+booking.id())).body(Acknowledgement.of(booking));
    }
    @GetMapping("/api/v1/bookings/{id}")
    public Details get(Authentication auth,@PathVariable UUID id){var identity=AuthenticatedIdentity.from(auth);checkouts.requireLegacyReadable(identity,id);return Details.of(bookings.get(identity,id));}
    @GetMapping("/api/v1/me/bookings")
    public Page list(Authentication auth,@RequestParam(required=false) UUID after,@RequestParam(defaultValue="10") int size){
        var page=bookings.list(AuthenticatedIdentity.from(auth),after,size);
        return new Page(page.items().stream().map(Summary::of).toList(),page.nextCursor());
    }
}

