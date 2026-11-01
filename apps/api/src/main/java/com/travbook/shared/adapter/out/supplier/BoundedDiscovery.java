package com.travbook.shared.adapter.out.supplier;
import com.travbook.shared.domain.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
/** Adapter utility, not an application dependency; failures retain explicit per-provider coverage. */
public final class BoundedDiscovery {
 public record Batch<T>(List<T> items,List<SupplierCoverage> coverage){}
 private final ExecutorService pool;private final MeterRegistry metrics;private final long millis;
 public BoundedDiscovery(ExecutorService pool,MeterRegistry metrics,long millis){this.pool=pool;this.metrics=metrics;this.millis=millis;}
 public <T> Batch<T> collect(String vertical,List<String> providers,Function<String,List<T>> call){if(providers.isEmpty()||providers.size()>4)throw new IllegalArgumentException("Choose one to four provider profiles");long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(millis);var tasks=new LinkedHashMap<String,Future<List<T>>>();for(var p:providers)try{tasks.put(p,pool.submit(()->call.apply(p)));}catch(RejectedExecutionException e){tasks.put(p,null);}var items=new ArrayList<T>();var coverage=new ArrayList<SupplierCoverage>();for(var entry:tasks.entrySet()){var status=SupplierCoverage.Status.UNAVAILABLE;try{if(entry.getValue()!=null){items.addAll(entry.getValue().get(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS));status=SupplierCoverage.Status.AVAILABLE;}}catch(InterruptedException e){Thread.currentThread().interrupt();}catch(ExecutionException|TimeoutException e){/* Unavailability is never silently empty inventory. */}finally{if(entry.getValue()!=null)entry.getValue().cancel(true);}coverage.add(new SupplierCoverage(entry.getKey(),status));metrics.counter("travbook.discovery.supplier","vertical",vertical,"supplier",entry.getKey(),"outcome",status.name(),"mode","MOCK").increment();}return new Batch<>(List.copyOf(items),List.copyOf(coverage));}
 public <T> T one(String vertical,String provider,Callable<T> call){Future<T> task=null;try{task=pool.submit(call);return task.get(millis,TimeUnit.MILLISECONDS);}catch(ExecutionException e){if(e.getCause() instanceof DomainFailure failure)throw failure;throw unavailable();}catch(InterruptedException e){Thread.currentThread().interrupt();throw unavailable();}catch(TimeoutException|RejectedExecutionException e){throw unavailable();}finally{if(task!=null)task.cancel(true);metrics.counter("travbook.discovery.recheck","vertical",vertical,"supplier",provider,"mode","MOCK").increment();}}
 private DomainFailure unavailable(){return new DomainFailure(DomainFailure.Code.SUPPLIERS_UNAVAILABLE,"The simulated provider could not respond within the request budget. Retry safely.");}
}
