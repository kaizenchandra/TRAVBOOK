package com.travbook.booking.application.usecase;
import com.travbook.booking.application.port.in.RunCancellation;
import com.travbook.booking.application.port.out.*;
import com.travbook.booking.domain.model.Cancellation;
import java.time.*;
public final class CancellationWorker implements RunCancellation {
    private final CancellationStore store;private final CancellationSupplier supplier;private final CancellationRefunds refunds;private final Clock clock;
    public CancellationWorker(CancellationStore store,CancellationSupplier supplier,CancellationRefunds refunds,Clock clock){this.store=store;this.supplier=supplier;this.refunds=refunds;this.clock=clock;}
    public boolean runNext(){
        var claimed=store.claim(Duration.ofSeconds(15));if(claimed.isEmpty())return false;var claim=claimed.get();var c=claim.cancellation();Cancellation next;
        if(claim.attempt()>5){store.finish(claim,c.uncertain(true,clock.instant()));return true;}
        try{
            if(c.step()==Cancellation.Step.CANCEL_SUPPLIER){
                var known=supplier.lookup(c.operationId());
                if(known.isEmpty()&&!c.quote().expiresAt().isAfter(clock.instant()))next=c.reject("CANCELLATION_QUOTE_EXPIRED",clock.instant());
                else next=c.supplier(known.orElseGet(()->supplier.cancel(c)),clock.instant());
            }else{
                var known=refunds.lookup(c.operationId());next=c.refund(known.orElseGet(()->refunds.submit(c)),claim.attempt()>=5,clock.instant());
            }
        }catch(IllegalArgumentException mismatch){next=c.uncertain(true,clock.instant());}
        catch(RuntimeException unknown){next=c.uncertain(claim.attempt()>=5,clock.instant());}
        store.finish(claim,next);return true;
    }
}
