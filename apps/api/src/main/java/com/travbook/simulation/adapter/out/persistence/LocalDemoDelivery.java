package com.travbook.simulation.adapter.out.persistence;
import com.travbook.simulation.application.port.out.DemoDelivery;
import com.travbook.simulation.domain.model.DemoOperation;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.*;
import java.time.Clock;
import java.sql.Timestamp;
/** Separate durable local provider receipt. There is intentionally no network delivery. */
public final class LocalDemoDelivery implements DemoDelivery {
    private final JdbcClient jdbc;private final TransactionTemplate tx;private final Clock clock;
    public LocalDemoDelivery(JdbcClient jdbc,TransactionTemplate tx,Clock clock){this.jdbc=jdbc;this.tx=tx;this.clock=clock;}
    private void outside(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Provider calls must be outside orchestration transactions");}
    public boolean accepted(DemoOperation o){outside();return Boolean.TRUE.equals(tx.execute(t->receipt(o)));}
    private boolean receipt(DemoOperation o){var found=jdbc.sql("select fingerprint from demo_delivery_receipt where operation_id=:id").param("id",o.id()).query(String.class).optional();
        if(found.isPresent()&&!found.get().equals(o.fingerprint()))throw new IllegalStateException("Simulation provider binding mismatch");return found.isPresent();}
    public DemoOperation.Outcome deliver(DemoOperation o){
        outside();return tx.execute(t->{
            if(receipt(o))return DemoOperation.Outcome.ACCEPTED;
            if(o.scenario()==DemoOperation.Scenario.FAIL||o.scenario()==DemoOperation.Scenario.RETRY_ONCE&&o.attempts()==1)return DemoOperation.Outcome.TRANSIENT;
            jdbc.sql("insert into demo_delivery_receipt(operation_id,fingerprint,accepted_at) values(:id,:fingerprint,:now) on conflict(operation_id) do nothing")
                .param("id",o.id()).param("fingerprint",o.fingerprint()).param("now",Timestamp.from(clock.instant())).update();
            if(!receipt(o))throw new IllegalStateException("Simulation receipt missing");return DemoOperation.Outcome.ACCEPTED;
        });
    }
}
