package com.travbook.hotels.adapter.out.supplier;
import com.travbook.hotels.application.port.out.*;
import com.travbook.hotels.domain.model.*;
import com.travbook.shared.domain.DomainFailure;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.travbook.shared.domain.DomainFailure.Code.*;
public final class BoundedHotelGateway implements HotelSupplierGateway {
    private final List<HotelSupplier> providers;private final ExecutorService executor;private final long budget;private final MeterRegistry metrics;
    public BoundedHotelGateway(List<HotelSupplier> providers,ExecutorService executor,long budget, MeterRegistry metrics){this.providers=List.copyOf(providers);this.executor=executor;this.budget=budget;this.metrics=metrics;}
    public Result search(HotelCriteria criteria,Instant now) {
        long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(budget);var futures=new LinkedHashMap<HotelSupplier,Future<HotelSupplier.Inventory>>();
        for(var p:providers)try{futures.put(p,executor.submit(()->p.search(criteria,now)));}catch(RejectedExecutionException e){futures.put(p,null);}
        var properties=new ArrayList<HotelProperty>();var offers=new ArrayList<HotelOffer>();var coverage=new ArrayList<HotelSnapshot.Coverage>();
        for(var entry:futures.entrySet()) {
            var status=HotelSnapshot.Coverage.Status.UNAVAILABLE;
            try {
                if(entry.getValue()!=null){var result=entry.getValue().isDone()?entry.getValue().get():entry.getValue().get(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);
                    properties.addAll(result.properties());offers.addAll(result.offers());status=HotelSnapshot.Coverage.Status.AVAILABLE;}
            }catch(InterruptedException e){Thread.currentThread().interrupt();}catch(ExecutionException|TimeoutException e){/* Preserve explicit failed coverage. */}
            finally{if(entry.getValue()!=null)entry.getValue().cancel(true);}
            coverage.add(new HotelSnapshot.Coverage(entry.getKey().id(),status));metrics.counter("travbook.hotels.search","supplier",entry.getKey().id(),"outcome",status.name(),"mode","MOCK").increment();
        }
        return new Result(properties,offers,coverage);
    }
    public List<HotelOffer> changeRates(UUID reservation,String property,HotelCriteria criteria,List<HotelOffer.Plan> plans,List<UUID> ids,boolean deferred,Instant now){
     var provider=providers.stream().filter(p->property.startsWith(p.id()+"-")).findFirst().orElseThrow(()->new DomainFailure(SUPPLIERS_UNAVAILABLE,"Hotel provider unavailable."));Future<List<HotelOffer>> task=null;
     try{task=executor.submit(()->provider.changeRates(reservation,property,criteria,plans,ids,deferred,now));return task.get(budget,TimeUnit.MILLISECONDS);}catch(ExecutionException e){if(e.getCause() instanceof DomainFailure failure)throw failure;throw new DomainFailure(SUPPLIERS_UNAVAILABLE,"Replacement rates unavailable.");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new DomainFailure(SUPPLIERS_UNAVAILABLE,"Replacement rates interrupted.");}catch(TimeoutException|RejectedExecutionException e){throw new DomainFailure(SUPPLIERS_UNAVAILABLE,"Replacement rate budget exceeded.");}finally{if(task!=null)task.cancel(true);}
    }
    public List<HotelOffer> reprice(HotelCriteria criteria,List<HotelOffer> selection,Instant now) {
        var p=providers.stream().filter(v->v.id().equals(selection.getFirst().supplier())).findFirst().orElseThrow(()->new DomainFailure(SUPPLIERS_UNAVAILABLE,"Hotel provider is unavailable."));
        Future<List<HotelOffer>> task=null;String outcome="unavailable";
        try{task=executor.submit(()->p.reprice(criteria,selection,now));var result=task.get(budget,TimeUnit.MILLISECONDS);outcome="available";return result;}
        catch(ExecutionException e){if(e.getCause() instanceof DomainFailure failure){outcome=failure.code().name();throw failure;}throw new DomainFailure(SUPPLIERS_UNAVAILABLE,"Hotel provider could not revalidate this selection.");}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new DomainFailure(SUPPLIERS_UNAVAILABLE,"Hotel revalidation interrupted. Please retry.");}
        catch(TimeoutException|RejectedExecutionException e){throw new DomainFailure(SUPPLIERS_UNAVAILABLE,"Hotel provider did not respond within the time budget.");}
        finally{if(task!=null)task.cancel(true);metrics.counter("travbook.hotels.reprice","supplier",p.id(),"outcome",outcome,"mode","MOCK").increment();}
    }
}
