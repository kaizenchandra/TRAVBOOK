package com.travbook;
import com.travbook.booking.application.port.out.*;
import com.travbook.booking.application.usecase.BookingWorker;
import com.travbook.booking.domain.model.*;
import com.travbook.shared.domain.DomainFailure;
import com.travbook.support.TestIssuer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"travbook.booking.worker-enabled=false","travbook.booking.events-enabled=false"})
@Import(BookingIntegrationTest.TimeConfiguration.class)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class BookingIntegrationTest {
    static final TestIssuer issuer=new TestIssuer();
    @Container static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);r.add("travbook.oidc-issuer",issuer::url);r.add("travbook.local-identity",()->true);}
    static class MutableClock extends Clock {volatile Instant now=Instant.now();public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return Clock.fixed(now,zone);}public Instant instant(){return now;}}
    @TestConfiguration static class TimeConfiguration {@Bean @Primary MutableClock bookingTestClock(){return new MutableClock();}}
    @Autowired MutableClock clock;@Autowired ObjectMapper json;@Autowired JdbcClient jdbc;@Autowired BookingStore store;@Autowired BookingValidationPort validation;
    @LocalServerPort int port;@TempDir Path temporary;
    final HttpClient http=HttpClient.newHttpClient();String auth,bob;
    @BeforeEach void setup()throws Exception {
        clock.now=Instant.now();auth=issuer.token("owner-"+UUID.randomUUID(),"quotes:read quotes:write bookings:read bookings:write");bob=issuer.token("other-"+UUID.randomUUID(),"quotes:read quotes:write bookings:read bookings:write");
        // Isolated Testcontainer fixtures only. Never truncate application history in an operational runbook.
        jdbc.sql("truncate mock_customer_cancellation,booking_aftercare_outbox,booking_cancellation_work,booking_cancellation_command,booking_cancellation,booking_cancellation_quote,booking_checkout_outbox,booking_checkout_work,booking_checkout_command,booking_checkout,booking_delivery_replay,booking_timeline_event,booking_event_delivery,booking_outbox,booking_command,booking_reservation,booking_workflow,booking_intent").update();
        jdbc.sql("create table if not exists test_validation_reads(quote_id uuid primary key,reads int not null)").update();
    }
    @AfterAll static void close(){issuer.close();}
    HttpResponse<String> call(String method,String path,String token,String key,String body)throws Exception {
        var r=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(15));if(token!=null)r.header("Authorization","Bearer "+token);if(key!=null)r.header("Idempotency-Key",key);
        if(method.equals("POST"))r.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body));else r.GET();return http.send(r.build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode tree(HttpResponse<String> r,int expected){assertThat(r.statusCode()).as(r.body()).isEqualTo(expected);return json.readTree(r.body());}
    String key(){return UUID.randomUUID().toString();}
    String offer(int index)throws Exception {
        String request="{\"origin\":\"DEL\",\"destination\":\"BOM\",\"departureDate\":\""+LocalDate.now().plusDays(2)+"\",\"returnDate\":null,\"adults\":1,\"children\":0,\"infants\":0,\"cabin\":\"ECONOMY\"}";
        return tree(call("POST","/api/v1/flights/search",null,null,request),200).get("offers").get(index).get("id").asString();
    }
    String quote(String offer,boolean accepted)throws Exception {
        String id=tree(call("POST","/api/v1/quotes",auth,key(),"{\"offerId\":\""+offer+"\"}"),201).get("id").asString();
        if(accepted)tree(call("POST","/api/v1/quotes/"+id+"/acceptance",auth,key(),"{\"version\":1}"),200);return id;
    }
    String request(String quote){return "{\"quoteId\":\""+quote+"\",\"quoteVersion\":1,\"travellers\":[{\"givenName\":\"Alex\",\"familyName\":\"Example\",\"dateOfBirth\":\"1990-01-01\"}]}";}
    JsonNode create(String quote,String command)throws Exception{return tree(call("POST","/api/v1/bookings",auth,command,request(quote)),202);}
    String booking()throws Exception{return create(quote(offer(0),true),key()).get("id").asString();}
    JsonNode get(String id)throws Exception{return tree(call("GET","/api/v1/bookings/"+id,auth,null,""),200);}
    int count(String table){return jdbc.sql("select count(*) from "+table).query(Integer.class).single();}
    BookingWorker worker(BookingValidationPort port){return new BookingWorker(store,port,clock,Duration.ofSeconds(15),4);}
    void expireLease(String id){jdbc.sql("update booking_workflow set lease_until=clock_timestamp()-interval '1 second' where booking_id=:id").param("id",UUID.fromString(id)).update();}
    void due(String id){jdbc.sql("update booking_workflow set next_attempt_at=clock_timestamp() where booking_id=:id").param("id",UUID.fromString(id)).update();}
    @Test void rejectsUnownedUnacceptedExpiredWrongVersionAndInvalidTravellers()throws Exception {
        String q=quote(offer(0),false);
        assertThat(tree(call("POST","/api/v1/bookings",auth,key(),request(q)),409).get("code").asString()).isEqualTo("QUOTE_NOT_ACCEPTED");
        assertThat(call("GET","/api/v1/quotes/"+q+"/booking-requirements",bob,null,"").statusCode()).isEqualTo(404);
        assertThat(call("POST","/api/v1/bookings",bob,key(),request(q)).statusCode()).isEqualTo(404);
        tree(call("POST","/api/v1/quotes/"+q+"/acceptance",auth,key(),"{\"version\":1}"),200);
        assertThat(call("POST","/api/v1/bookings",auth,key(),request(q).replace("\"quoteVersion\":1","\"quoteVersion\":2")).statusCode()).isEqualTo(409);
        assertThat(call("POST","/api/v1/bookings",auth,key(),request(q).replace("1990-01-01",LocalDate.now().minusYears(8).toString())).statusCode()).isEqualTo(422);
        assertThat(call("POST","/api/v1/bookings",auth,key(),request(q).replace("Alex","<script>")).statusCode()).isEqualTo(422);
        assertThat(call("POST","/api/v1/bookings",auth,key(),request(q).replace("\"quoteVersion\":1","\"quoteVersion\":1,\"total\":1")).statusCode()).isEqualTo(400);
        assertThat(call("POST","/api/v1/bookings",auth,null,request(q)).statusCode()).isEqualTo(400);
        assertThat(call("POST","/api/v1/bookings",issuer.token("restricted","quotes:read"),key(),request(q)).statusCode()).isEqualTo(403);
        clock.now=clock.now.plusSeconds(180);
        assertThat(tree(call("POST","/api/v1/bookings",auth,key(),request(q)),409).get("code").asString()).isEqualTo("QUOTE_EXPIRED");
        assertThat(count("booking_intent")).isZero();assertThat(count("booking_outbox")).isZero();
    }
    @Test void concurrentKeysReplayOneIntentAndChangedPayloadsConflictEvenAfterExpiry()throws Exception {
        String q=quote(offer(0),true),sameKey=key();JsonNode first;
        try(var pool=Executors.newFixedThreadPool(6)) {
            var calls=new ArrayList<Callable<JsonNode>>();for(int i=0;i<6;i++)calls.add(()->create(q,sameKey));
            var results=pool.invokeAll(calls);first=results.getFirst().get();for(var result:results)assertThat(result.get()).isEqualTo(first);
            calls.clear();for(int i=0;i<6;i++)calls.add(()->create(q,key()));for(var result:pool.invokeAll(calls))assertThat(result.get()).isEqualTo(first);
        }
        assertThat(count("booking_intent")).isEqualTo(1);assertThat(count("booking_workflow")).isEqualTo(1);assertThat(count("booking_reservation")).isEqualTo(1);assertThat(count("booking_outbox")).isEqualTo(1);assertThat(count("booking_command")).isEqualTo(7);
        assertThat(tree(call("POST","/api/v1/bookings",auth,sameKey,request(q).replace("Alex","Blair")),409).get("code").asString()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(tree(call("POST","/api/v1/bookings",auth,key(),request(q).replace("Alex","Blair")),409).get("code").asString()).isEqualTo("BOOKING_CONFLICT");
        assertThat(tree(call("POST","/api/v1/bookings",auth,sameKey,request(q).replace("Alex","  Alex  ")),202)).isEqualTo(first);
        clock.now=clock.now.plusSeconds(180);assertThat(create(q,sameKey)).isEqualTo(first);assertThat(create(q,key())).isEqualTo(first);
        assertThat(tree(call("GET","/api/v1/quotes/"+q+"/booking-requirements",auth,null,""),200).get("existingBookingId")).isEqualTo(first.get("id"));
    }
    @Test void freshProviderValidationCanOutliveDiscoveryCacheButNeverConfirmsReservation()throws Exception {
        String source=offer(2);clock.now=clock.now.plusSeconds(890);String q=quote(source,true);String id=create(q,key()).get("id").asString();clock.now=clock.now.plusSeconds(20);
        var worker=worker(approved->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();assertThat(jdbc.sql("select attempts from booking_workflow where booking_id=:id").param("id",UUID.fromString(id)).query(Integer.class).single()).isEqualTo(1);return validation.current(approved);});
        assertThat(worker.runNext()).isTrue();var details=get(id);
        assertThat(details.get("booking").get("status").asString()).isEqualTo("AWAITING_PAYMENT");
        assertThat(details.get("booking").get("total").get("amount").asString()).isEqualTo("4681.00");
        assertThat(details.get("reservationStatus").asString()).isEqualTo("NOT_REQUESTED");assertThat(details.get("paymentStatus").asString()).isEqualTo("NOT_STARTED");
        assertThat(details.get("workflow").get("terminal").asBoolean()).isTrue();assertThat(count("booking_outbox")).isEqualTo(3);assertThat(worker.runNext()).isFalse();
    }
    @Test void staleWorkersCannotCommitAndRetriesAreDurablyBounded()throws Exception {
        String id=booking();var a=store.claim(Duration.ofSeconds(15)).orElseThrow();assertThat(store.beginRead(a,4)).isEqualTo(1);expireLease(id);
        var b=store.claim(Duration.ofSeconds(15)).orElseThrow();assertThat(store.beginRead(b,4)).isEqualTo(2);
        assertThat(store.finish(a,Booking.Decision.VALIDATED)).isFalse();assertThat(get(id).get("booking").get("status").asString()).isEqualTo("VALIDATING");
        assertThat(store.finish(b,Booking.Decision.VALIDATED)).isTrue();assertThat(store.finish(b,Booking.Decision.VALIDATED)).isFalse();assertThat(count("booking_outbox")).isEqualTo(3);
        String retryId=booking();var reads=new AtomicInteger();var unavailable=worker(q->{reads.incrementAndGet();throw new DomainFailure(DomainFailure.Code.SUPPLIERS_UNAVAILABLE,"Simulated read timeout");});
        for(int i=0;i<4;i++){due(retryId);assertThat(unavailable.runNext()).isTrue();}
        var review=get(retryId);assertThat(reads.get()).isEqualTo(4);assertThat(review.get("workflow").get("attempts").asInt()).isEqualTo(4);
        assertThat(review.get("booking").get("status").asString()).isEqualTo("MANUAL_REVIEW");assertThat(review.get("inventoryStatus").asString()).isEqualTo("UNKNOWN");assertThat(unavailable.runNext()).isFalse();
    }
    @Test void expiryDuringReadAndAtDurableAcceptanceCannotAdvanceTheIntent()throws Exception {
        String id=booking();worker(q->{var result=validation.current(q);clock.now=clock.now.plusSeconds(180);return result;}).runNext();
        assertThat(get(id).get("booking").get("status").asString()).isEqualTo("EXPIRED");
        clock.now=Instant.now();String other=booking();var original=store.forQuote(UUID.fromString(tree(call("GET","/api/v1/me",auth,null,""),200).get("id").asString()),UUID.fromString(get(other).get("booking").get("quoteId").asString())).orElseThrow();
        // A direct persistence-boundary call proves rollback of a winner that expires while awaiting local commit.
        var invalid=new Booking(UUID.randomUUID(),new BookingQuote(UUID.randomUUID(),1,original.quote().customerId(),original.quote().acceptanceId(),original.quote().offerId(),"MOCK",clock.instant().minusSeconds(1),1,0,0,original.quote().terms()),original.travellers(),"a".repeat(64),clock.instant(),clock.instant(),1,Booking.Status.CREATED,null,UUID.randomUUID(),UUID.randomUUID());
        int before=count("booking_intent");assertThatThrownBy(()->store.create(invalid,key())).isInstanceOf(DomainFailure.class);assertThat(count("booking_intent")).isEqualTo(before);
    }
    @Test void pricePolicyAndInventoryChangesHaveDistinctSafeOutcomes()throws Exception {
        String price=booking();worker(q->{var t=q.terms();return new FlightTerms(t.supplier(),t.supplierReference(),t.airline(),t.legs(),t.passengers(),t.cabin(),t.baggage(),t.cancellationPolicy(),new FlightTerms.Money(t.base().amount().add(java.math.BigDecimal.ONE),"INR"),t.taxes());}).runNext();
        assertThat(get(price).get("booking").get("failureCode").asString()).isEqualTo("PRICE_CHANGED");
        String terms=booking();worker(q->{var t=q.terms();return new FlightTerms(t.supplier(),t.supplierReference(),t.airline(),t.legs(),t.passengers(),t.cabin(),t.baggage(),"Changed policy",t.base(),t.taxes());}).runNext();
        assertThat(get(terms).get("booking").get("failureCode").asString()).isEqualTo("TERMS_CHANGED");
        String lost=booking();worker(q->{throw new DomainFailure(DomainFailure.Code.OFFER_UNAVAILABLE,"Lost availability");}).runNext();assertThat(get(lost).get("booking").get("status").asString()).isEqualTo("INVENTORY_UNAVAILABLE");
    }
    @Test void ownedHistoryIsPaginatedAndDoesNotLeakTravellerData()throws Exception {
        String id=booking();booking();booking();
        assertThat(call("GET","/api/v1/bookings/"+id,bob,null,"").statusCode()).isEqualTo(404);assertThat(call("GET","/api/v1/bookings/"+id,null,null,"").statusCode()).isEqualTo(401);
        Set<String> ids=new HashSet<>();String cursor="";
        for(int i=0;i<3;i++){var result=tree(call("GET","/api/v1/me/bookings?size=1"+cursor,auth,null,""),200);assertThat(result.toString()).doesNotContain("Alex","1990-01-01");assertThat(ids.add(result.get("items").get(0).get("id").asString())).isTrue();cursor=result.get("nextCursor").isNull()?"":"&after="+result.get("nextCursor").asString();}
        assertThat(tree(call("GET","/api/v1/me/bookings",bob,null,""),200).get("items").size()).isZero();assertThat(call("GET","/api/v1/me/bookings?after="+id,bob,null,"").statusCode()).isEqualTo(422);
        assertThatThrownBy(()->jdbc.sql("update booking_intent set input_snapshot='{}',version=version+1 where id=:id").param("id",UUID.fromString(id)).update()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.sql("update booking_reservation set external_reference='FAKE' where booking_id=:id").param("id",UUID.fromString(id)).update()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.sql("select envelope::text from booking_outbox").query(String.class).list().toString()).doesNotContain("Alex","Example","1990-01-01");
    }
    @Test void aRealWorkerJvmCrashAfterReadRecoversWithoutDuplicateTransitions()throws Exception {
        String id=booking();String q=get(id).get("booking").get("quoteId").asString();
        assertThat(child("crash")).isEqualTo(23);assertThat(get(id).get("booking").get("status").asString()).isEqualTo("VALIDATING");
        assertThat(count("booking_outbox")).isEqualTo(2);expireLease(id); // Simulate elapsed downtime without a sleep-based test.
        assertThat(child("resume")).isZero();var details=get(id);assertThat(details.get("booking").get("status").asString()).isEqualTo("AWAITING_PAYMENT");
        assertThat(details.get("workflow").get("attempts").asInt()).isEqualTo(2);assertThat(count("booking_outbox")).isEqualTo(3);
        assertThat(jdbc.sql("select reads from test_validation_reads where quote_id=:id").param("id",UUID.fromString(q)).query(Integer.class).single()).isEqualTo(2);
        assertThat(details.get("reservationStatus").asString()).isEqualTo("NOT_REQUESTED");assertThat(details.get("paymentStatus").asString()).isEqualTo("NOT_STARTED");
    }
    int child(String phase)throws Exception {
        Path log=temporary.resolve(phase+".log");String classpath=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",classpath,"com.travbook.support.BookingWorkerProcess",phase).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("DATABASE_URL",postgres.getJdbcUrl());builder.environment().put("DATABASE_USERNAME",postgres.getUsername());builder.environment().put("DATABASE_PASSWORD",postgres.getPassword());
        var process=builder.start();boolean exited=process.waitFor(30,TimeUnit.SECONDS);if(!exited)process.destroyForcibly();assertThat(exited).as("Child worker exceeded deadline").isTrue();
        int code=process.exitValue();if(code!=0&&code!=23)throw new AssertionError("Worker failed: "+Files.readString(log));return code;
    }
}
