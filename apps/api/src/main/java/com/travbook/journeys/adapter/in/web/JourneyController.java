package com.travbook.journeys.adapter.in.web;
import com.travbook.journeys.application.port.in.Journeys;
import com.travbook.journeys.domain.model.Journey.*;
import com.travbook.platform.security.AuthenticatedIdentity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
@RestController public class JourneyController {
 private final Journeys journeys;private final Clock clock;
 public JourneyController(Journeys j,Clock c){journeys=j;clock=c;}
 public record QuoteView(UUID id,int version,Selection selection,Instant createdAt,Instant expiresAt,Instant acceptedAt,UUID bookingId,boolean expired,String mode){static QuoteView of(Journeys.QuoteDetails d){var q=d.quote();return new QuoteView(q.id(),q.version(),q.selection(),q.createdAt(),q.expiresAt(),d.acceptedAt(),d.bookingId(),d.expired(),q.mode());}}
 public record BookingView(UUID id,long version,QuoteView quote,List<Passenger> passengers,String email,Status status,Payment payment,Reservation reservation,Refund refund,Step step,String supplierReference,String refundReference,String failure,Instant createdAt,Instant updatedAt,String mode){static BookingView of(Booking b,Instant now){return new BookingView(b.id(),b.version(),QuoteView.of(new Journeys.QuoteDetails(b.quote(),b.acceptedAt(),b.id(),!now.isBefore(b.quote().expiresAt()))),b.passengers(),b.email(),b.status(),b.payment(),b.reservation(),b.refund(),b.step(),b.supplierReference(),b.refundReference(),b.failure(),b.createdAt(),b.updatedAt(),b.mode());}}
 public record Accept(@Min(1) @Max(1) int version,@AssertTrue boolean consent){}
 public record Book(@NotNull UUID quoteId,@NotNull @Size(min=1,max=10) List<@NotNull @Valid Passenger> passengers,@NotBlank @Email @Size(max=160) String email,@NotNull Scenario scenario){}
 public record Cancel(@NotNull UUID quoteId,@AssertTrue boolean consent,boolean simulateDelay){}
 public record Empty(){}
 public record Page(List<BookingView> items,UUID next){}
 private UUID correlation(HttpServletRequest r){return UUID.fromString((String)r.getAttribute("correlationId"));}
 private BookingView view(Booking b){return BookingView.of(b,clock.instant());}
 @PostMapping("/api/v1/journey-quotes") @ResponseStatus(HttpStatus.CREATED) public QuoteView quote(Authentication a,@RequestBody Request r,@RequestHeader("Idempotency-Key") String k,HttpServletRequest h){return QuoteView.of(journeys.quote(AuthenticatedIdentity.from(a),r,k,correlation(h)));}
 @GetMapping("/api/v1/journey-quotes/{id}") public QuoteView quote(Authentication a,@PathVariable UUID id){return QuoteView.of(journeys.quote(AuthenticatedIdentity.from(a),id));}
 @PostMapping("/api/v1/journey-quotes/{id}/acceptance") public QuoteView accept(Authentication a,@PathVariable UUID id,@Valid @RequestBody Accept r,@RequestHeader("Idempotency-Key") String k,HttpServletRequest h){return QuoteView.of(journeys.accept(AuthenticatedIdentity.from(a),id,r.version(),r.consent(),k,correlation(h)));}
 @PostMapping("/api/v1/journey-bookings") @ResponseStatus(HttpStatus.ACCEPTED) public BookingView book(Authentication a,@Valid @RequestBody Book r,@RequestHeader("Idempotency-Key") String k,HttpServletRequest h){return view(journeys.book(AuthenticatedIdentity.from(a),r.quoteId(),r.passengers(),r.email(),r.scenario(),k,correlation(h)));}
 @GetMapping("/api/v1/journey-bookings/{id}") public BookingView booking(Authentication a,@PathVariable UUID id){return view(journeys.booking(AuthenticatedIdentity.from(a),id));}
 @GetMapping("/api/v1/me/journey-bookings") public Page list(Authentication a,@RequestParam(required=false) UUID after){var p=journeys.list(AuthenticatedIdentity.from(a),after);return new Page(p.items().stream().map(this::view).toList(),p.next());}
 @PostMapping("/api/v1/journey-bookings/{id}/payment") @ResponseStatus(HttpStatus.ACCEPTED) public BookingView pay(Authentication a,@PathVariable UUID id,@RequestBody Empty r,@RequestHeader("Idempotency-Key") String k,HttpServletRequest h){return view(journeys.pay(AuthenticatedIdentity.from(a),id,k,correlation(h)));}
 @PostMapping("/api/v1/journey-bookings/{id}/cancellation-quotes") @ResponseStatus(HttpStatus.CREATED) public CancellationQuote cancelQuote(Authentication a,@PathVariable UUID id,@RequestBody Empty r,@RequestHeader("Idempotency-Key") String k,HttpServletRequest h){return journeys.cancellationQuote(AuthenticatedIdentity.from(a),id,k,correlation(h));}
 @PostMapping("/api/v1/journey-bookings/{id}/cancellations") @ResponseStatus(HttpStatus.ACCEPTED) public BookingView cancel(Authentication a,@PathVariable UUID id,@Valid @RequestBody Cancel r,@RequestHeader("Idempotency-Key") String k,HttpServletRequest h){return view(journeys.cancel(AuthenticatedIdentity.from(a),id,r.quoteId(),r.consent(),r.simulateDelay(),k,correlation(h)));}
 @GetMapping("/api/v1/journey-bookings/{id}/document") public Document document(Authentication a,@PathVariable UUID id){return journeys.document(AuthenticatedIdentity.from(a),id);}
}
