package com.travbook.customer.adapter.out.persistence;
import com.travbook.customer.domain.model.TravelOrganizer;
import com.travbook.customer.application.port.in.CustomerOrganizer.Saved;
import com.travbook.customer.application.port.out.OrganizerStore;
import com.travbook.shared.domain.DomainFailure;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
public final class JdbcOrganizer implements OrganizerStore {
    private final JdbcClient jdbc;private final TransactionTemplate tx;private final ObjectMapper json;
    public JdbcOrganizer(JdbcClient jdbc,TransactionTemplate tx,ObjectMapper json){this.jdbc=jdbc;this.tx=tx;this.json=json;}
    public TravelOrganizer get(UUID owner){return jdbc.sql("select payload::text from customer_organizer where customer_id=:owner").param("owner",owner).query(String.class).optional().map(s->json.readValue(s,TravelOrganizer.class)).orElseGet(()->TravelOrganizer.empty(owner));}
    public Saved save(UUID owner,long version,TravelOrganizer.Fields fields,String key,UUID correlation,Instant now){return Objects.requireNonNull(tx.execute(t->{
        jdbc.sql("select id from customer_account where id=:owner for update").param("owner",owner).query(UUID.class).single();
        String fingerprint;try{fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((version+"|"+json.writeValueAsString(fields)).getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        var prior=jdbc.sql("select fingerprint,applied_version from organizer_command where customer_id=:owner and command_key=:key").param("owner",owner).param("key",key).query((r,n)->new Object[]{r.getString(1),r.getLong(2)}).optional();
        var current=get(owner);if(prior.isPresent()){if(!fingerprint.equals(prior.get()[0]))throw new DomainFailure(DomainFailure.Code.IDEMPOTENCY_CONFLICT,"This saved-details key was already used for a different request.");return new Saved((Long)prior.get()[1],true,current);}
        var next=current.change(version,fields,now);
        jdbc.sql("insert into customer_organizer(customer_id,version,payload) values(:owner,:version,cast(:payload as jsonb)) on conflict(customer_id) do update set version=excluded.version,payload=excluded.payload")
            .param("owner",owner).param("version",next.version()).param("payload",json.writeValueAsString(next)).update();
        jdbc.sql("insert into organizer_command(customer_id,command_key,fingerprint,applied_version,correlation_id,occurred_at) values(:owner,:key,:hash,:version,:correlation,:at)")
            .param("owner",owner).param("key",key).param("hash",fingerprint).param("version",next.version()).param("correlation",correlation).param("at",java.sql.Timestamp.from(now)).update();
        return new Saved(next.version(),false,next);
    }));}
}
