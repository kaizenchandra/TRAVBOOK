package com.travbook.notifications.adapter.out.persistence;
import com.travbook.notifications.application.port.out.NotificationStore;
import com.travbook.notifications.application.port.in.Notifications;
import com.travbook.notifications.domain.model.*;
import com.travbook.shared.domain.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;
public final class JdbcNotificationStore implements NotificationStore {
    private final JdbcClient jdbc;private final TransactionTemplate tx;private final ObjectMapper json;
    public JdbcNotificationStore(JdbcClient jdbc,TransactionTemplate tx,ObjectMapper json){this.jdbc=jdbc;this.tx=tx;this.json=json;}
    private DomainFailure absent(){return new DomainFailure(DomainFailure.Code.NOT_FOUND,"Notification not found.");}
    public void accept(LifecycleSignal signal,boolean email,boolean sms,Instant now){tx.executeWithoutResult(t->{
        jdbc.sql("insert into notification_cursor(source,aggregate_id,version,state_key) values(:source,:id,0,'') on conflict do nothing").param("source",signal.source()).param("id",signal.aggregateId()).update();
        var old=jdbc.sql("select version,state_key from notification_cursor where source=:source and aggregate_id=:id for update").param("source",signal.source()).param("id",signal.aggregateId()).query((r,n)->new Object[]{r.getLong(1),r.getString(2)}).single();
        String stateKey=new TreeMap<>(signal.states()).toString();
        int inserted=jdbc.sql("insert into notification_seen(source,event_id,aggregate_id,version,fingerprint) values(:source,:event,:aggregate,:version,:fingerprint) on conflict do nothing").param("source",signal.source()).param("event",signal.eventId()).param("aggregate",signal.aggregateId()).param("version",signal.version()).param("fingerprint",signal.fingerprint()).update();
        if(inserted==0){var fingerprint=jdbc.sql("select fingerprint from notification_seen where source=:source and event_id=:event").param("source",signal.source()).param("event",signal.eventId()).query(String.class).single();if(!fingerprint.equals(signal.fingerprint()))throw new IllegalStateException("Event identifier reused with different content");return;}
        if(signal.version()<=(Long)old[0])return;
        jdbc.sql("update notification_cursor set version=:version,state_key=:state where source=:source and aggregate_id=:id").param("version",signal.version()).param("state",stateKey).param("source",signal.source()).param("id",signal.aggregateId()).update();
        if(stateKey.equals(old[1]))return;var notice=Notice.from(signal);
        jdbc.sql("insert into customer_notification(id,owner_id,source,event_id,occurred_at,payload) values(:id,:owner,:source,:event,:at,cast(:payload as jsonb))").param("id",notice.id()).param("owner",notice.owner()).param("source",signal.source()).param("event",signal.eventId()).param("at",Timestamp.from(notice.occurredAt())).param("payload",json.writeValueAsString(notice)).update();
        var start=jdbc.sql("select started_at from notification_epoch where id=1").query((r,n)->r.getTimestamp(1).toInstant()).single();
        if(signal.occurredAt().isBefore(start))return;
        for(var channel:Delivery.Channel.values())if(channel==Delivery.Channel.EMAIL?email:sms){var d=new Delivery(UUID.randomUUID(),notice.id(),notice.owner(),channel,Delivery.State.QUEUED,0,1,now,null);
            jdbc.sql("insert into notification_delivery(id,notice_id,owner_id,channel,state,attempts,version,next_at,payload) values(:id,:notice,:owner,:channel,'QUEUED',0,1,:at,cast(:payload as jsonb))").param("id",d.id()).param("notice",d.noticeId()).param("owner",d.owner()).param("channel",channel.name()).param("at",Timestamp.from(now)).param("payload",json.writeValueAsString(d)).update();event(d,now);}
    });}
    private Notice get(UUID owner,UUID id){var notice=jdbc.sql("select payload::text,read_at from customer_notification where owner_id=:owner and id=:id").param("owner",owner).param("id",id).query((r,n)->{
        var p=json.readValue(r.getString(1),Notice.class);var read=r.getTimestamp(2);return new Notice(p.id(),p.owner(),p.bookingId(),p.source(),p.title(),p.body(),p.route(),p.occurredAt(),read==null?null:read.toInstant(),p.templateVersion(),p.mode(),List.of());
    }).optional().orElseThrow(this::absent);return new Notice(notice.id(),notice.owner(),notice.bookingId(),notice.source(),notice.title(),notice.body(),notice.route(),notice.occurredAt(),notice.readAt(),notice.templateVersion(),notice.mode(),deliveries(notice.id()));}
    private List<Notice.DeliveryView> deliveries(UUID notice){return jdbc.sql("select id,channel,state,attempts,payload->>'reference' reference from notification_delivery where notice_id=:notice order by channel").param("notice",notice).query((r,n)->new Notice.DeliveryView(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getInt(4),r.getString(5))).list();}
    public Notifications.Page list(UUID owner,UUID after){if(after!=null)get(owner,after);var ids=jdbc.sql("select id from customer_notification where owner_id=:owner and (cast(:after as uuid) is null or (occurred_at,id)<(select occurred_at,id from customer_notification where owner_id=:owner and id=:after)) order by occurred_at desc,id desc limit 21").param("owner",owner).param("after",after).query(UUID.class).list();var items=ids.subList(0,Math.min(ids.size(),20)).stream().map(id->get(owner,id)).toList();return new Notifications.Page(owner,items,ids.size()>20?items.getLast().id():null);}
    public Notice read(UUID owner,UUID id,Instant now){return Objects.requireNonNull(tx.execute(t->{if(jdbc.sql("update customer_notification set read_at=coalesce(read_at,:now) where owner_id=:owner and id=:id").param("now",Timestamp.from(now)).param("owner",owner).param("id",id).update()==0)throw absent();return get(owner,id);}));}
    public Optional<Claim> claim(Instant now){return Objects.requireNonNull(tx.execute(t->{
        var p=jdbc.sql("select payload::text from notification_delivery where state in ('QUEUED','RUNNING','RETRY') and next_at<=:now and (lease_until is null or lease_until<=:now) order by next_at,id for update skip locked limit 1").param("now",Timestamp.from(now)).query(String.class).optional();if(p.isEmpty())return Optional.empty();var old=json.readValue(p.get(),Delivery.class);var next=old.claim(now);var token=UUID.randomUUID();
        jdbc.sql("update notification_delivery set state='RUNNING',attempts=:attempts,version=:version,payload=cast(:payload as jsonb),lease_token=:token,lease_until=:until where id=:id").param("attempts",next.attempts()).param("version",next.version()).param("payload",json.writeValueAsString(next)).param("token",token).param("until",Timestamp.from(now.plusSeconds(15))).param("id",next.id()).update();event(next,now);return Optional.of(new Claim(next,token,old.attempts()>=3));
    }));}
    public boolean complete(Claim claim,Delivery next,Instant now){return Boolean.TRUE.equals(tx.execute(t->{
        int n=jdbc.sql("update notification_delivery set state=:state,version=:version,next_at=:next,payload=cast(:payload as jsonb),lease_token=null,lease_until=null where id=:id and lease_token=:token and version=:old and lease_until>:now").param("state",next.state().name()).param("version",next.version()).param("next",Timestamp.from(next.nextAt())).param("payload",json.writeValueAsString(next)).param("id",next.id()).param("token",claim.token()).param("old",claim.delivery().version()).param("now",Timestamp.from(now)).update();if(n==1)event(next,now);return n==1;
    }));}
    private void event(Delivery d,Instant now){jdbc.sql("insert into notification_delivery_event(id,delivery_id,version,state,occurred_at) values(:id,:delivery,:version,:state,:at)").param("id",UUID.randomUUID()).param("delivery",d.id()).param("version",d.version()).param("state",d.state().name()).param("at",Timestamp.from(now)).update();}
}
