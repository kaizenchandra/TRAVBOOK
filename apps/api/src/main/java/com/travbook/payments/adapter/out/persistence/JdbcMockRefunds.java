package com.travbook.payments.adapter.out.persistence;
import com.travbook.payments.application.port.in.PaymentRefunds.*;
import com.travbook.payments.application.port.out.RefundStore;
import com.travbook.payments.domain.model.RefundEntitlement;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.sql.*;
import java.time.*;
import java.util.*;
public final class JdbcMockRefunds implements RefundStore {
    private final JdbcClient jdbc;private final TransactionTemplate tx;private final ObjectMapper json;private final Clock clock;private final long leaseMillis;
    public JdbcMockRefunds(JdbcClient jdbc,TransactionTemplate tx,ObjectMapper json,Clock clock){this(jdbc,tx,json,clock,Duration.ofSeconds(15));}
    public JdbcMockRefunds(JdbcClient jdbc,TransactionTemplate tx,ObjectMapper json,Clock clock,Duration lease){if(lease.toMillis()<1||lease.toMillis()>60000)throw new IllegalArgumentException("Invalid settlement lease");this.jdbc=jdbc;this.tx=tx;this.json=json;this.clock=clock;this.leaseMillis=lease.toMillis();}
    private void lock(UUID id){jdbc.sql("select pg_advisory_xact_lock(hashtextextended(:id,0))").param("id",id.toString()).query(Object.class).single();}
    private Receipt map(ResultSet r,int n)throws SQLException{return new Receipt(r.getObject("operation_id",UUID.class),r.getObject("payment_id",UUID.class),r.getBigDecimal("amount"),r.getString("currency"),r.getString("reference"),"SUCCEEDED".equals(r.getString("status")),"MOCK");}
    private Optional<Receipt> receipt(UUID id){return jdbc.sql("select * from payment_refund where operation_id=:id").param("id",id).query(this::map).optional();}
    public Optional<Receipt> lookup(UUID id){return tx.execute(t->{lock(id);return receipt(id);});}
    public Receipt submit(Command c){return tx.execute(t->{
        lock(c.operationId());var existing=receipt(c.operationId());if(existing.isPresent()){
            var original=jdbc.sql("select request::text from payment_refund where operation_id=:id").param("id",c.operationId()).query(String.class).single();
            if(!json.readValue(original,Command.class).equals(c))throw new IllegalArgumentException("Refund operation key reused");return existing.get();
        }
        var captured=jdbc.sql("select amount from payment_account where id=:id and customer_id=:owner and currency=:currency and mode='MOCK' and status='CAPTURED' for update")
            .param("id",c.paymentId()).param("owner",c.customerId()).param("currency",c.currency()).query(java.math.BigDecimal.class).optional().orElseThrow(()->new IllegalArgumentException("Captured owned payment required"));
        var committed=jdbc.sql("select coalesce(sum(amount),0) from payment_refund where payment_id=:id").param("id",c.paymentId()).query(java.math.BigDecimal.class).single();
        RefundEntitlement.requireWithinCapture(c.amount(),captured,committed,c.currency());var now=clock.instant();
        jdbc.sql("insert into payment_refund(operation_id,payment_id,customer_id,entitlement_id,amount,currency,mode,status,reference,request,ready_at,created_at) values(:id,:payment,:owner,:entitlement,:amount,:currency,'MOCK','PENDING',:reference,cast(:request as jsonb),:ready,:now)")
            .param("id",c.operationId()).param("payment",c.paymentId()).param("owner",c.customerId()).param("entitlement",c.entitlementId()).param("amount",c.amount()).param("currency",c.currency()).param("reference","MOCK-REFUND-"+c.operationId())
            .param("request",json.writeValueAsString(c)).param("ready",Timestamp.from(now.plusSeconds(c.simulateDelay()?5:0))).param("now",Timestamp.from(now)).update();
        var r=receipt(c.operationId()).orElseThrow();event(r,1,now);return r;
    });}
    private record Settlement(UUID id,UUID token) {}
    public boolean settleNext(){
        var claim=tx.execute(t->{
            var id=jdbc.sql("select operation_id from payment_refund where status='PENDING' and ready_at<=:now and settlement_attempts<5 and (settlement_lease is null or settlement_lease<clock_timestamp()) order by ready_at,operation_id for update skip locked limit 1").param("now",Timestamp.from(clock.instant())).query(UUID.class).optional();if(id.isEmpty())return Optional.<Settlement>empty();UUID token=UUID.randomUUID();
            jdbc.sql("update payment_refund set settlement_attempts=settlement_attempts+1,settlement_token=:token,settlement_lease=clock_timestamp()+(:lease*interval '1 millisecond') where operation_id=:id").param("token",token).param("lease",leaseMillis).param("id",id.get()).update();return Optional.of(new Settlement(id.get(),token));
        });if(claim.isEmpty())return false;var owned=claim.get();
        return tx.execute(t->{
        var next=jdbc.sql("select * from payment_refund where operation_id=:id and status='PENDING' and settlement_token=:token and settlement_lease>clock_timestamp() for update").param("id",owned.id()).param("token",owned.token()).query(this::map).optional();if(next.isEmpty())return false;var r=next.get();
        jdbc.sql("select id from payment_account where id=:id for update").param("id",r.paymentId()).query(UUID.class).single();
        for(String account:List.of("BOOKING_PAYABLE","MOCK_CLEARING"))jdbc.sql("insert into payment_refund_journal(id,operation_id,account,direction,amount,currency) values(:id,:operation,:account,:direction,:amount,:currency)")
            .param("id",UUID.randomUUID()).param("operation",r.operationId()).param("account",account).param("direction",account.equals("BOOKING_PAYABLE")?"DEBIT":"CREDIT").param("amount",r.amount()).param("currency",r.currency()).update();
        int changed=jdbc.sql("update payment_refund set status='SUCCEEDED',version=version+1,completed_at=:now,settlement_token=null,settlement_lease=null where operation_id=:id and status='PENDING' and settlement_token=:token and settlement_lease>clock_timestamp()")
            .param("now",Timestamp.from(clock.instant())).param("id",r.operationId()).param("token",owned.token()).update();if(changed==0){t.setRollbackOnly();return false;}event(receipt(r.operationId()).orElseThrow(),2,clock.instant());return true;
    });}
    private void event(Receipt r,int version,Instant now){
        var command=json.readValue(jdbc.sql("select request::text from payment_refund where operation_id=:id").param("id",r.operationId()).query(String.class).single(),Command.class);
        UUID id=UUID.randomUUID();var body=new LinkedHashMap<String,Object>(Map.of("eventId",id,"aggregateId",r.operationId(),"aggregateVersion",version,"eventType",r.completed()?"MockRefundCompleted":"MockRefundRequested","schemaVersion",1,"producer","payments","mode","MOCK","occurredAt",now,"correlationId",command.correlationId(),"causationId",command.causationId()));
        body.put("payload",Map.of("paymentId",r.paymentId(),"amount",r.amount().toPlainString(),"currency",r.currency(),"status",r.completed()?"SUCCEEDED":"PENDING"));
        jdbc.sql("insert into payment_refund_outbox(id,aggregate_id,aggregate_version,event_type,mode,envelope) values(:id,:aggregate,:version,:type,'MOCK',cast(:body as jsonb))")
            .param("id",id).param("aggregate",r.operationId()).param("version",version).param("type",body.get("eventType")).param("body",json.writeValueAsString(body)).update();
    }
}
