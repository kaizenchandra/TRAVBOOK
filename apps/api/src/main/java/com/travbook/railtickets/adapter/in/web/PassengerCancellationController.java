package com.travbook.railtickets.adapter.in.web;
import com.travbook.railtickets.application.port.in.RailTickets;
import com.travbook.railtickets.domain.model.RailTicket.*;
import com.travbook.shared.domain.CustomerIdentity;
import com.travbook.platform.security.AuthenticatedIdentity;
import jakarta.servlet.http.HttpServletRequest;import jakarta.validation.Valid;import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;import org.springframework.security.core.Authentication;import org.springframework.http.*;
import java.math.BigDecimal;import java.time.Clock;import java.nio.charset.StandardCharsets;import java.util.*;
@RestController public class PassengerCancellationController {
 private final RailTickets tickets;private final Clock clock;
 public PassengerCancellationController(RailTickets tickets,Clock clock){this.tickets=tickets;this.clock=clock;}
 public record Selection(@NotNull @Size(min=1,max=6) List<@NotNull @DecimalMin("0") @DecimalMax("5") @Digits(integer=1,fraction=0) BigDecimal> passengerIndexes){public Selection{if(passengerIndexes!=null)passengerIndexes=passengerIndexes.stream().map(value->value==null?null:value.stripTrailingZeros()).toList();}}
 private UUID correlation(HttpServletRequest r){return UUID.fromString((String)r.getAttribute("correlationId"));}
 private RailTicketViews.BookingV2 view(CustomerIdentity owner,Booking b){var details=tickets.details(owner,b.id());return RailTicketViews.BookingV2.of(details.booking(),clock.instant(),details.cancellations());}
 @GetMapping("/api/v2/rail-ticket-bookings/{id}") public RailTicketViews.BookingV2 get(Authentication a,@PathVariable UUID id){var owner=AuthenticatedIdentity.from(a);return view(owner,tickets.get(owner,id));}
 @GetMapping("/api/v2/rail-ticket-pnr/{pnr}") public RailTicketViews.BookingV2 pnr(Authentication a,@PathVariable String pnr){var owner=AuthenticatedIdentity.from(a);return view(owner,tickets.pnr(owner,pnr));}
 @PostMapping("/api/v2/rail-ticket-bookings/{id}/cancellation-quotes") @ResponseStatus(HttpStatus.CREATED) public RailTicketViews.PassengerCancellationView quote(Authentication a,@PathVariable UUID id,@Valid @RequestBody Selection selection,@RequestHeader("Idempotency-Key") String key,HttpServletRequest r){return RailTicketViews.PassengerCancellationView.of(tickets.passengerCancellationQuote(AuthenticatedIdentity.from(a),id,selection.passengerIndexes().stream().map(value->value.intValueExact()).toList(),key,correlation(r)));}
 @PostMapping("/api/v2/rail-ticket-bookings/{id}/cancellations") @ResponseStatus(HttpStatus.ACCEPTED) public RailTicketViews.BookingV2 cancel(Authentication a,@PathVariable UUID id,@Valid @RequestBody RailTicketController.CancelRequest body,@RequestHeader("Idempotency-Key") String key,HttpServletRequest r){var owner=AuthenticatedIdentity.from(a);return view(owner,tickets.cancelPassengers(owner,id,body.cancellationQuoteId(),body.consent(),body.simulateDelay(),body.simulateResponseLoss(),key,correlation(r)));}
 private RailTicketViews.VoucherView metadata(Voucher v,int revision){return new RailTicketViews.VoucherView(v.id(),v.bookingId(),"MOCK_RAIL_TICKET_SUMMARY","MOCK",v.createdAt(),v.sha256(),v.byteLength(),"/api/v2/rail-ticket-bookings/"+v.bookingId()+"/voucher/content?revision="+revision);}
 @PostMapping("/api/v2/rail-ticket-bookings/{id}/voucher") @ResponseStatus(HttpStatus.CREATED) public RailTicketViews.VoucherView prepare(Authentication a,@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,HttpServletRequest r){var owner=AuthenticatedIdentity.from(a);var v=tickets.passengerVoucher(owner,id,key,correlation(r));return metadata(v,v.revision());}
 @GetMapping("/api/v2/rail-ticket-bookings/{id}/voucher") public RailTicketViews.VoucherView metadata(Authentication a,@PathVariable UUID id){var owner=AuthenticatedIdentity.from(a);int revision=tickets.get(owner,id).cancelledPassengerIndexes().size();return metadata(tickets.readPassengerVoucher(owner,id,revision),revision);}
 @GetMapping("/api/v2/rail-ticket-bookings/{id}/voucher/content") public ResponseEntity<byte[]> download(Authentication a,@PathVariable UUID id,@RequestParam int revision){var v=tickets.readPassengerVoucher(AuthenticatedIdentity.from(a),id,revision);return ResponseEntity.ok().contentType(new MediaType("text","plain",StandardCharsets.UTF_8)).header("Content-Disposition","attachment; filename=\"travbook-MOCK-rail-"+v.id()+".txt\"").header("Cache-Control","no-store").header("X-Content-Type-Options","nosniff").body(v.content().getBytes(StandardCharsets.UTF_8));}
}
