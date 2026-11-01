package com.travbook.customer.adapter.out.persistence;

import com.travbook.customer.application.port.in.CustomerProfiles.Saved;
import com.travbook.customer.application.port.out.ProfileStore;
import com.travbook.customer.domain.model.*;
import com.travbook.shared.domain.DomainFailure;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

public final class JdbcProfiles implements ProfileStore {
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    public JdbcProfiles(JdbcClient jdbc, TransactionTemplate tx) { this.jdbc=jdbc; this.tx=tx; }
    public Profile get(UUID owner) {
        return jdbc.sql("select version,display_name,email,phone,updated_at from customer_profile where customer_id=:owner")
            .param("owner",owner).query((r,n)->new Profile(owner,r.getLong("version"),
                new Profile.Fields(r.getString("display_name"),r.getString("email"),r.getString("phone")),r.getTimestamp("updated_at").toInstant()))
            .optional().orElseGet(()->Profile.empty(owner));
    }
    private record Receipt(String fingerprint,long version) {}
    public Saved save(UUID owner, ProfileChange change, String key, UUID correlation, Instant now) {
        return Objects.requireNonNull(tx.execute(status -> {
            jdbc.sql("select id from customer_account where id=:owner for update").param("owner",owner).query(UUID.class).single();
            var receipt=jdbc.sql("select fingerprint,applied_version from customer_profile_command where customer_id=:owner and command_key=:key")
                .param("owner",owner).param("key",key).query((r,n)->new Receipt(r.getString("fingerprint"),r.getLong("applied_version"))).optional();
            var current=get(owner);
            if (receipt.isPresent()) {
                if (!receipt.get().fingerprint().equals(change.fingerprint()))
                    throw new DomainFailure(DomainFailure.Code.IDEMPOTENCY_CONFLICT,"This request key was already used for different profile details.");
                return new Saved(receipt.get().version(),true,current);
            }
            var next=current.change(change.expectedVersion(),change.fields(),now);
            jdbc.sql("""
                insert into customer_profile(customer_id,version,display_name,email,phone,updated_at)
                values(:owner,:version,:name,:email,:phone,:now)
                on conflict(customer_id) do update set version=excluded.version,display_name=excluded.display_name,
                  email=excluded.email,phone=excluded.phone,updated_at=excluded.updated_at
                """).param("owner",owner).param("version",next.version()).param("name",next.fields().displayName())
                .param("email",next.fields().email()).param("phone",next.fields().phone()).param("now",Timestamp.from(now)).update();
            var action=UUID.randomUUID();
            jdbc.sql("insert into customer_profile_command(id,customer_id,command_key,fingerprint,applied_version,created_at) values(:id,:owner,:key,:hash,:version,:now)")
                .param("id",action).param("owner",owner).param("key",key).param("hash",change.fingerprint()).param("version",next.version()).param("now",Timestamp.from(now)).update();
            jdbc.sql("insert into customer_profile_audit(id,customer_id,profile_version,changed_fields,correlation_id,occurred_at) values(:id,:owner,:version,:fields,:correlation,:now)")
                .param("id",action).param("owner",owner).param("version",next.version()).param("fields",String.join(",",current.changedFields(next.fields())))
                .param("correlation",correlation).param("now",Timestamp.from(now)).update();
            return new Saved(next.version(),false,next);
        }));
    }
}
