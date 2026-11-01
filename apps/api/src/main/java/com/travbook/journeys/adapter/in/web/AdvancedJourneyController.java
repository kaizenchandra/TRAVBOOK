package com.travbook.journeys.adapter.in.web;
import com.travbook.journeys.application.port.in.AdvancedJourneys;
import com.travbook.journeys.domain.model.*;
import com.travbook.platform.security.AuthenticatedIdentity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
@RestController public class AdvancedJourneyController {
 private final AdvancedJourneys journeys;private final Clock clock;
 public AdvancedJourneyController(AdvancedJourneys j,Clock c){journeys=j;clock=c;}
 public record QuoteView(@JsonUnwrapped JourneyController.QuoteView core,Care.Terms terms,AdvancedJourneys.Replacement replacement){static QuoteView of(AdvancedJourneys.Quotation q){return new QuoteView(JourneyController.QuoteView.of(q.details()),q.terms(),q.replacement());}}
 public record ActionView(UUID id,UUID bookingId,Care.Type type,Care.State status,Care.Phase step,List<String> componentIds,List<Care.RefundPart> refunds,UUID replacementId,String failure,Instant createdAt,Instant updatedAt,String mode){static ActionView of(Care.Action a){return new ActionView(a.id(),a.booking().id(),a.type(),a.status(),a.step(),a.componentIds(),a.refunds(),a.replacementId(),a.failure(),a.createdAt(),a.updatedAt(),"MOCK");}}
 public record BookingView(@JsonUnwrapped JourneyController.BookingView core,Care.Terms terms,Care.Funding funding,List<String> activeComponentIds,List<ActionView> actions,boolean documentAvailable,boolean legacy,UUID rescheduledFrom,UUID rescheduledTo){}
 public record Page(List<BookingView> items,UUID next){}
 public record QuoteRequest(@NotNull @Valid Journey.Request selection,@NotNull Care.Plan fundingPlan){}
 public record Accept(@Min(1) @Max(2) int version,@AssertTrue boolean consent){}
 public record CancelQuote(@NotNull @Size(min=1,max=10) List<@NotBlank @Size(max=40) String> componentIds){}
 public record Cancel(@NotNull UUID quoteId,@AssertTrue boolean consent,boolean simulateDelay,boolean responseLoss){}
 public record Balance(boolean simulateDecline,boolean responseLoss){}
 public record Replace(@NotNull UUID quoteId,@Min(1) long version,@AssertTrue boolean consent){}
 public record Advance(@Min(1) long version,@AssertTrue boolean consent){}
 private UUID correlation(HttpServletRequest r){return UUID.fromString((String)r.getAttribute("correlationId"));}
 private BookingView view(Care.Summary s){return new BookingView(JourneyController.BookingView.of(s.booking(),clock.instant()),s.terms(),s.funding(),s.activeComponentIds(),s.actions().stream().map(ActionView::of).toList(),s.documentAvailable(),s.legacy(),s.rescheduledFrom(),s.rescheduledTo());}
 @PostMapping("/api/v2/journey-quotes") @ResponseStatus(HttpStatus.CREATED) public QuoteView quote(Authentication a,@Valid @RequestBody QuoteRequest r,@RequestHeader("Idempotency-Key") String key,HttpServletRequest h){return QuoteView.of(journeys.quote(AuthenticatedIdentity.from(a),r.selection(),r.fundingPlan(),key,correlation(h)));}
 @GetMapping("/api/v2/journey-quotes/{id}") public QuoteView quote(Authentication a,@PathVariable UUID id){return QuoteView.of(journeys.quote(AuthenticatedIdentity.from(a),id));}
 @PostMapping("/api/v2/journey-quotes/{id}/acceptance") public QuoteView accept(Authentication a,@PathVariable UUID id,@Valid @RequestBody Accept r,@RequestHeader("Idempotency-Key") String key,HttpServletRequest h){return QuoteView.of(journeys.accept(AuthenticatedIdentity.from(a),id,r.version(),r.consent(),key,correlation(h)));}
 @PostMapping("/api/v2/journey-bookings") @ResponseStatus(HttpStatus.ACCEPTED) public BookingView book(Authentication a,@Valid @RequestBody JourneyController.Book r,@RequestHeader("Idempotency-Key") String key,HttpServletRequest h){return view(journeys.book(AuthenticatedIdentity.from(a),r.quoteId(),r.passengers(),r.email(),r.scenario(),key,correlation(h)));}
 @GetMapping("/api/v2/journey-bookings/{id}") public BookingView get(Authentication a,@PathVariable UUID id){return view(journeys.get(AuthenticatedIdentity.from(a),id));}
 @GetMapping("/api/v2/me/journey-bookings") public Page list(Authentication a,@RequestParam(required=false) UUID after){var p=journeys.list(AuthenticatedIdentity.from(a),after);return new Page(p.items().stream().map(this::view).toList(),p.next());}
 @PostMapping("/api/v2/journey-bookings/{id}/payment") @ResponseStatus(HttpStatus.ACCEPTED) public BookingView pay(Authentication a,@PathVariable UUID id,@RequestBody JourneyController.Empty r,@RequestHeader("Idempotency-Key") String key,HttpServletRequest h){return view(journeys.pay(AuthenticatedIdentity.from(a),id,key,correlation(h)));}
 @PostMapping("/api/v2/journey-bookings/{id}/balance") @ResponseStatus(HttpStatus.ACCEPTED) public ActionView balance(Authentication a,@PathVariable UUID id,@RequestBody Balance r,@RequestHeader("Idempotency-Key") String key,HttpServletRequest h){return ActionView.of(journeys.balance(AuthenticatedIdentity.from(a),id,r.simulateDecline(),r.responseLoss(),key,correlation(h)));}
 @PostMapping("/api/v2/journey-bookings/{id}/cancellation-quotes") @ResponseStatus(HttpStatus.CREATED) public Care.CancellationQuote cancelQuote(Authentication a,@PathVariable UUID id,@Valid @RequestBody CancelQuote r,@RequestHeader("Idempotency-Key") String key,HttpServletRequest h){return journeys.cancellationQuote(AuthenticatedIdentity.from(a),id,r.componentIds(),key,correlation(h));}
 @PostMapping("/api/v2/journey-bookings/{id}/cancellations") @ResponseStatus(HttpStatus.ACCEPTED) public ActionView cancel(Authentication a,@PathVariable UUID id,@Valid @RequestBody Cancel r,@RequestHeader("Idempotency-Key") String key,HttpServletRequest h){return ActionView.of(journeys.cancel(AuthenticatedIdentity.from(a),id,r.quoteId(),r.consent(),r.simulateDelay(),r.responseLoss(),key,correlation(h)));}
 @PostMapping("/api/v2/journey-bookings/{id}/reschedule-quotes") @ResponseStatus(HttpStatus.CREATED) public QuoteView rescheduleQuote(Authentication a,@PathVariable UUID id,@RequestBody Journey.Request r,@RequestHeader("Idempotency-Key") String key,HttpServletRequest h){return QuoteView.of(journeys.rescheduleQuote(AuthenticatedIdentity.from(a),id,r,key,correlation(h)));}
 @PostMapping("/api/v2/journey-bookings/{id}/reschedules") @ResponseStatus(HttpStatus.ACCEPTED) public ActionView replace(Authentication a,@PathVariable UUID id,@Valid @RequestBody Replace r,@RequestHeader("Idempotency-Key") String key,HttpServletRequest h){return ActionView.of(journeys.reschedule(AuthenticatedIdentity.from(a),id,r.quoteId(),r.version(),r.consent(),key,correlation(h)));}
 @GetMapping("/api/v2/journey-bookings/{id}/document") public Journey.Document document(Authentication a,@PathVariable UUID id){return journeys.document(AuthenticatedIdentity.from(a),id);}
 @GetMapping("/api/v2/journey-bookings/{id}/dispatch") public Care.Dispatch dispatch(Authentication a,@PathVariable UUID id){return journeys.dispatch(AuthenticatedIdentity.from(a),id);}
 @PostMapping("/api/v2/journey-bookings/{id}/dispatch-demo") public Care.Dispatch advance(Authentication a,@PathVariable UUID id,@Valid @RequestBody Advance r,@RequestHeader("Idempotency-Key") String key,HttpServletRequest h){return journeys.advanceDemo(AuthenticatedIdentity.from(a),id,r.version(),r.consent(),key,correlation(h));}
}
