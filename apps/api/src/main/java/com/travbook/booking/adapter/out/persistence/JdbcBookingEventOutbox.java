package com.travbook.booking.adapter.out.persistence;
import com.travbook.booking.application.port.out.BookingEventOutbox;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Duration;
import java.util.*;
public final class JdbcBookingEventOutbox implements BookingEventOutbox {
    private final JdbcClient jdbc;private final TransactionTemplate tx;
    public JdbcBookingEventOutbox(JdbcClient jdbc,TransactionTemplate tx){this.jdbc=jdbc;this.tx=tx;}
    public Backlog backlog(){
        return jdbc.sql("select count(*) as pending,count(*) filter(where status='DEAD') as dead,coalesce(greatest(0,extract(epoch from clock_timestamp()-min(queued_at))),0) as age from booking_event_delivery where status<>'DELIVERED'")
            .query((rs,n)->new Backlog(rs.getLong("pending"),rs.getLong("dead"),rs.getDouble("age"))).single();
    }
    public Optional<Claim> claim(Duration lease){
        return tx.execute(t->{
            var selected=jdbc.sql("""
                select o.id,o.aggregate_id,o.aggregate_version,o.envelope,d.attempts
                from booking_event_delivery d join booking_outbox o on o.id=d.event_id
                where o.published_at is null and
                  ((d.status in ('READY','RETRY') and d.next_attempt_at<=clock_timestamp()) or (d.status='RUNNING' and d.lease_until<clock_timestamp()))
                  and not exists(select 1 from booking_outbox p where p.aggregate_id=o.aggregate_id and p.aggregate_version<o.aggregate_version and p.published_at is null)
                order by d.next_attempt_at,o.id for update of d skip locked limit 1
                """).query((rs,n)->new Claim(rs.getObject("id",UUID.class),rs.getObject("aggregate_id",UUID.class),rs.getLong("aggregate_version"),rs.getString("envelope"),UUID.randomUUID(),rs.getInt("attempts")+1,rs.getInt("attempts")>=MAX_ATTEMPTS)).optional();
            if(selected.isEmpty())return Optional.empty();var c=selected.get();
            if(c.exhausted()){
                jdbc.sql("update booking_event_delivery set status='DEAD',lease_token=null,lease_until=null,failure_code='ATTEMPTS_EXHAUSTED' where event_id=:id").param("id",c.eventId()).update();
            }else{
                jdbc.sql("update booking_event_delivery set status='RUNNING',attempts=attempts+1,lease_token=:token,lease_until=clock_timestamp()+(:seconds * interval '1 second') where event_id=:id")
                    .param("token",c.token()).param("seconds",lease.toSeconds()).param("id",c.eventId()).update();
            }
            return selected;
        });
    }
    public boolean acknowledge(Claim c){
        return tx.execute(t->{
            int updated=jdbc.sql("update booking_event_delivery set status='DELIVERED',lease_token=null,lease_until=null,failure_code=null where event_id=:id and status='RUNNING' and lease_token=:token and lease_until>clock_timestamp() and exists(select 1 from booking_timeline_event where event_id=:id)")
                .param("id",c.eventId()).param("token",c.token()).update();
            if(updated==0)return false;
            // The independent consumer has committed before this transaction begins.
            jdbc.sql("update booking_outbox set published_at=clock_timestamp() where id=:id and published_at is null").param("id",c.eventId()).update();
            return true;
        });
    }
    public boolean fail(Claim c,String code,boolean permanent){
        boolean dead=permanent||c.attempt()>=MAX_ATTEMPTS;
        long delay=(1L<<Math.min(c.attempt()-1,4))*1000+Math.floorMod(c.eventId().hashCode()+31*c.attempt(),251);
        return jdbc.sql("update booking_event_delivery set status=:status,failure_code=:code,next_attempt_at=clock_timestamp()+(:delay * interval '1 millisecond'),lease_token=null,lease_until=null where event_id=:id and status='RUNNING' and lease_token=:token and lease_until>clock_timestamp()")
            .param("status",dead?"DEAD":"RETRY").param("code",code).param("delay",delay).param("id",c.eventId()).param("token",c.token()).update()==1;
    }
}
