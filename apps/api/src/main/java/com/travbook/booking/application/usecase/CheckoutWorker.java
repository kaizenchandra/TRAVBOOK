package com.travbook.booking.application.usecase;
import com.travbook.booking.application.port.in.RunCheckout;
import com.travbook.booking.application.port.out.*;
import com.travbook.booking.domain.model.*;
import com.travbook.shared.domain.DomainFailure;
import java.time.*;
import java.util.*;
public final class CheckoutWorker implements RunCheckout {
    private final CheckoutStore store;private final BookingValidationPort validation;private final CheckoutPayments payments;private final CheckoutSupplier supplier;private final Clock clock;
    public CheckoutWorker(CheckoutStore store,BookingValidationPort validation,CheckoutPayments payments,CheckoutSupplier supplier,Clock clock){this.store=store;this.validation=validation;this.payments=payments;this.supplier=supplier;this.clock=clock;}
    public boolean runNext(){
        var claimed=store.claim(Duration.ofSeconds(15));if(claimed.isEmpty())return false;
        var claim=claimed.get();var c=claim.checkout();Checkout next;
        if(claim.attempt()>5){store.finish(claim,c.review(clock.instant()));return true;}
        try{
            if(c.step()==Checkout.Step.REVALIDATE){checkCurrent(c);next=c.verified(clock.instant());}
            else{
                boolean payment=Set.of(Checkout.Step.AUTHORIZE,Checkout.Step.CAPTURE,Checkout.Step.VOID).contains(c.step());
                var known=payment?payments.lookup(c.operationId()):supplier.lookup(c.operationId());
                // A lookup failure is unknown, not absence. Only an authoritative empty result permits an idempotent mutation.
                if(known.isEmpty()){
                    if(c.step()==Checkout.Step.AUTHORIZE)c.quote().requireValid(clock.instant());
                    if(c.step()==Checkout.Step.RESERVE)checkCurrent(c);
                }
                var result=known.orElseGet(()->payment?payments.execute(c):supplier.execute(c));
                next=c.effect(result,clock.instant());
            }
        }catch(DomainFailure failure){
            if(Set.of(DomainFailure.Code.QUOTE_EXPIRED,DomainFailure.Code.OFFER_EXPIRED,DomainFailure.Code.OFFER_UNAVAILABLE,DomainFailure.Code.QUOTE_VERSION_CONFLICT).contains(failure.code())){
                // Lookup returned definitive absence before any eligibility exception can occur.
                next=c.rejected(failure.code().name(),clock.instant());
            }else next=c.uncertain(claim.attempt()>=5,clock.instant());
        }catch(IllegalArgumentException incompatible){next=c.review(clock.instant());}
        catch(RuntimeException unknown){next=c.uncertain(claim.attempt()>=5,clock.instant());}
        store.finish(claim,next);return true;
    }
    private void checkCurrent(Checkout c){
        c.quote().requireValid(clock.instant());var current=validation.current(c.quote());c.quote().requireValid(clock.instant());
        if(!c.quote().terms().samePrice(current)||!c.quote().terms().sameTerms(current))throw new DomainFailure(DomainFailure.Code.QUOTE_VERSION_CONFLICT,"Price or policies changed; accept a new quote.");
    }
}
