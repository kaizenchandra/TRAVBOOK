package com.travbook.customer.adapter.in.web;
import com.travbook.customer.domain.model.TravelOrganizer;
import com.travbook.customer.application.port.in.CustomerOrganizer;
import com.travbook.platform.security.AuthenticatedIdentity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/me/travel-organizer")
public class OrganizerController {
    private final CustomerOrganizer organizer;
    public OrganizerController(CustomerOrganizer organizer){this.organizer=organizer;}
    public record Input(@NotNull UUID expectedCustomerId,@Min(0) long expectedVersion,@NotNull TravelOrganizer.Fields fields,boolean consent){}
    @GetMapping public TravelOrganizer get(Authentication auth){return organizer.get(AuthenticatedIdentity.from(auth));}
    @PutMapping public CustomerOrganizer.Saved save(Authentication auth,@Valid @RequestBody Input r,@RequestHeader("Idempotency-Key")String key,HttpServletRequest request){return organizer.save(AuthenticatedIdentity.from(auth),r.expectedCustomerId(),r.expectedVersion(),r.fields(),r.consent(),key,UUID.fromString((String)request.getAttribute("correlationId")));}
}
