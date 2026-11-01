package com.travbook.trips.adapter.out.persistence;
import com.travbook.trips.application.port.out.DocumentStore;
import com.travbook.trips.domain.model.TripDocument;
import com.travbook.shared.domain.DomainFailure;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.sql.*;
import java.util.*;
public final class JdbcDocuments implements DocumentStore {
    private final JdbcClient jdbc;private final TransactionTemplate tx;private final ObjectMapper json;
    public JdbcDocuments(JdbcClient jdbc,TransactionTemplate tx,ObjectMapper json){this.jdbc=jdbc;this.tx=tx;this.json=json;}
    private TripDocument map(ResultSet rs,int n)throws SQLException{return new TripDocument(rs.getObject("id",UUID.class),rs.getObject("customer_id",UUID.class),rs.getObject("booking_id",UUID.class),rs.getObject("checkout_id",UUID.class),rs.getLong("checkout_version"),rs.getTimestamp("created_at").toInstant(),rs.getString("body"),rs.getString("sha256"));}
    public Optional<TripDocument> replay(UUID owner,String key,UUID bookingId){
        return jdbc.sql("select d.*,k.booking_id as command_booking from trip_document_command k join trip_document d on d.id=k.document_id where k.customer_id=:owner and k.command_key=:key")
            .param("owner",owner).param("key",key).query((rs,n)->{if(!bookingId.equals(rs.getObject("command_booking",UUID.class)))throw new DomainFailure(DomainFailure.Code.IDEMPOTENCY_CONFLICT,"This document key belongs to a different booking.");return map(rs,n);}).optional();
    }
    public TripDocument create(TripDocument d,String key,UUID correlation){return tx.execute(t->{
        var replay=replay(d.customerId(),key,d.bookingId());if(replay.isPresent())return replay.get();
        boolean inserted=jdbc.sql("insert into trip_document(id,customer_id,booking_id,checkout_id,checkout_version,kind,mode,created_at,body,sha256,byte_length) values(:id,:owner,:booking,:checkout,:version,'MOCK_CONFIRMATION_SUMMARY','MOCK',:created,:body,:hash,:length) on conflict(customer_id,booking_id,kind) do nothing")
            .param("id",d.id()).param("owner",d.customerId()).param("booking",d.bookingId()).param("checkout",d.checkoutId()).param("version",d.checkoutVersion()).param("created",Timestamp.from(d.createdAt())).param("body",d.body()).param("hash",d.sha256()).param("length",d.bytes().length).update()==1;
        var current=list(d.customerId(),d.bookingId()).getFirst();
        if(!current.checkoutId().equals(d.checkoutId())||current.checkoutVersion()!=d.checkoutVersion())throw new DomainFailure(DomainFailure.Code.BOOKING_CONFLICT,"A document for a different checkout version already exists.");
        UUID commandId=UUID.randomUUID();
        boolean claimed=jdbc.sql("insert into trip_document_command(customer_id,command_key,booking_id,document_id,command_id) values(:owner,:key,:booking,:id,:command) on conflict do nothing")
            .param("owner",d.customerId()).param("key",key).param("booking",d.bookingId()).param("id",current.id()).param("command",commandId).update()==1;
        if(!claimed)return replay(d.customerId(),key,d.bookingId()).orElseThrow();
        if(inserted){
            UUID eventId=UUID.randomUUID();var e=new LinkedHashMap<String,Object>(Map.of("eventId",eventId,"eventType","MockDocumentCreated","schemaVersion",1,"aggregateId",d.id(),"aggregateVersion",1,"occurredAt",current.createdAt(),"correlationId",correlation,"causationId",commandId,"producer","trips","mode","MOCK"));
            e.put("payload",Map.of("documentId",d.id(),"bookingId",d.bookingId(),"checkoutId",d.checkoutId(),"kind",TripDocument.KIND,"sha256",d.sha256()));
            jdbc.sql("insert into trip_document_outbox(id,document_id,envelope) values(:id,:document,cast(:envelope as jsonb))").param("id",eventId).param("document",d.id()).param("envelope",json.writeValueAsString(e)).update();
        }
        return current;
    });}
    public List<TripDocument> list(UUID owner,UUID bookingId){return jdbc.sql("select * from trip_document where customer_id=:owner and booking_id=:booking order by created_at,id limit 1").param("owner",owner).param("booking",bookingId).query(this::map).list();}
    public Optional<TripDocument> find(UUID owner,UUID bookingId,UUID id){return jdbc.sql("select * from trip_document where customer_id=:owner and booking_id=:booking and id=:id").param("owner",owner).param("booking",bookingId).param("id",id).query(this::map).optional();}
}
