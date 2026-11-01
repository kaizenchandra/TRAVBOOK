package com.travbook.search.adapter.out.supplier;
import com.travbook.search.application.port.out.*;
import com.travbook.search.domain.model.*;
import com.travbook.shared.domain.DomainFailure;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.travbook.shared.domain.DomainFailure.Code.*;
public final class BoundedSupplierGateway implements SupplierGateway {
    private final List<FlightSupplier> providers;
    private final ExecutorService executor;
    private final long budgetMs;
    private final MeterRegistry metrics;
    public BoundedSupplierGateway(List<FlightSupplier> providers,ExecutorService executor,long budgetMs,MeterRegistry metrics) {
        this.providers=List.copyOf(providers); this.executor=executor; this.budgetMs=budgetMs; this.metrics=metrics;
    }
    public Results search(FlightQuery query,Instant now) {
        long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(budgetMs);
        var pending=new LinkedHashMap<FlightSupplier,Future<List<FlightOffer>>>();
        for(var p:providers) {
            try { pending.put(p,executor.submit(() -> p.search(query,now))); }
            catch(RejectedExecutionException overloaded) { pending.put(p,null); }
        }
        var offers=new ArrayList<FlightOffer>();
        var coverage=new ArrayList<SearchSnapshot.Coverage>();
        for(var entry:pending.entrySet()) {
            String outcome="unavailable";
            try {
                Future<List<FlightOffer>> future=entry.getValue();
                if(future==null) throw new RejectedExecutionException();
                var result=future.isDone() ? future.get() : future.get(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);
                offers.addAll(result);
                coverage.add(new SearchSnapshot.Coverage(entry.getKey().id(),SearchSnapshot.Coverage.Status.AVAILABLE));
                outcome="available";
            } catch(InterruptedException e) {
                Thread.currentThread().interrupt();
                coverage.add(new SearchSnapshot.Coverage(entry.getKey().id(),SearchSnapshot.Coverage.Status.UNAVAILABLE));
            } catch(ExecutionException | TimeoutException | RejectedExecutionException e) {
                coverage.add(new SearchSnapshot.Coverage(entry.getKey().id(),SearchSnapshot.Coverage.Status.UNAVAILABLE));
            } finally {
                if(entry.getValue()!=null) entry.getValue().cancel(true);
                metrics.counter("travbook.supplier.search","supplier",entry.getKey().id(),"outcome",outcome,"mode","MOCK").increment();
            }
        }
        return new Results(offers,coverage);
    }
    public FlightOffer reprice(FlightOffer offer,Instant now) {
        var provider=providers.stream().filter(p -> p.id().equals(offer.supplier())).findFirst()
            .orElseThrow(() -> new DomainFailure(SUPPLIERS_UNAVAILABLE,"Provider is not configured."));
        Future<FlightOffer> future=null;
        try {
            future=executor.submit(() -> provider.reprice(offer,now));
            return future.get(budgetMs,TimeUnit.MILLISECONDS);
        } catch(ExecutionException e) {
            if(e.getCause() instanceof DomainFailure failure) throw failure;
            throw new DomainFailure(SUPPLIERS_UNAVAILABLE,"Provider could not revalidate this offer.");
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DomainFailure(SUPPLIERS_UNAVAILABLE,"Revalidation was interrupted. Please retry.");
        } catch(TimeoutException | RejectedExecutionException e) {
            throw new DomainFailure(SUPPLIERS_UNAVAILABLE,"Provider did not respond within its time budget.");
        } finally { if(future!=null) future.cancel(true); }
    }
}

