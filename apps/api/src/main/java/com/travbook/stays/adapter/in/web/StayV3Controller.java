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
import com.travbook.hotels.domain.model.HotelOffer.Plan;
import java.nio.charset.StandardCharsets;
import java.util.*;
@RestController
public class StayV3Controller {
 private final HotelStays stays;private final java.time.Clock clock;public StayV3Controller(HotelStays stays,java.time.Clock clock){this.stays=stays;this.clock=clock;}
 public record QuoteRequest(@NotNull @Size(min=1,max=4) List<@NotNull UUID> offerIds,@NotNull CollectionMethod collectionMethod){}
 public record AcceptRequest(@Min(1) int version,@AssertTrue boolean consent){}
 public record GuestRequest(@Min(0) @Max(3) int roomIndex,@NotBlank @Size(max=60) String givenName,@NotBlank @Size(max=60) String familyName,@NotNull LocalDate dateOfBirth){Guest domain(){return new Guest(roomIndex,givenName,familyName,dateOfBirth);}}
 public record BookRequest(@NotNull UUID quoteId,@Min(1) int version,@NotNull @Size(min=1,max=20) List<@NotNull @Valid GuestRequest> guests,@NotNull SpecialRequest specialRequest,@NotNull Scenario scenario,@AssertTrue boolean consent){}
 public record CancelRequest(@NotNull UUID cancellationQuoteId,@AssertTrue boolean consent,boolean simulateDelay,boolean simulateResponseLoss){}
 public record AmendRequest(@NotNull UUID quoteId,@Min(1) long bookingVersion,@AssertTrue boolean consent,boolean simulateResponseLoss){}
 public record Occupancy(@Min(1) @Max(3) int adults,@NotNull @Size(max=2) List<@NotNull @Min(0) @Max(17) Integer> childAges){com.travbook.hotels.domain.model.HotelCriteria.Occupancy domain(){return new com.travbook.hotels.domain.model.HotelCriteria.Occupancy(adults,childAges);}}
 public record Criteria(@NotBlank @Size(min=2,max=80) String destination,@NotNull LocalDate checkIn,@NotNull LocalDate checkOut,@NotNull @Size(min=1,max=4) List<@NotNull @Valid Occupancy> rooms){com.travbook.hotels.domain.model.HotelCriteria domain(){return new com.travbook.hotels.domain.model.HotelCriteria(destination,checkIn,checkOut,rooms.stream().map(o->o.domain()).toList());}}
 public record ChangeQuoteRequest(@Min(1) long bookingVersion,@NotNull @Valid Criteria criteria,@NotNull @Size(min=1,max=4) List<@NotNull Plan> plans){}
 public record ChangeRequest(@NotNull UUID quoteId,@Min(1) long bookingVersion,@NotNull @Size(min=1,max=20) List<@NotNull @Valid GuestRequest> guests,@NotNull Scenario scenario,@AssertTrue boolean consent,boolean simulateRefundDelay,boolean simulateResponseLoss){}
 @PostMapping("/api/v3/stay-bookings/{id}/change-quotes") @ResponseStatus(HttpStatus.CREATED) public StayViews.ChangeQuoteView quoteChange(Authentication a,@PathVariable UUID id,@Valid @RequestBody ChangeQuoteRequest r,@RequestHeader("Idempotency-Key") String key,HttpServletRequest h){return StayViews.ChangeQuoteView.of(stays.changeQuote(AuthenticatedIdentity.from(a),id,r.bookingVersion(),r.criteria().domain(),r.plans(),key,correlation(h)));}
 @PostMapping("/api/v3/stay-bookings/{id}/changes") @ResponseStatus(HttpStatus.ACCEPTED) public StayViews.BookingV3View change(Authentication a,@PathVariable UUID id,@Valid @RequestBody ChangeRequest r,@RequestHeader("Idempotency-Key") String key,HttpServletRequest h){return StayViews.BookingV3View.of(stays.change(AuthenticatedIdentity.from(a),id,r.quoteId(),r.bookingVersion(),r.guests().stream().map(g->g.domain()).toList(),r.scenario(),r.consent(),r.simulateRefundDelay(),r.simulateResponseLoss(),key,correlation(h)),clock.instant());}
 private UUID correlation(HttpServletRequest r){return UUID.fromString((String)r.getAttribute("correlationId"));}
 @GetMapping("/api/v3/stay-bookings/{id}") public StayViews.BookingV3View booking(Authentication a,@PathVariable UUID id){return StayViews.BookingV3View.of(stays.get(AuthenticatedIdentity.from(a),id),clock.instant());}
 @GetMapping("/api/v3/me/stay-bookings") public StayViews.ListView list(Authentication a,@RequestParam(required=false) UUID after){var page=stays.list(AuthenticatedIdentity.from(a),after);return new StayViews.ListView(page.items().stream().map(b->StayViews.Summary.of(b)).toList(),page.nextCursor());}
 @PostMapping("/api/v3/stay-bookings/{id}/cancellation-quotes") @ResponseStatus(HttpStatus.CREATED) public StayViews.CancellationView cancellationQuote(Authentication a,@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,HttpServletRequest h){return StayViews.CancellationView.of(stays.cancellationQuote(AuthenticatedIdentity.from(a),id,key,correlation(h)));}
 @PostMapping("/api/v3/stay-bookings/{id}/cancellations") @ResponseStatus(HttpStatus.ACCEPTED) public StayViews.BookingV3View cancel(Authentication a,@PathVariable UUID id,@Valid @RequestBody CancelRequest r,@RequestHeader("Idempotency-Key") String key,HttpServletRequest h){return StayViews.BookingV3View.of(stays.cancel(AuthenticatedIdentity.from(a),id,r.cancellationQuoteId(),r.consent(),r.simulateDelay(),r.simulateResponseLoss(),key,correlation(h)),clock.instant());}
 @PostMapping("/api/v3/stay-bookings/{id}/voucher") @ResponseStatus(HttpStatus.CREATED) public StayViews.VoucherV3View voucher(Authentication a,@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,HttpServletRequest h){return StayViews.VoucherV3View.of(stays.voucher(AuthenticatedIdentity.from(a),id,key,correlation(h)));}
 @GetMapping("/api/v3/stay-bookings/{id}/voucher") public StayViews.VoucherV3View voucher(Authentication a,@PathVariable UUID id){return StayViews.VoucherV3View.of(stays.readVoucher(AuthenticatedIdentity.from(a),id));}
 @GetMapping("/api/v3/stay-bookings/{id}/voucher/content") public ResponseEntity<byte[]> download(Authentication a,@PathVariable UUID id){var v=stays.readVoucher(AuthenticatedIdentity.from(a),id);return ResponseEntity.ok().contentType(new MediaType("text","plain",StandardCharsets.UTF_8)).header("Content-Disposition","attachment; filename=\"travbook-MOCK-accommodation-"+v.id()+".txt\"").header("Cache-Control","no-store").header("X-Content-Type-Options","nosniff").body(v.content().getBytes(StandardCharsets.UTF_8));}
 @GetMapping("/api/v3/stay-bookings/{id}/timeline") public List<StayViews.EventView> timeline(Authentication a,@PathVariable UUID id){return stays.timeline(AuthenticatedIdentity.from(a),id).stream().map(e->StayViews.EventView.of(e)).toList();}
}
