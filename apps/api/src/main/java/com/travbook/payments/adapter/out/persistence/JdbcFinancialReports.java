package com.travbook.payments.adapter.out.persistence;
import com.travbook.payments.application.port.out.FinancialReportStore;
import com.travbook.payments.domain.model.FinancialSnapshot;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.UUID;
/** One PostgreSQL statement gives one consistent, owner-scoped MVCC snapshot. */
public final class JdbcFinancialReports implements FinancialReportStore {
    private final JdbcClient jdbc;private final TransactionTemplate tx;
    public JdbcFinancialReports(JdbcClient jdbc,TransactionTemplate tx){this.jdbc=jdbc;this.tx=tx;}
    public FinancialSnapshot read(UUID owner,Instant now){return tx.execute(t->jdbc.sql("""
        with accounts as (select * from payment_account where customer_id=:owner and mode='MOCK'),
        captures as (select j.payment_id,count(*) n,sum(case when j.direction='DEBIT' then j.amount else -j.amount end) net,
          min(j.amount) lo,max(j.amount) hi from payment_journal j join accounts a on a.id=j.payment_id group by j.payment_id),
        refunds as (select r.* from payment_refund r join accounts a on a.id=r.payment_id),
        refund_posts as (select j.operation_id,count(*) n,sum(case when j.direction='DEBIT' then j.amount else -j.amount end) net,
          min(j.amount) lo,max(j.amount) hi from payment_refund_journal j join refunds r on r.operation_id=j.operation_id group by j.operation_id)
        select
          (select coalesce(sum(j.amount),0) from payment_journal j join accounts a on a.id=j.payment_id where j.direction='DEBIT') captured,
          (select coalesce(sum(j.amount),0) from payment_refund_journal j join refunds r on r.operation_id=j.operation_id where j.direction='CREDIT') refunded,
          (select coalesce(sum(amount),0) from refunds where status='PENDING') pending,
          (select coalesce(sum(amount),0) from accounts where status='AUTHORIZED') authorized,
          (select count(*) from accounts) accounts,
          (select count(*) from accounts where status='CAPTURED') captures,
          (select count(*) from refunds where status='SUCCEEDED') refunds,
          (select count(*) from accounts a left join captures j on j.payment_id=a.id
            where (a.status='CAPTURED' and (coalesce(j.n,0)<>2 or j.net<>0 or j.lo<>a.amount or j.hi<>a.amount))
              or (a.status<>'CAPTURED' and j.payment_id is not null)) +
          (select count(*) from refunds r left join refund_posts j on j.operation_id=r.operation_id
            where (r.status='SUCCEEDED' and (coalesce(j.n,0)<>2 or j.net<>0 or j.lo<>r.amount or j.hi<>r.amount))
              or (r.status<>'SUCCEEDED' and j.operation_id is not null)) +
          (select count(*) from refunds r join accounts a on a.id=r.payment_id where r.customer_id<>a.customer_id or r.currency<>a.currency) +
          (select count(*) from accounts a where (select coalesce(sum(r.amount),0) from refunds r where r.payment_id=a.id)>a.amount) issues
        """).param("owner",owner).query((r,n)->new FinancialSnapshot(r.getBigDecimal("captured"),r.getBigDecimal("refunded"),r.getBigDecimal("pending"),r.getBigDecimal("authorized"),r.getLong("accounts"),r.getLong("captures"),r.getLong("refunds"),r.getLong("issues"),now)).single());}
}
