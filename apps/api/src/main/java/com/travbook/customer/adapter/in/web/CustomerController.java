package com.travbook.customer.adapter.in.web;
import com.travbook.customer.application.port.in.Customers;
import com.travbook.platform.security.AuthenticatedIdentity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;
@RestController
public class CustomerController {
    private final Customers customers;
    public CustomerController(Customers customers) { this.customers=customers; }
    public record View(UUID id,Instant createdAt) {}
    @GetMapping("/api/v1/me") public View me(Authentication authentication) {
        var c=customers.resolve(AuthenticatedIdentity.from(authentication));
        return new View(c.id(),c.createdAt());
    }
}

