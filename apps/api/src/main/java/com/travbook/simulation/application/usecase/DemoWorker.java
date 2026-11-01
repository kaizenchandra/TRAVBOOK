package com.travbook.simulation.application.usecase;
import com.travbook.simulation.application.port.in.ProcessDemoOperations;
import com.travbook.simulation.application.port.out.*;
import com.travbook.simulation.domain.model.DemoOperation.Outcome;
import java.time.Clock;
public final class DemoWorker implements ProcessDemoOperations {
    private final DemoStore store;private final DemoDelivery delivery;private final Clock clock;
    public DemoWorker(DemoStore store,DemoDelivery delivery,Clock clock){this.store=store;this.delivery=delivery;this.clock=clock;}
    public boolean tick(){
        var claim=store.claim(clock.instant());if(claim.isEmpty())return false;
        var c=claim.get();
        try {
            var result=delivery.accepted(c.operation())?Outcome.ACCEPTED:c.lookupOnly()?Outcome.TRANSIENT:delivery.deliver(c.operation());
            store.complete(c,c.operation().complete(result,clock.instant()));return true;
        } catch(RuntimeException failure) {
            if(c.operation().attempts()>=3)store.complete(c,c.operation().review(clock.instant()));
            throw failure;
        }
    }
}
