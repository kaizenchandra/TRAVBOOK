package com.travbook.flightprovider.adapter.in.web;
import com.travbook.flightprovider.application.port.in.FlightProvider;
import com.travbook.flightprovider.domain.model.*;
import com.travbook.platform.security.AuthenticatedIdentity;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
@RestController
@ConditionalOnProperty(name="travbook.demo.enabled",havingValue="true")
public class PromotionController {
    private final FlightProvider provider;
    public PromotionController(FlightProvider provider){this.provider=provider;}
    public record Input(@NotNull UUID expectedCustomerId,@NotBlank @Size(max=32) String flightId,@NotNull @Size(min=1,max=6) List<FlightFixture.@NotNull Selection> selections,@NotNull @Size(max=24) String couponCode){}
    @PostMapping("/api/v2/demo/flight-provider/quotes") @ResponseStatus(HttpStatus.CREATED)
    public FlightProviderController.Response<ProviderQuote> quote(Authentication auth,@Valid @RequestBody Input r,@RequestHeader("Idempotency-Key")String key,HttpServletRequest request){var q=provider.promotionalQuote(AuthenticatedIdentity.from(auth),r.expectedCustomerId(),r.flightId(),r.selections(),r.couponCode(),key,UUID.fromString((String)request.getAttribute("correlationId")));return new FlightProviderController.Response<>("MOCK",FlightFixture.NOTICE,q);}
    @GetMapping("/api/v1/demo/flight-provider/quotes/{id}/promotion") public FlightProvider.PromotionView get(Authentication auth,@PathVariable UUID id){return provider.promotion(AuthenticatedIdentity.from(auth),id);}
}
