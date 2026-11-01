package com.travbook.offers.adapter.in.web;
import com.travbook.offers.application.port.in.Coupons;
import com.travbook.customer.application.port.in.Customers;
import com.travbook.platform.security.AuthenticatedIdentity;
import com.travbook.shared.domain.TravelMoney;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.time.Instant;
import java.util.*;
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="travbook.demo.enabled",havingValue="true")
public class CouponController {
    private final Coupons coupons;private final Customers customers;
    public CouponController(Coupons coupons,Customers customers){this.coupons=coupons;this.customers=customers;}
    public record Offer(String code,int version,String scope,int percent,TravelMoney maximumDiscount,TravelMoney minimumBase,Instant startsAt,Instant endsAt,int globalLimit,int customerLimit,String terms){}
    public record Catalog(UUID customerId,String mode,List<Offer> items){}
    @GetMapping("/api/v1/coupons") public Catalog list(Authentication auth){var owner=customers.resolve(AuthenticatedIdentity.from(auth)).id();return new Catalog(owner,"MOCK",coupons.catalog(owner).stream().map(c->new Offer(c.code(),c.version(),c.scope(),c.percent(),c.maximumDiscount(),c.minimumBase(),c.startsAt(),c.endsAt(),c.globalLimit(),c.customerLimit(),c.terms())).toList());}
}
