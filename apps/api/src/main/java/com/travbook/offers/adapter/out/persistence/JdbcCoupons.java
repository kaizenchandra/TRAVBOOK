package com.travbook.offers.adapter.out.persistence;
import com.travbook.offers.application.port.out.CouponStore;
import com.travbook.offers.domain.model.*;
import com.travbook.shared.domain.DomainFailure;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;
public final class JdbcCoupons implements CouponStore {
    private final JdbcClient jdbc;private final TransactionTemplate tx;private final ObjectMapper json;
    public JdbcCoupons(JdbcClient jdbc,TransactionTemplate tx,ObjectMapper json){this.jdbc=jdbc;this.tx=tx;this.json=json;}
    public Coupon find(String code){return jdbc.sql("select payload::text from coupon_campaign where code=:code").param("code",code).query(String.class).optional().map(s->json.readValue(s,Coupon.class)).orElseThrow(()->Coupon.invalid("Coupon code is not recognized."));}
    public List<Coupon> catalog(UUID owner,Instant now){return jdbc.sql("select payload::text from coupon_campaign where (payload->>'enabled')::boolean and (payload->>'startsAt')::timestamptz<=:now and (payload->>'endsAt')::timestamptz>:now and ((payload->>'eligibleCustomer') is null or (payload->>'eligibleCustomer')::uuid=:owner) order by code limit 50").param("now",Timestamp.from(now)).param("owner",owner).query(String.class).list().stream().map(s->json.readValue(s,Coupon.class)).toList();}
    public long count(String code,UUID owner){return jdbc.sql("select count(*) from coupon_redemption where code=:code and (cast(:owner as uuid) is null or owner_id=:owner)").param("code",code).param("owner",owner).query(Long.class).single();}
    public CouponAward save(CouponAward award){
        if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Coupon awards require the quotation unit of work");
        jdbc.sql("insert into coupon_award(id,quote_id,owner_id,code,payload) values(:id,:quote,:owner,:code,cast(:payload as jsonb))").param("id",award.id()).param("quote",award.quoteId()).param("owner",award.owner()).param("code",award.policy().code()).param("payload",json.writeValueAsString(award)).update();return award;
    }
    public void redeem(UUID owner,UUID awardId,UUID reservation,UUID correlation,Instant now){
        if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Coupon redemption must be atomic with the reservation unit of work");
        tx.executeWithoutResult(t->{
            var a=jdbc.sql("select payload::text from coupon_award where id=:id and owner_id=:owner").param("id",awardId).param("owner",owner).query(String.class).optional().map(s->json.readValue(s,CouponAward.class)).orElseThrow(()->new DomainFailure(DomainFailure.Code.NOT_FOUND,"Coupon quotation not found."));
            jdbc.sql("select code from coupon_campaign where code=:code for update").param("code",a.policy().code()).query(String.class).single();
            var prior=jdbc.sql("select reservation_id from coupon_redemption where award_id=:id").param("id",awardId).query(UUID.class).optional();
            if(prior.isPresent()){if(!prior.get().equals(reservation))throw new DomainFailure(DomainFailure.Code.BOOKING_CONFLICT,"Coupon award already redeemed.");return;}
            var current=find(a.policy().code());a.redeem(current,now);current.available(count(current.code(),null),count(current.code(),owner));
            jdbc.sql("insert into coupon_redemption(award_id,reservation_id,owner_id,code,discount,created_at) values(:award,:reservation,:owner,:code,:discount,:at)")
                .param("award",awardId).param("reservation",reservation).param("owner",owner).param("code",current.code()).param("discount",a.discount().decimal()).param("at",Timestamp.from(now)).update();
            var event=UUID.randomUUID();var payload=Map.of("eventId",event,"eventType","CouponRedeemed","schemaVersion",1,"aggregateId",awardId,"aggregateVersion",1,"occurredAt",now,"correlationId",correlation,"causationId",reservation,"producer","offers","payload",Map.of("reservationId",reservation,"code",current.code(),"discount",a.discount(),"mode","MOCK"));
            jdbc.sql("insert into coupon_outbox(id,award_id,envelope) values(:id,:award,cast(:payload as jsonb))").param("id",event).param("award",awardId).param("payload",json.writeValueAsString(payload)).update();
        });
    }
}
