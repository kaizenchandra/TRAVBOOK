package com.travbook;
import com.travbook.booking.application.port.in.*;
import com.travbook.booking.application.port.out.*;
import com.travbook.booking.application.usecase.BookingEventDispatcher;
import com.travbook.booking.domain.event.*;
import com.travbook.booking.domain.model.*;
import com.travbook.customer.application.port.in.Customers;
import com.travbook.shared.domain.CustomerIdentity;
import com.travbook.support.TestIssuer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static com.travbook.booking.application.port.in.DispatchBookingEvents.Outcome.*;
@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"travbook.booking.worker-enabled=false","travbook.booking.events-enabled=false"})
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class BookingEventIntegrationTest {
    static final TestIssuer issuer=new TestIssuer();
    @Container static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);r.add("travbook.oidc-issuer",issuer::url);r.add("travbook.local-identity",()->true);}
    @Autowired JdbcClient jdbc;@Autowired ObjectMapper json;@Autowired BookingStore bookings;@Autowired BookingEventOutbox outbox;
    @Autowired BookingEventDelivery delivery;@Autowired DispatchBookingEvents dispatcher;@Autowired Customers customers;
    @LocalServerPort int port;@TempDir Path temp;UUID owner;String auth;
    @BeforeEach void setup()throws Exception{
        // Isolated Testcontainer only; preserve application data outside these test fixtures.
        jdbc.sql("truncate mock_customer_cancellation,booking_aftercare_outbox,booking_cancellation_work,booking_cancellation_command,booking_cancellation,booking_cancellation_quote,booking_checkout_outbox,booking_checkout_work,booking_checkout_command,booking_checkout,booking_delivery_replay,booking_timeline_event,booking_event_delivery,booking_outbox,booking_command,booking_reservation,booking_workflow,booking_intent").update();
        String subject="event-owner-"+UUID.randomUUID();owner=customers.resolve(new CustomerIdentity(issuer.url(),subject)).id();auth=issuer.token(subject,"bookings:read");
    }
    @AfterAll static void close(){issuer.close();}
    Booking fixture(){
        var departure=OffsetDateTime.now(ZoneOffset.ofHoursMinutes(5,30)).plusDays(2);
        var terms=new FlightTerms("sample-aster","mock-fixture","Aster Air",List.of(new FlightTerms.Leg("DEL","BOM",departure,departure.plusHours(2),"Asia/Kolkata",120,0,"MOCK-101")),1,"ECONOMY","Fixture baggage","Fixture policy",new FlightTerms.Money(new BigDecimal("4000.00"),"INR"),new FlightTerms.Money(new BigDecimal("600.00"),"INR"));
        var q=new BookingQuote(UUID.randomUUID(),1,owner,UUID.randomUUID(),UUID.randomUUID(),"MOCK",Instant.now().plusSeconds(120),1,0,0,terms);
        return bookings.create(new Booking(UUID.randomUUID(),q,List.of(new Traveller("Alex","Example",LocalDate.of(1990,1,1))),"a".repeat(64),Instant.now(),Instant.now(),1,Booking.Status.CREATED,null,UUID.randomUUID(),UUID.randomUUID()),UUID.randomUUID().toString());
    }
    void validate(){var claim=bookings.claim(Duration.ofSeconds(15)).orElseThrow();bookings.beginRead(claim,4);assertThat(bookings.finish(claim,Booking.Decision.VALIDATED)).isTrue();}
    int count(String table){return jdbc.sql("select count(*) from "+table).query(Integer.class).single();}
    int published(){return jdbc.sql("select count(*) from booking_outbox where published_at is not null").query(Integer.class).single();}
    void expire(UUID id){jdbc.sql("update booking_event_delivery set lease_until=clock_timestamp()-interval '1 second' where event_id=:id").param("id",id).update();}
    void due(){jdbc.sql("update booking_event_delivery set next_attempt_at=clock_timestamp() where status='RETRY'").update();}
    void deliver(BookingEventOutbox.Claim c){delivery.deliver(c.eventId(),c.aggregateId(),c.version(),c.envelope());}
    HttpResponse<String> get(UUID id,String token,String query)throws Exception{
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/bookings/"+id+"/timeline"+query)).GET();
        if(token!=null)request.header("Authorization","Bearer "+token);
        return HttpClient.newHttpClient().send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void ownedPaginatedHistoryExposesLagAndNoTravellerOrDiagnosticData()throws Exception{
        var b=fixture();validate();assertThat(count("booking_event_delivery")).isEqualTo(3);
        var pending=get(b.id(),auth,"");assertThat(pending.statusCode()).isEqualTo(200);assertThat(json.readTree(pending.body()).get("deliveryState").asString()).isEqualTo("PENDING");
        for(int i=0;i<3;i++)assertThat(dispatcher.runNext()).isEqualTo(DELIVERED);assertThat(dispatcher.runNext()).isEqualTo(IDLE);
        assertThat(published()).isEqualTo(3);assertThat(count("booking_timeline_event")).isEqualTo(3);
        var page=json.readTree(get(b.id(),auth,"?size=2").body());assertThat(page.get("items").size()).isEqualTo(2);assertThat(page.get("nextAfterVersion").asInt()).isEqualTo(2);
        assertThat(page.get("deliveryState").asString()).isEqualTo("CAUGHT_UP");assertThat(page.get("deliveredVersion").asInt()).isEqualTo(3);
        var last=get(b.id(),auth,"?size=2&afterVersion=2");assertThat(json.readTree(last.body()).get("items").get(0).get("eventType").asString()).isEqualTo("BookingAwaitingPayment");
        assertThat(last.body()).doesNotContain("Alex","Example","1990-01-01","correlationId","causationId",owner.toString());
        assertThat(get(b.id(),null,"").statusCode()).isEqualTo(401);assertThat(get(b.id(),issuer.token("other","bookings:read"),"").statusCode()).isEqualTo(404);
        assertThat(get(b.id(),issuer.token("restricted","quotes:read"),"").statusCode()).isEqualTo(403);
        for(String invalid:List.of("?size=0","?size=51","?afterVersion=-1","?afterVersion=4"))assertThat(get(b.id(),auth,invalid).statusCode()).isEqualTo(422);
        assertThat(get(b.id(),auth,"").headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    }
    @Test void concurrentConsumersDeduplicateAndRejectConflictingOrOutOfOrderEvents()throws Exception{
        fixture();validate();var c=outbox.claim(Duration.ofSeconds(15)).orElseThrow();assertThat(outbox.acknowledge(c)).isFalse();
        try(var pool=Executors.newFixedThreadPool(6)){
            var calls=new ArrayList<Callable<Void>>();for(int i=0;i<6;i++)calls.add(()->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();deliver(c);return null;});
            for(var task:pool.invokeAll(calls))task.get();
        }
        assertThat(count("booking_timeline_event")).isEqualTo(1);assertThat(published()).isZero();
        var conflicting=json.readTree(c.envelope()).deepCopy();((tools.jackson.databind.node.ObjectNode)conflicting).put("correlationId",UUID.randomUUID().toString());
        assertThatThrownBy(()->delivery.deliver(c.eventId(),c.aggregateId(),c.version(),conflicting.toString())).isInstanceOf(EventRejected.class).hasMessage("EVENT_CONFLICT");
        var third=jdbc.sql("select envelope::text from booking_outbox where aggregate_version=3").query(String.class).single();var e=json.readValue(third,BookingLifecycleEvent.class);
        assertThatThrownBy(()->delivery.deliver(e.eventId(),e.aggregateId(),e.aggregateVersion(),third)).isInstanceOf(EventRejected.class).hasMessage("ORDER_GAP");
        assertThat(count("booking_timeline_event")).isEqualTo(1);assertThat(outbox.acknowledge(c)).isTrue();
        assertThat(dispatcher.runNext()).isEqualTo(DELIVERED);assertThat(dispatcher.runNext()).isEqualTo(DELIVERED);
        assertThatThrownBy(()->jdbc.sql("delete from booking_timeline_event where event_id=:id").param("id",c.eventId()).update()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void claimsAreOrderedFencedAndAnAcknowledgementLossIsSafe()throws Exception{
        fixture();validate();var a=outbox.claim(Duration.ofSeconds(15)).orElseThrow();
        assertThat(outbox.claim(Duration.ofSeconds(15))).isEmpty();deliver(a);expire(a.eventId());var b=outbox.claim(Duration.ofSeconds(15)).orElseThrow();
        assertThat(b.eventId()).isEqualTo(a.eventId());assertThat(b.attempt()).isEqualTo(2);assertThat(outbox.acknowledge(a)).isFalse();assertThat(outbox.fail(a,"OLD_WORKER",true)).isFalse();
        deliver(b);assertThat(outbox.acknowledge(b)).isTrue();assertThat(outbox.acknowledge(b)).isFalse();assertThat(count("booking_timeline_event")).isEqualTo(1);
        try(var pool=Executors.newFixedThreadPool(8)){
            var calls=new ArrayList<Callable<DispatchBookingEvents.Outcome>>();for(int i=0;i<8;i++)calls.add(()->dispatcher.runNext());for(var task:pool.invokeAll(calls))task.get();
        }
        for(int i=0;i<3;i++)if(dispatcher.runNext()==IDLE)break; // Finish successors not eligible during the concurrent claims.
        assertThat(count("booking_timeline_event")).isEqualTo(3);assertThat(published()).isEqualTo(3);
    }
    @Test void retriesAreBoundedAndQuarantineBlocksOnlyItsOwnAggregateWithAuditedReplay()throws Exception{
        var broken=fixture();validate();var failed=new BookingEventDispatcher(outbox,(id,aggregate,version,body)->{throw new IllegalStateException("Sensitive exception must not be stored");},Duration.ofSeconds(15));
        for(int i=1;i<=5;i++){due();assertThat(failed.runNext()).isEqualTo(i==5?DEAD:RETRY);}
        assertThat(failed.runNext()).isEqualTo(IDLE);assertThat(published()).isZero();
        assertThat(dispatcher.health().pending()).isEqualTo(3);assertThat(dispatcher.health().quarantined()).isEqualTo(1);
        assertThat(json.readTree(get(broken.id(),auth,"").body()).get("deliveryState").asString()).isEqualTo("ATTENTION_REQUIRED");
        fixture();assertThat(dispatcher.runNext()).isEqualTo(DELIVERED);assertThat(published()).isEqualTo(1);
        UUID id=jdbc.sql("select event_id from booking_event_delivery where status='DEAD'").query(UUID.class).single();
        assertThatThrownBy(()->jdbc.sql("select replay_booking_event(:id,'operator','short')").param("id",id).query(Object.class).single()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.sql("select attempts from booking_event_delivery where event_id=:id").param("id",id).query(Integer.class).single()).isEqualTo(5);
        jdbc.sql("select replay_booking_event(:id,'test-operator','Resolved local consumer availability')").param("id",id).query(Object.class).single();
        assertThat(count("booking_delivery_replay")).isEqualTo(1);for(int i=0;i<3;i++)assertThat(dispatcher.runNext()).isEqualTo(DELIVERED);
        assertThat(count("booking_timeline_event")).isEqualTo(4);assertThat(published()).isEqualTo(4);
        assertThat(dispatcher.health().pending()).isZero();
        assertThatThrownBy(()->jdbc.sql("update booking_delivery_replay set actor='changed'").update()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.sql("select coalesce(failure_code,'') from booking_event_delivery").query(String.class).list().toString()).doesNotContain("Sensitive");
    }
    @Test void invalidSchemaModeUnknownFieldsAndMetadataAreQuarantinedWithoutPublication(){
        for(String mutation:List.of("version","mode","extra","metadata","coercion")){
            fixture();var worker=new BookingEventDispatcher(outbox,(id,aggregate,version,body)->{
                var node=(tools.jackson.databind.node.ObjectNode)json.readTree(body);
                switch(mutation){case "version"->node.put("schemaVersion",99);case "mode"->node.put("mode","LIVE");case "extra"->node.put("travellerName","not allowed");case "coercion"->node.put("schemaVersion","1");default->node.put("aggregateId",UUID.randomUUID().toString());}
                delivery.deliver(id,aggregate,version,node.toString());
            },Duration.ofSeconds(15));
            assertThat(worker.runNext()).isEqualTo(DEAD);
        }
        assertThat(count("booking_timeline_event")).isZero();assertThat(published()).isZero();
        assertThat(jdbc.sql("select failure_code from booking_event_delivery").query(String.class).list()).containsOnly("INVALID_ENVELOPE");
    }
    @Test void crashesBeforeDeliveryConsumeThePersistedBudget(){
        fixture();for(int i=1;i<=5;i++){var c=outbox.claim(Duration.ofSeconds(15)).orElseThrow();assertThat(c.attempt()).isEqualTo(i);expire(c.eventId());}
        assertThat(dispatcher.runNext()).isEqualTo(DEAD);assertThat(dispatcher.runNext()).isEqualTo(IDLE);assertThat(count("booking_timeline_event")).isZero();
        assertThat(jdbc.sql("select attempts from booking_event_delivery").query(Integer.class).single()).isEqualTo(5);
    }
    @Test void actualProcessCrashAfterConsumerCommitRecoversWithoutDuplicateHistory()throws Exception{
        fixture();assertThat(child("crash")).isEqualTo(24);assertThat(count("booking_timeline_event")).isEqualTo(1);assertThat(published()).isZero();
        UUID id=jdbc.sql("select event_id from booking_event_delivery").query(UUID.class).single();expire(id); // Simulate elapsed downtime on isolated DB.
        assertThat(child("resume")).isZero();assertThat(count("booking_timeline_event")).isEqualTo(1);assertThat(published()).isEqualTo(1);
        assertThat(jdbc.sql("select attempts from booking_event_delivery").query(Integer.class).single()).isEqualTo(2);
    }
    @Test void acknowledgementTransactionFailureCannotPublishOrLoseTheCommittedReceipt(){
        fixture();
        jdbc.sql("create function test_reject_publication() returns trigger language plpgsql as $$ begin raise exception 'injected ack failure'; end; $$").update();
        jdbc.sql("create trigger test_ack_failure before update on booking_outbox for each row execute function test_reject_publication()").update();
        try{
            assertThatThrownBy(()->dispatcher.runNext()).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(count("booking_timeline_event")).isEqualTo(1);assertThat(published()).isZero();
            assertThat(jdbc.sql("select status from booking_event_delivery").query(String.class).single()).isEqualTo("RUNNING");
        }finally{jdbc.sql("drop trigger test_ack_failure on booking_outbox").update();jdbc.sql("drop function test_reject_publication()").update();}
        UUID id=jdbc.sql("select event_id from booking_event_delivery").query(UUID.class).single();expire(id);
        assertThat(dispatcher.runNext()).isEqualTo(DELIVERED);assertThat(count("booking_timeline_event")).isEqualTo(1);assertThat(published()).isEqualTo(1);
    }
    int child(String phase)throws Exception{
        Path log=temp.resolve(phase+".log");String cp=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",cp,"com.travbook.support.BookingEventProcess",phase).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("DATABASE_URL",postgres.getJdbcUrl());builder.environment().put("DATABASE_USERNAME",postgres.getUsername());builder.environment().put("DATABASE_PASSWORD",postgres.getPassword());
        var process=builder.start();boolean exited=process.waitFor(30,TimeUnit.SECONDS);if(!exited)process.destroyForcibly();assertThat(exited).isTrue();
        int code=process.exitValue();if(code!=0&&code!=24)throw new AssertionError(Files.readString(log));return code;
    }
}
