package com.travbook.customer.adapter.in.web;

import com.travbook.customer.application.port.in.CustomerProfiles;
import com.travbook.customer.domain.model.*;
import com.travbook.platform.security.AuthenticatedIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/profile")
public class ProfileController {
    private final CustomerProfiles profiles;
    public ProfileController(CustomerProfiles profiles) { this.profiles=profiles; }
    public record Input(@NotNull UUID expectedCustomerId, @Min(0) @Max(9007199254740990L) long expectedVersion,
                        @NotNull @Size(max=80) String displayName, @NotNull @Size(max=254) String email,
                        @NotNull @Size(max=16) String phone, boolean consent) {}
    public record View(UUID customerId,long version,String displayName,String email,String phone,
                       boolean emailVerified,boolean phoneVerified,Instant updatedAt) {
        static View of(Profile p) { return new View(p.customerId(),p.version(),p.fields().displayName(),p.fields().email(),p.fields().phone(),false,false,p.updatedAt()); }
    }
    public record SavedView(long appliedVersion,boolean replayed,View profile) {}
    @GetMapping public View get(Authentication auth) { return View.of(profiles.get(AuthenticatedIdentity.from(auth))); }
    @PutMapping public SavedView save(Authentication auth,@Valid @RequestBody Input input,
                                     @RequestHeader("Idempotency-Key") String key,HttpServletRequest request) {
        var change=new ProfileChange(input.expectedCustomerId(),input.expectedVersion(),new Profile.Fields(input.displayName(),input.email(),input.phone()),input.consent());
        var saved=profiles.save(AuthenticatedIdentity.from(auth),change,key,UUID.fromString((String)request.getAttribute("correlationId")));
        return new SavedView(saved.appliedVersion(),saved.replayed(),View.of(saved.profile()));
    }
}
