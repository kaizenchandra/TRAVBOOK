package com.travbook.notifications.adapter.out.persistence;
import com.travbook.notifications.application.port.out.NotificationGateway;
import com.travbook.notifications.domain.model.Delivery;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.*;
import java.util.Optional;
public final class MockNotificationGateway implements NotificationGateway {
    private final JdbcClient jdbc;private final TransactionTemplate tx;
    public MockNotificationGateway(JdbcClient jdbc,TransactionTemplate tx){this.jdbc=jdbc;this.tx=tx;}
    private void independent(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Notification provider operation cannot run inside an orchestration transaction");}
    public Optional<String> lookup(Delivery d){independent();return jdbc.sql("select fingerprint,reference from notification_provider_receipt where delivery_id=:id").param("id",d.id()).query((r,n)->{if(!r.getString(1).equals(d.fingerprint()))throw new IllegalStateException("Notification receipt binding mismatch");return r.getString(2);}).optional();}
    public String deliver(Delivery d){independent();tx.executeWithoutResult(t->jdbc.sql("insert into notification_provider_receipt(delivery_id,fingerprint,reference) values(:id,:fingerprint,:reference) on conflict do nothing").param("id",d.id()).param("fingerprint",d.fingerprint()).param("reference","MOCK-NOTIFICATION-"+d.id()).update());return lookup(d).orElseThrow();}
}
