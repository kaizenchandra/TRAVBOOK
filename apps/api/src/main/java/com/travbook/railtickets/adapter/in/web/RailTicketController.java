package com.travbook.railtickets.adapter.in.web;
import com.travbook.railtickets.application.port.in.RailTickets;
import com.travbook.railtickets.domain.model.RailTicket.*;
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
public class RailTicketController {
 private final RailTickets railTickets;private final java.time.Clock clock;public RailTicketController(RailTickets railTickets,java.time.Clock clock){this.railTickets=railTickets;this.clock=clock;}
 public record QuoteRequest(@NotNull UUID journeyId,@NotNull com.travbook.rail.domain.model.Rail.TravelClass travelClass,@NotNull com.travbook.rail.domain.model.Rail.Quota quota,@NotBlank @Pattern(regexp="[A-Z]{2,5}") String boardingStation,@NotNull @Size(min=1,max=6) List<@NotNull @Valid PassengerRequest> passengers){}
 public record AcceptRequest(@Min(1) int version,@AssertTrue boolean consent){}
 public record PassengerRequest(@NotBlank @Size(max=60) String givenName,@NotBlank @Size(max=60) String familyName,@NotNull LocalDate dateOfBirth,@NotNull Berth berthPreference){Passenger domain(){return new Passenger(givenName,familyName,dateOfBirth,berthPreference);}}
 public record BookRequest(@NotNull UUID quoteId,@Min(1) int version,@NotNull Scenario scenario,@AssertTrue boolean consent){}
 public record CancelRequest(@NotNull UUID cancellationQuoteId,@AssertTrue boolean consent,boolean simulateDelay,boolean simulateResponseLoss){}
 private RailTicketViews.BookingView legacy(Booking b,java.time.Instant now){b.legacyAllowed();return RailTicketViews.BookingView.of(b,now);}
 private UUID correlation(HttpServletRequest r){return UUID.fromString((String)r.getAttribute("correlationId"));}
 @PostMapping("/api/v1/rail-ticket-quotes") @ResponseStatus(HttpStatus.CREATED) public RailTicketViews.QuoteView quote(Authentication a,@Valid @RequestBody QuoteRequest r,@RequestHeader("Idempotency-Key") String key,HttpServletRequest h){return RailTicketViews.QuoteView.of(railTickets.quote(AuthenticatedIdentity.from(a),r.journeyId(),r.travelClass(),r.quota(),r.boardingStation(),r.passengers().stream().map(passenger->passenger.domain()).toList(),key,correlation(h)));}
 @GetMapping("/api/v1/rail-ticket-quotes/{id}") public RailTicketViews.QuoteView quote(Authentication a,@PathVariable UUID id){return RailTicketViews.QuoteView.of(railTickets.getQuote(AuthenticatedIdentity.from(a),id));}
 @PostMapping("/api/v1/rail-ticket-quotes/{id}/acceptance") public RailTicketViews.QuoteView accept(Authentication a,@PathVariable UUID id,@Valid @RequestBody AcceptRequest r,@RequestHeader("Idempotency-Key") String key,HttpServletRequest h){return RailTicketViews.QuoteView.of(railTickets.accept(AuthenticatedIdentity.from(a),id,r.version(),r.consent(),key,correlation(h)));}
 @PostMapping("/api/v1/rail-ticket-bookings") @ResponseStatus(HttpStatus.ACCEPTED) public RailTicketViews.BookingView book(Authentication a,@Valid @RequestBody BookRequest r,@RequestHeader("Idempotency-Key") String key,HttpServletRequest h){return legacy(railTickets.book(AuthenticatedIdentity.from(a),r.quoteId(),r.version(),r.scenario(),r.consent(),key,correlation(h)),clock.instant());}
 @GetMapping("/api/v1/rail-ticket-bookings/{id}") public RailTicketViews.BookingView booking(Authentication a,@PathVariable UUID id){return legacy(railTickets.get(AuthenticatedIdentity.from(a),id),clock.instant());}
 @GetMapping("/api/v1/rail-ticket-pnr/{pnr}") public RailTicketViews.BookingView pnr(Authentication a,@PathVariable String pnr){return legacy(railTickets.pnr(AuthenticatedIdentity.from(a),pnr),clock.instant());}
 @GetMapping("/api/v1/me/rail-ticket-bookings") public RailTicketViews.ListView list(Authentication a,@RequestParam(required=false) UUID after){var page=railTickets.list(AuthenticatedIdentity.from(a),after);return new RailTicketViews.ListView(page.items().stream().map(b->RailTicketViews.Summary.of(b)).toList(),page.nextCursor());}
 @PostMapping("/api/v1/rail-ticket-bookings/{id}/cancellation-quotes") @ResponseStatus(HttpStatus.CREATED) public RailTicketViews.CancellationView cancellationQuote(Authentication a,@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,HttpServletRequest h){return RailTicketViews.CancellationView.of(railTickets.cancellationQuote(AuthenticatedIdentity.from(a),id,key,correlation(h)));}
 @PostMapping("/api/v1/rail-ticket-bookings/{id}/cancellations") @ResponseStatus(HttpStatus.ACCEPTED) public RailTicketViews.BookingView cancel(Authentication a,@PathVariable UUID id,@Valid @RequestBody CancelRequest r,@RequestHeader("Idempotency-Key") String key,HttpServletRequest h){return legacy(railTickets.cancel(AuthenticatedIdentity.from(a),id,r.cancellationQuoteId(),r.consent(),r.simulateDelay(),r.simulateResponseLoss(),key,correlation(h)),clock.instant());}
 @PostMapping("/api/v1/rail-ticket-bookings/{id}/voucher") @ResponseStatus(HttpStatus.CREATED) public RailTicketViews.VoucherView voucher(Authentication a,@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,HttpServletRequest h){return RailTicketViews.VoucherView.of(railTickets.voucher(AuthenticatedIdentity.from(a),id,key,correlation(h)));}
 @GetMapping("/api/v1/rail-ticket-bookings/{id}/voucher") public RailTicketViews.VoucherView voucher(Authentication a,@PathVariable UUID id){return RailTicketViews.VoucherView.of(railTickets.readVoucher(AuthenticatedIdentity.from(a),id));}
 @GetMapping("/api/v1/rail-ticket-bookings/{id}/voucher/content") public ResponseEntity<byte[]> download(Authentication a,@PathVariable UUID id){var v=railTickets.readVoucher(AuthenticatedIdentity.from(a),id);return ResponseEntity.ok().contentType(new MediaType("text","plain",StandardCharsets.UTF_8)).header("Content-Disposition","attachment; filename=\"travbook-MOCK-rail-"+v.id()+".txt\"").header("Cache-Control","no-store").header("X-Content-Type-Options","nosniff").body(v.content().getBytes(StandardCharsets.UTF_8));}
 @GetMapping("/api/v1/rail-ticket-bookings/{id}/timeline") public List<RailTicketViews.EventView> timeline(Authentication a,@PathVariable UUID id){return railTickets.timeline(AuthenticatedIdentity.from(a),id).stream().map(e->RailTicketViews.EventView.of(e)).toList();}
}
