package com.travbook.payments.adapter.out.persistence;
import com.travbook.payments.application.port.in.PaymentOperations.*;
import com.travbook.payments.application.port.out.PaymentStore;
import com.travbook.payments.domain.model.Payment;
import com.travbook.shared.domain.DomainFailure;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.sql.*;
import java.time.*;
import java.util.*;
public final class JdbcMockPayments implements PaymentStore {
    private final JdbcClient jdbc;private final TransactionTemplate tx;private final ObjectMapper json;private final Clock clock;
    public JdbcMockPayments(JdbcClient jdbc,TransactionTemplate tx,ObjectMapper json,Clock clock){this.jdbc=jdbc;this.tx=tx;this.json=json;this.clock=clock;}
    private void lock(UUID id){jdbc.sql("select pg_advisory_xact_lock(hashtextextended(:id,0))").param("id",id.toString()).query(Object.class).single();}
    public Optional<Current> current(UUID id){return tx.execute(t->jdbc.sql("select p.*,coalesce((select sum(r.amount) from payment_refund r where r.payment_id=p.id),0) as committed,coalesce((select sum(r.amount) from payment_refund r where r.payment_id=p.id and r.status='SUCCEEDED'),0) as settled from payment_account p where p.id=:id").param("id",id).query((r,n)->new Current(r.getObject("id",UUID.class),r.getObject("customer_id",UUID.class),Payment.State.valueOf(r.getString("status")),r.getBigDecimal("amount"),r.getString("currency"),r.getString("mode"),r.getString("reference"),r.getBigDecimal("committed"),r.getBigDecimal("settled"))).optional());}
    public Optional<Receipt> lookup(UUID id){return tx.execute(t->{lock(id);return receipt(id);});}
    private Optional<Receipt> receipt(UUID id){return jdbc.sql("select receipt::text from payment_operation where operation_id=:id").param("id",id).query(String.class).optional().map(s->json.readValue(s,Receipt.class));}
    public Receipt apply(Command c){return tx.execute(t->{
        lock(c.operationId());var prior=receipt(c.operationId());if(prior.isPresent())return replay(c,prior.get());
        if(c.operation()==Payment.Operation.AUTHORIZE){
            if(!c.validUntil().isAfter(clock.instant()))throw new DomainFailure(DomainFailure.Code.QUOTE_EXPIRED,"Authorization window expired.");
            jdbc.sql("insert into payment_account(id,customer_id,amount,currency,mode,status,valid_until,reference) values(:id,:owner,:amount,:currency,'MOCK',:status,:until,:ref) on conflict(id) do nothing")
                .param("id",c.paymentId()).param("owner",c.customerId()).param("amount",c.amount()).param("currency",c.currency()).param("status",c.simulateDecline()?"DECLINED":"AUTHORIZED")
                .param("until",Timestamp.from(c.validUntil())).param("ref","MOCK-PAY-"+c.paymentId()).update();
        }
        var p=jdbc.sql("select * from payment_account where id=:id for update").param("id",c.paymentId()).query((rs,n)->new Payment(rs.getObject("id",UUID.class),rs.getObject("customer_id",UUID.class),rs.getBigDecimal("amount"),rs.getString("currency"),Payment.State.valueOf(rs.getString("status")),rs.getTimestamp("valid_until").toInstant(),rs.getString("reference"),rs.getLong("version"))).optional().orElseThrow(()->new IllegalStateException("Missing payment authorization"));
        prior=receipt(c.operationId());if(prior.isPresent())return replay(c,prior.get());
        if(!p.customerId().equals(c.customerId())||p.amount().compareTo(c.amount())!=0||!p.currency().equals(c.currency()))throw new IllegalArgumentException("Payment binding mismatch");
        Payment next=switch(c.operation()){case AUTHORIZE->p;case CAPTURE->p.capture(c.simulateDecline(),clock.instant());case VOID->p.voidAuthorization();};
        boolean accepted=switch(c.operation()){case AUTHORIZE->next.state()==Payment.State.AUTHORIZED;case CAPTURE->next.state()==Payment.State.CAPTURED;case VOID->next.state()==Payment.State.VOIDED;};
        if(next!=p)jdbc.sql("update payment_account set status=:status,version=:version where id=:id and version=:previous").param("status",next.state().name()).param("version",next.version()).param("id",p.id()).param("previous",p.version()).update();
        var receipt=new Receipt(c.operationId(),p.id(),next.state(),accepted,p.reference(),p.amount(),p.currency(),"MOCK");
        jdbc.sql("insert into payment_operation(operation_id,payment_id,operation,request,receipt) values(:id,:payment,:op,cast(:request as jsonb),cast(:receipt as jsonb))")
            .param("id",c.operationId()).param("payment",p.id()).param("op",c.operation().name()).param("request",json.writeValueAsString(c)).param("receipt",json.writeValueAsString(receipt)).update();
        if(c.operation()==Payment.Operation.CAPTURE&&accepted){
            for(String account:List.of("MOCK_CLEARING","BOOKING_PAYABLE"))jdbc.sql("insert into payment_journal(id,operation_id,payment_id,account,direction,amount,currency) values(:id,:op,:payment,:account,:direction,:amount,:currency)")
                .param("id",UUID.randomUUID()).param("op",c.operationId()).param("payment",p.id()).param("account",account).param("direction",account.equals("MOCK_CLEARING")?"DEBIT":"CREDIT").param("amount",p.amount()).param("currency",p.currency()).update();
        }
        return receipt;
    });}
    private Receipt replay(Command command,Receipt receipt){
        var original=jdbc.sql("select request::text from payment_operation where operation_id=:id").param("id",command.operationId()).query(String.class).single();
        if(!json.readValue(original,Command.class).equals(command))throw new IllegalArgumentException("Payment operation key reused");return receipt;
    }
}
