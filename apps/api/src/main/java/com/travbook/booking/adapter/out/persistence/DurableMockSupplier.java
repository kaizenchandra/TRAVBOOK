package com.travbook.booking.adapter.out.persistence;
import com.travbook.booking.application.port.out.CheckoutSupplier;
import com.travbook.booking.domain.model.Checkout;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.*;
public final class DurableMockSupplier implements CheckoutSupplier {
    private final JdbcClient jdbc;private final TransactionTemplate tx;private final ObjectMapper json;private final Clock clock;
    public DurableMockSupplier(JdbcClient jdbc,TransactionTemplate tx,ObjectMapper json,Clock clock){this.jdbc=jdbc;this.tx=tx;this.json=json;this.clock=clock;}
    private void lock(UUID id){jdbc.sql("select pg_advisory_xact_lock(hashtextextended(:id,0))").param("id",id.toString()).query(Object.class).single();}
    public Optional<Checkout.Effect> lookup(UUID id){return tx.execute(t->{lock(id);return receipt(id);});}
    private Optional<Checkout.Effect> receipt(UUID id){return jdbc.sql("select receipt::text from mock_supplier_operation where operation_id=:id").param("id",id).query(String.class).optional().map(s->json.readValue(s,Checkout.Effect.class));}
    public Checkout.Effect execute(Checkout c){
        if(!Set.of("sample-aster","sample-horizon").contains(c.quote().terms().supplier()))throw new IllegalArgumentException("Supplier has no approved mock fulfilment capability");
        var result=tx.execute(t->{
            lock(c.operationId());var known=receipt(c.operationId());
            // The canonical effect binding excludes mutable workflow observations and retry counters.
            var request=Map.of("checkoutId",c.id(),"step",c.step(),"quote",c.quote(),"scenario",c.scenario());String input=json.writeValueAsString(request);
            if(known.isPresent()){
                String prior=jdbc.sql("select request::text from mock_supplier_operation where operation_id=:id").param("id",c.operationId()).query(String.class).single();
                if(!json.readTree(prior).equals(json.readTree(input)))throw new IllegalArgumentException("Supplier key reused");return known.get();
            }
            String state;String reference="MOCK-SUPPLIER-"+c.id();
            if(c.step()==Checkout.Step.RESERVE){
                c.quote().requireValid(clock.instant());state=c.scenario()==Checkout.Scenario.RESERVATION_REJECTED?"REJECTED":"CONFIRMED";
                jdbc.sql("insert into mock_supplier_reservation(checkout_id,reference,status,input) values(:id,:ref,:status,cast(:input as jsonb))")
                    .param("id",c.id()).param("ref",reference).param("status",state).param("input",input).update();
            }else if(c.step()==Checkout.Step.CANCEL){
                String current=jdbc.sql("select status from mock_supplier_reservation where checkout_id=:id for update").param("id",c.id()).query(String.class).single();
                if(!"CONFIRMED".equals(current))throw new IllegalStateException("Cannot cancel unconfirmed fixture reservation");state="CANCELLED";
                jdbc.sql("update mock_supplier_reservation set status='CANCELLED' where checkout_id=:id").param("id",c.id()).update();
            }else throw new IllegalArgumentException("Unsupported mock supplier operation");
            var r=new Checkout.Effect(c.operationId(),c.id(),state,!state.equals("REJECTED"),reference,c.quote().terms().total(),"MOCK");
            jdbc.sql("insert into mock_supplier_operation(operation_id,checkout_id,operation,request,receipt) values(:id,:checkout,:op,cast(:request as jsonb),cast(:receipt as jsonb))")
                .param("id",c.operationId()).param("checkout",c.id()).param("op",c.step().name()).param("request",input).param("receipt",json.writeValueAsString(r)).update();return r;
        });
        if(c.step()==Checkout.Step.RESERVE&&c.scenario()==Checkout.Scenario.RESERVATION_RESPONSE_LOST)throw new IllegalStateException("Simulated response loss after durable reservation");
        return result;
    }
}
