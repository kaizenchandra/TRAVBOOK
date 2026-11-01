package com.travbook.stays.adapter.in.web;
import com.travbook.stays.application.port.in.HotelStays;
import com.travbook.stays.domain.model.Stay.*;
import com.travbook.platform.security.AuthenticatedIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.*;
@RestController
public class StayController {
 private final HotelStays stays;private final java.time.Clock clock;public StayController(HotelStays stays,java.time.Clock clock){this.stays=stays;this.clock=clock;}
 public record QuoteRequest(@NotNull @Size(min=1,max=4) List<@NotNull UUID> offerIds){}
 public record AcceptRequest(@Min(1) int version,@AssertTrue boolean consent){}
 public record GuestRequest(@Min(0) @Max(3) int roomIndex,@NotBlank @Size(max=60) String givenName,@NotBlank @Size(max=60) String familyName,@NotNull LocalDate dateOfBirth){Guest domain(){return new Guest(roomIndex,givenName,familyName,dateOfBirth);}}
 public record BookRequest(@NotNull UUID quoteId,@Min(1) int version,@NotNull @Size(min=1,max=20) List<@NotNull @Valid GuestRequest> guests,@NotNull SpecialRequest specialRequest,@NotNull Scenario scenario,@AssertTrue boolean consent){}
 public record CancelRequest(@NotNull UUID cancellationQuoteId,@AssertTrue boolean consent,boolean simulateDelay,boolean simulateResponseLoss){}
 private void legacyQuote(Authentication a,UUID id){if(stays.getQuote(AuthenticatedIdentity.from(a),id).quote().collectionMethod()!=CollectionMethod.PAY_NOW)throw new com.travbook.shared.domain.DomainFailure(com.travbook.shared.domain.DomainFailure.Code.BOOKING_CONFLICT,"Use the current hotel API for deferred terms.");}
 private void legacyBooking(Authentication a,UUID id){if(stays.get(AuthenticatedIdentity.from(a),id).modern())throw new com.travbook.shared.domain.DomainFailure(com.travbook.shared.domain.DomainFailure.Code.BOOKING_CONFLICT,"Use the current hotel API for deferred or amended stays.");}
 private UUID correlation(HttpServletRequest r){return UUID.fromString((String)r.getAttribute("correlationId"));}
 @PostMapping("/api/v1/stay-quotes") @ResponseStatus(HttpStatus.CREATED) public StayViews.QuoteView quote(Authentication a,@Valid @RequestBody QuoteRequest r,@RequestHeader("Idempotency-Key") String key,HttpServletRequest h){return StayViews.QuoteView.of(stays.quote(AuthenticatedIdentity.from(a),r.offerIds(),key,correlation(h)));}
 @GetMapping("/api/v1/stay-quotes/{id}") public StayViews.QuoteView quote(Authentication a,@PathVariable UUID id){legacyQuote(a,id);return StayViews.QuoteView.of(stays.getQuote(AuthenticatedIdentity.from(a),id));}
 @PostMapping("/api/v1/stay-quotes/{id}/acceptance") public StayViews.QuoteView accept(Authentication a,@PathVariable UUID id,@Valid @RequestBody AcceptRequest r,@RequestHeader("Idempotency-Key") String key,HttpServletRequest h){legacyQuote(a,id);return StayViews.QuoteView.of(stays.accept(AuthenticatedIdentity.from(a),id,r.version(),r.consent(),key,correlation(h)));}
 @PostMapping("/api/v1/stay-bookings") @ResponseStatus(HttpStatus.ACCEPTED) public StayViews.BookingView book(Authentication a,@Valid @RequestBody BookRequest r,@RequestHeader("Idempotency-Key") String key,HttpServletRequest h){legacyQuote(a,r.quoteId());return StayViews.BookingView.of(stays.book(AuthenticatedIdentity.from(a),r.quoteId(),r.version(),r.guests().stream().map(g->g.domain()).toList(),r.specialRequest(),r.scenario(),r.consent(),key,correlation(h)),clock.instant());}
 @GetMapping("/api/v1/stay-bookings/{id}") public StayViews.BookingView booking(Authentication a,@PathVariable UUID id){legacyBooking(a,id);return StayViews.BookingView.of(stays.get(AuthenticatedIdentity.from(a),id),clock.instant());}
 @GetMapping("/api/v1/me/stay-bookings") public StayViews.ListView list(Authentication a,@RequestParam(required=false) UUID after){var page=stays.list(AuthenticatedIdentity.from(a),after);return new StayViews.ListView(page.items().stream().map(b->StayViews.Summary.of(b)).toList(),page.nextCursor());}
 @PostMapping("/api/v1/stay-bookings/{id}/cancellation-quotes") @ResponseStatus(HttpStatus.CREATED) public StayViews.CancellationView cancellationQuote(Authentication a,@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,HttpServletRequest h){legacyBooking(a,id);return StayViews.CancellationView.of(stays.cancellationQuote(AuthenticatedIdentity.from(a),id,key,correlation(h)));}
 @PostMapping("/api/v1/stay-bookings/{id}/cancellations") @ResponseStatus(HttpStatus.ACCEPTED) public StayViews.BookingView cancel(Authentication a,@PathVariable UUID id,@Valid @RequestBody CancelRequest r,@RequestHeader("Idempotency-Key") String key,HttpServletRequest h){legacyBooking(a,id);return StayViews.BookingView.of(stays.cancel(AuthenticatedIdentity.from(a),id,r.cancellationQuoteId(),r.consent(),r.simulateDelay(),r.simulateResponseLoss(),key,correlation(h)),clock.instant());}
 @PostMapping("/api/v1/stay-bookings/{id}/voucher") @ResponseStatus(HttpStatus.CREATED) public StayViews.VoucherView voucher(Authentication a,@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,HttpServletRequest h){legacyBooking(a,id);return StayViews.VoucherView.of(stays.voucher(AuthenticatedIdentity.from(a),id,key,correlation(h)));}
 @GetMapping("/api/v1/stay-bookings/{id}/voucher") public StayViews.VoucherView voucher(Authentication a,@PathVariable UUID id){legacyBooking(a,id);return StayViews.VoucherView.of(stays.readVoucher(AuthenticatedIdentity.from(a),id));}
 @GetMapping("/api/v1/stay-bookings/{id}/voucher/content") public ResponseEntity<byte[]> download(Authentication a,@PathVariable UUID id){legacyBooking(a,id);var v=stays.readVoucher(AuthenticatedIdentity.from(a),id);return ResponseEntity.ok().contentType(new MediaType("text","plain",StandardCharsets.UTF_8)).header("Content-Disposition","attachment; filename=\"travbook-MOCK-accommodation-"+v.id()+".txt\"").header("Cache-Control","no-store").header("X-Content-Type-Options","nosniff").body(v.content().getBytes(StandardCharsets.UTF_8));}
 @GetMapping("/api/v1/stay-bookings/{id}/timeline") public List<StayViews.EventView> timeline(Authentication a,@PathVariable UUID id){return stays.timeline(AuthenticatedIdentity.from(a),id).stream().map(e->StayViews.EventView.of(e)).toList();}
}
