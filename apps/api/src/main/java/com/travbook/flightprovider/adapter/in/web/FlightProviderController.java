package com.travbook.flightprovider.adapter.in.web;
import com.travbook.flightprovider.application.port.in.FlightProvider;
import com.travbook.flightprovider.domain.model.*;
import com.travbook.shared.domain.TravelMoney;
import com.travbook.platform.security.AuthenticatedIdentity;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/demo/flight-provider")
@ConditionalOnProperty(name="travbook.demo.enabled",havingValue="true")
public class FlightProviderController {
    private final FlightProvider provider;
    public FlightProviderController(FlightProvider provider){this.provider=provider;}
    public record Response<T>(String mode,String notice,T data) {}
    private <T> Response<T> response(T data){return new Response<>("MOCK",FlightFixture.NOTICE,data);}
    private UUID correlation(HttpServletRequest request){return UUID.fromString((String)request.getAttribute("correlationId"));}
    public record QuoteInput(@NotBlank @Size(max=32) String flightId,@NotNull @Size(min=1,max=6) List<FlightFixture.@NotNull Selection> selections) {}
    public record AcceptedMoney(@NotNull @Pattern(regexp="[0-9]{1,8}\\.[0-9]{2}") String amount,@NotNull @Pattern(regexp="INR") String currency) {}
    public record ReserveInput(@NotNull UUID quoteId,@Min(1) @Max(1) int quoteVersion,@NotNull @Valid AcceptedMoney acceptedTotal,boolean consent) {}
    public record Consent(boolean consent) {}
    public enum PaymentOutcome { SUCCEED, DECLINE }
    public enum TicketOutcome { ISSUE, REJECT }
    public record PaymentInput(@NotNull PaymentOutcome outcome,boolean consent) {}
    public record TicketInput(@NotNull TicketOutcome outcome,boolean consent) {}
    public record CheckInInput(@NotNull @Size(min=1,max=6) List<@NotNull @Pattern(regexp="P[1-6]") String> passengerIds,boolean consent) {}
    public record Capabilities(String provider,String paymentProvider,String basePath,String environment,String authentication,String credentialStoreReference,String checkInMethod,List<String> capabilities,List<String> unsupported) {}
    @GetMapping("/capabilities") public Response<Capabilities> capabilities(){return response(new Capabilities("travbook-fictional-air","travbook-fictional-pay","/api/v1/demo/flight-provider","LOCAL_MOCK","Existing verified customer OIDC bearer token or session; bookings:read/write scopes and cookie CSRF",null,"MOCK_API",List.of("SEAT_MAP","MEALS","BAGGAGE","IMMUTABLE_QUOTES","EXCLUSIVE_RESERVATION","SIMULATED_PAYMENT","TICKETING","PASSENGER_CHECK_IN","FULL_CANCELLATION","FULL_REFUND"),List.of("Real supplier or payment credentials","Existing platform booking fulfilment","Timed holds","Infants and special-assistance seating","Post-reservation ancillary changes","Partial cancellation","Official check-in/boarding passes","Signed gateway webhooks","SANDBOX","LIVE")));}
    @GetMapping("/flights") public Response<List<FlightFixture.Flight>> flights(@RequestParam LocalDate date){return response(provider.flights(date));}
    @GetMapping("/flights/{flightId}/seats") public Response<FlightFixture.SeatMap> seats(@PathVariable String flightId){return response(provider.seats(flightId));}
    @GetMapping("/flights/{flightId}/ancillaries") public Response<FlightFixture.Catalog> ancillaries(@PathVariable String flightId){return response(provider.ancillaries(flightId));}
    @PostMapping("/quotes") @ResponseStatus(HttpStatus.CREATED) public Response<ProviderQuote> quote(Authentication auth,@Valid @RequestBody QuoteInput r,@RequestHeader("Idempotency-Key")String key,HttpServletRequest request){return response(provider.quote(AuthenticatedIdentity.from(auth),r.flightId(),r.selections(),key,correlation(request)));}
    @GetMapping("/quotes/{id}") public Response<ProviderQuote> quote(Authentication auth,@PathVariable UUID id){return response(provider.quote(AuthenticatedIdentity.from(auth),id));}
    @PostMapping("/reservations") @ResponseStatus(HttpStatus.CREATED) public Response<ProviderReservation> reserve(Authentication auth,@Valid @RequestBody ReserveInput r,@RequestHeader("Idempotency-Key")String key,HttpServletRequest request){return response(provider.reserve(AuthenticatedIdentity.from(auth),r.quoteId(),r.quoteVersion(),new TravelMoney(r.acceptedTotal().amount(),r.acceptedTotal().currency()),r.consent(),key,correlation(request)));}
    @GetMapping("/reservations/{id}") public Response<ProviderReservation> get(Authentication auth,@PathVariable UUID id){return response(provider.reservation(AuthenticatedIdentity.from(auth),id));}
    @GetMapping("/reservations") public Response<FlightProvider.Page> list(Authentication auth,@RequestParam(required=false) UUID after){return response(provider.reservations(AuthenticatedIdentity.from(auth),after));}
    @PostMapping("/reservations/{id}/payments") public Response<ProviderReservation> payment(Authentication auth,@PathVariable UUID id,@Valid @RequestBody PaymentInput r,@RequestHeader("Idempotency-Key")String key,HttpServletRequest request){return response(provider.act(AuthenticatedIdentity.from(auth),id,r.outcome()==PaymentOutcome.SUCCEED?ProviderReservation.Action.PAY_SUCCESS:ProviderReservation.Action.PAY_DECLINE,List.of(),r.consent(),key,correlation(request)));}
    @PostMapping("/reservations/{id}/tickets") public Response<ProviderReservation> ticket(Authentication auth,@PathVariable UUID id,@Valid @RequestBody TicketInput r,@RequestHeader("Idempotency-Key")String key,HttpServletRequest request){return response(provider.act(AuthenticatedIdentity.from(auth),id,r.outcome()==TicketOutcome.ISSUE?ProviderReservation.Action.TICKET_ISSUE:ProviderReservation.Action.TICKET_REJECT,List.of(),r.consent(),key,correlation(request)));}
    @PostMapping("/reservations/{id}/check-in") public Response<ProviderReservation> checkIn(Authentication auth,@PathVariable UUID id,@Valid @RequestBody CheckInInput r,@RequestHeader("Idempotency-Key")String key,HttpServletRequest request){return response(provider.act(AuthenticatedIdentity.from(auth),id,ProviderReservation.Action.CHECK_IN,r.passengerIds(),r.consent(),key,correlation(request)));}
    @PostMapping("/reservations/{id}/cancellations") public Response<ProviderReservation> cancel(Authentication auth,@PathVariable UUID id,@RequestBody Consent r,@RequestHeader("Idempotency-Key")String key,HttpServletRequest request){return response(provider.act(AuthenticatedIdentity.from(auth),id,ProviderReservation.Action.CANCEL,List.of(),r.consent(),key,correlation(request)));}
    @PostMapping("/reservations/{id}/refunds") public Response<ProviderReservation> refund(Authentication auth,@PathVariable UUID id,@RequestBody Consent r,@RequestHeader("Idempotency-Key")String key,HttpServletRequest request){return response(provider.act(AuthenticatedIdentity.from(auth),id,ProviderReservation.Action.REFUND,List.of(),r.consent(),key,correlation(request)));}
    @GetMapping("/reservations/{id}/documents") public Response<List<ProviderReservation.Document>> documents(Authentication auth,@PathVariable UUID id){return response(provider.documents(AuthenticatedIdentity.from(auth),id));}
}
