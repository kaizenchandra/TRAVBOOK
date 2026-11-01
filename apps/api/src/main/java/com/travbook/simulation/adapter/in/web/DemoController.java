package com.travbook.simulation.adapter.in.web;
import com.travbook.simulation.application.port.in.DemoServices;
import com.travbook.simulation.domain.model.DemoOperation;
import com.travbook.customer.domain.model.Profile;
import com.travbook.journeys.domain.model.Care;
import com.travbook.shared.domain.TravelMoney;
import com.travbook.platform.security.AuthenticatedIdentity;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.*;
@RestController
@RequestMapping("/api/v1/demo")
@ConditionalOnProperty(name="travbook.demo.enabled",havingValue="true")
public class DemoController {
    private final DemoServices demo;
    public DemoController(DemoServices demo){this.demo=demo;}
    public record Input(@NotNull UUID expectedCustomerId,@NotNull DemoOperation.Kind kind,@NotNull DemoOperation.Scenario scenario,boolean consent){}
    public record CouponInput(@NotNull UUID quoteId,@NotBlank @Size(max=32) String code){}
    public record OperationView(UUID customerId,UUID id,String kind,String status,int attempts,long version,Instant createdAt,Instant updatedAt,Instant nextAttemptAt,String reference,boolean terminal,String mode,String notice){
        static OperationView of(DemoOperation o){return new OperationView(o.owner(),o.id(),o.kind().name(),o.status().name(),o.attempts(),o.version(),o.createdAt(),o.updatedAt(),o.nextAttemptAt(),o.reference(),o.terminal(),"MOCK",o.notice());}
    }
    public record Page(UUID customerId,List<OperationView> items,UUID next,String mode){}
    public record Catalog(String mode,List<DemoServices.Capability> services,String notice){}
    public record Deployment(String mode,String target,String awsStatus,boolean awsDeployed,String notice){}
    public record CouponView(UUID customerId,UUID quoteId,String code,boolean eligible,String reason,TravelMoney originalTotal,TravelMoney discount,TravelMoney previewTotal,Instant expiresAt,boolean applied,String mode,String notice){}
    public record GpsView(UUID customerId,Care.Dispatch dispatch,String latitude,String longitude,boolean stale,boolean terminal,Instant asOf,String mode,String notice){}
    public record ProfileView(UUID customerId,long version,String displayName,String email,String phone,boolean emailVerified,boolean phoneVerified,Instant updatedAt){
        static ProfileView of(Profile p){return new ProfileView(p.customerId(),p.version(),p.fields().displayName(),p.fields().email(),p.fields().phone(),false,false,p.updatedAt());}
    }
    public record PrivacyView(ProfileView profile,List<String> included,List<String> excluded,boolean completeExport,String mode,String notice){}
    public record FinancialView(UUID customerId,String capturedAmount,String refundedAmount,String pendingRefundAmount,String authorizedAmount,String netCaptured,long accountCount,long captureCount,long refundCount,long integrityIssues,String currency,Instant asOf,String mode,String settlementStatus,String notice){}
    public record PaymentView(UUID customerId,UUID id,String state,TravelMoney amount,TravelMoney committedRefunds,TravelMoney settledRefunds,String reference,String mode){}
    @GetMapping("/catalog") public Catalog catalog(){return new Catalog("MOCK",demo.catalog(),"Dummy integrations only. Existing booking APIs enforce ownership, quotes and financial invariants.");}
    @GetMapping("/deployment") public Deployment deployment(){return new Deployment("MOCK","LOCAL_COMPOSE","NOT_DEPLOYED",false,"No AWS target or resources are provisioned. Use local Compose and mocked Terraform tests for this demonstration.");}
    @PostMapping("/operations") @ResponseStatus(HttpStatus.ACCEPTED)
    public OperationView create(Authentication auth,@Valid @RequestBody Input input,@RequestHeader("Idempotency-Key")String key,HttpServletRequest request){
        return OperationView.of(demo.create(AuthenticatedIdentity.from(auth),input.expectedCustomerId(),input.kind(),input.scenario(),input.consent(),key,UUID.fromString((String)request.getAttribute("correlationId"))));
    }
    @GetMapping("/operations") public Page list(Authentication auth,@RequestParam(required=false)UUID after){var p=demo.list(AuthenticatedIdentity.from(auth),after);return new Page(p.customerId(),p.items().stream().map(OperationView::of).toList(),p.next(),"MOCK");}
    @GetMapping("/operations/{id}") public OperationView get(Authentication auth,@PathVariable UUID id){return OperationView.of(demo.get(AuthenticatedIdentity.from(auth),id));}
    @PostMapping("/coupons/preview") public CouponView coupon(Authentication auth,@Valid @RequestBody CouponInput input){
        var p=demo.coupon(AuthenticatedIdentity.from(auth),input.quoteId(),input.code());var c=p.coupon();
        return new CouponView(p.customerId(),p.quoteId(),c.code(),c.eligible(),c.reason(),p.originalTotal(),c.discount(),c.previewTotal(),p.expiresAt(),false,"MOCK","Preview only. No coupon is applied or redeemed; the immutable booking quote and payment amount are unchanged.");
    }
    @GetMapping("/gps/{id}") public GpsView gps(Authentication auth,@PathVariable UUID id){var p=demo.gps(AuthenticatedIdentity.from(auth),id);return new GpsView(p.customerId(),p.dispatch(),p.latitude(),p.longitude(),p.stale(),p.terminal(),p.asOf(),"MOCK","Synthetic stage-based coordinates, not live GPS or an actual route. No person or vehicle is tracked.");}
    @GetMapping("/privacy-preview") public PrivacyView privacy(Authentication auth){return new PrivacyView(ProfileView.of(demo.privacyPreview(AuthenticatedIdentity.from(auth))),List.of("Current customer profile"),List.of("Identity-provider data","Travellers and bookings","Payments and documents","Audit and retention records"),false,"MOCK","Contains your actual saved profile fields only. This is not a complete account export or an erasure operation.");}
    private String amount(java.math.BigDecimal value){return value.setScale(2).toPlainString();}
    @GetMapping("/financial-report") public FinancialView report(Authentication auth){var report=demo.report(AuthenticatedIdentity.from(auth));var r=report.snapshot();return new FinancialView(report.customerId(),amount(r.captured()),amount(r.refunded()),amount(r.pendingRefund()),amount(r.authorized()),amount(r.netCaptured()),r.accountCount(),r.captureCount(),r.refundCount(),r.integrityIssues(),"INR",r.asOf(),"MOCK","NOT_RECONCILED","Owned local MOCK ledger records only. No real funds, bank balances or external settlement reconciliation.");}
    @GetMapping("/payments/{id}") public PaymentView payment(Authentication auth,@PathVariable UUID id){var p=demo.payment(AuthenticatedIdentity.from(auth),id);return new PaymentView(p.owner(),p.paymentId(),p.state().name(),TravelMoney.inr(p.amount()),TravelMoney.inr(p.committedRefunds()),TravelMoney.inr(p.settledRefunds()),p.reference(),"MOCK");}
}
