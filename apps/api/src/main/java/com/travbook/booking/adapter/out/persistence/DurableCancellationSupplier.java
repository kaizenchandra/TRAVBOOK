package com.travbook.booking.adapter.out.persistence;
import com.travbook.booking.application.port.out.CancellationSupplier;
import com.travbook.booking.domain.model.*;
import com.travbook.shared.domain.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.*;
public final class DurableCancellationSupplier implements CancellationSupplier {
    private final JdbcClient jdbc;private final TransactionTemplate tx;private final ObjectMapper json;private final Clock clock;
    public DurableCancellationSupplier(JdbcClient jdbc,TransactionTemplate tx,ObjectMapper json,Clock clock){this.jdbc=jdbc;this.tx=tx;this.json=json;this.clock=clock;}
    private void lock(UUID id){jdbc.sql("select pg_advisory_xact_lock(hashtextextended(:id,0))").param("id",id.toString()).query(Object.class).single();}
    private record Reservation(String status,String reference,BookingQuote quote) {}
    private Reservation reservation(Checkout c,boolean locked){return jdbc.sql("select status,reference,input::text from mock_supplier_reservation where checkout_id=:id"+(locked?" for update":"")).param("id",c.id()).query((r,n)->new Reservation(r.getString(1),r.getString(2),json.treeToValue(json.readTree(r.getString(3)).get("quote"),BookingQuote.class))).optional().orElseThrow(()->new DomainFailure(DomainFailure.Code.BOOKING_CONFLICT,"Supplier reservation unavailable."));}
    public void verifyCancellable(Checkout c){var r=reservation(c,false);if(!matches(c,r)||!r.status().equals("CONFIRMED"))throw new DomainFailure(DomainFailure.Code.BOOKING_CONFLICT,"Supplier cancellation eligibility changed.");}
    private boolean matches(Checkout c,Reservation r){return Set.of("sample-aster","sample-horizon").contains(c.quote().terms().supplier())&&r.reference().equals(c.supplierReference())&&r.quote().equals(c.quote());}
    private Optional<Cancellation.SupplierReceipt> receipt(UUID id){return jdbc.sql("select receipt::text from mock_customer_cancellation where operation_id=:id").param("id",id).query(String.class).optional().map(s->json.readValue(s,Cancellation.SupplierReceipt.class));}
    public Optional<Cancellation.SupplierReceipt> lookup(UUID id){return tx.execute(t->{lock(id);return receipt(id);});}
    public Cancellation.SupplierReceipt cancel(Cancellation c){
        var result=tx.execute(t->{
            lock(c.operationId());String request=json.writeValueAsString(c.quote());var known=receipt(c.operationId());if(known.isPresent()){
                String prior=jdbc.sql("select request::text from mock_customer_cancellation where operation_id=:id").param("id",c.operationId()).query(String.class).single();if(!json.readTree(prior).equals(json.readTree(request)))throw new IllegalArgumentException("Cancellation operation key reused");return known.get();
            }
            var checkout=c.quote().checkout();var r=reservation(checkout,true);boolean accepted=c.quote().expiresAt().isAfter(clock.instant())&&matches(checkout,r)&&r.status().equals("CONFIRMED");
            var expected=MockCancellationPolicy.parse(r.quote().terms().cancellationPolicy()).refund(r.quote().terms().total().amount());accepted=accepted&&expected.compareTo(c.quote().refundAmount().amount())==0;
            if(accepted)jdbc.sql("update mock_supplier_reservation set status='CANCELLED' where checkout_id=:id and status='CONFIRMED'").param("id",checkout.id()).update();
            var out=new Cancellation.SupplierReceipt(c.operationId(),checkout.id(),accepted,c.quote().refundAmount(),c.quote().retainedAmount(),r.reference(),"MOCK");
            jdbc.sql("insert into mock_customer_cancellation(operation_id,checkout_id,request,receipt) values(:id,:checkout,cast(:request as jsonb),cast(:receipt as jsonb))").param("id",c.operationId()).param("checkout",checkout.id()).param("request",request).param("receipt",json.writeValueAsString(out)).update();return out;
        });
        if(c.scenario()==Cancellation.Scenario.CANCEL_RESPONSE_LOST)throw new IllegalStateException("Simulated cancellation response loss");return result;
    }
}
