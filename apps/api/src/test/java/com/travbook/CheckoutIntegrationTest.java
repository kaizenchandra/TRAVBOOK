package com.travbook;
import com.travbook.booking.application.port.out.*;
import com.travbook.booking.application.usecase.*;
import com.travbook.booking.domain.model.*;
import com.travbook.payments.application.port.in.PaymentOperations;
import com.travbook.payments.domain.model.Payment;
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
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"travbook.booking.worker-enabled=false","travbook.booking.events-enabled=false","travbook.booking.checkout-enabled=false"})
@Import(CheckoutIntegrationTest.TimeConfiguration.class)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class CheckoutIntegrationTest {
    static final TestIssuer issuer=new TestIssuer();
    @Container static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine");
    @DynamicPropertySource static void props(DynamicPropertyRegistry r){r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);r.add("travbook.oidc-issuer",issuer::url);r.add("travbook.local-identity",()->true);}
    static class MutableClock extends Clock {volatile Instant now=Instant.now();public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return Clock.fixed(now,zone);}public Instant instant(){return now;}}
    @TestConfiguration static class TimeConfiguration {@Bean @Primary MutableClock checkoutClock(){return new MutableClock();}}
    @Autowired MutableClock clock;@Autowired ObjectMapper json;@Autowired JdbcClient jdbc;@Autowired BookingStore bookings;
    @Autowired BookingValidationPort validation;@Autowired CheckoutStore store;@Autowired CheckoutPayments payments;@Autowired CheckoutSupplier supplier;@Autowired PaymentOperations paymentOperations;
    @LocalServerPort int port;@TempDir Path temporary;String auth,bob;
    final HttpClient http=HttpClient.newHttpClient();
    @BeforeEach void setup()throws Exception{
        clock.now=Instant.now();auth=issuer.token("owner-"+UUID.randomUUID(),"quotes:read quotes:write bookings:read bookings:write");bob=issuer.token("other-"+UUID.randomUUID(),"quotes:read quotes:write bookings:read bookings:write");
        // Isolated Testcontainer fixtures, never the preserved local database.
        jdbc.sql("truncate mock_customer_cancellation,booking_aftercare_outbox,booking_cancellation_work,booking_cancellation_command,booking_cancellation,booking_cancellation_quote,payment_refund_outbox,payment_refund_journal,payment_refund,payment_journal,payment_operation,payment_account,mock_supplier_operation,mock_supplier_reservation,booking_checkout_outbox,booking_checkout_work,booking_checkout_command,booking_checkout,booking_delivery_replay,booking_timeline_event,booking_event_delivery,booking_outbox,booking_command,booking_reservation,booking_workflow,booking_intent").update();
    }
    @AfterAll static void close(){issuer.close();}
    HttpResponse<String> call(String method,String path,String token,String key,String body)throws Exception{
        var r=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(15));if(token!=null)r.header("Authorization","Bearer "+token);if(key!=null)r.header("Idempotency-Key",key);
        if(method.equals("POST"))r.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body));else r.GET();return http.send(r.build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode tree(HttpResponse<String> r,int code){assertThat(r.statusCode()).as(r.body()).isEqualTo(code);return json.readTree(r.body());}
    String key(){return UUID.randomUUID().toString();}
    String intent()throws Exception{
        var search=tree(call("POST","/api/v1/flights/search",null,null,"{\"origin\":\"DEL\",\"destination\":\"BOM\",\"departureDate\":\""+LocalDate.now().plusDays(2)+"\",\"returnDate\":null,\"adults\":1,\"children\":0,\"infants\":0,\"cabin\":\"ECONOMY\"}"),200);
        String q=tree(call("POST","/api/v1/quotes",auth,key(),"{\"offerId\":\""+search.get("offers").get(0).get("id").asString()+"\"}"),201).get("id").asString();
        tree(call("POST","/api/v1/quotes/"+q+"/acceptance",auth,key(),"{\"version\":1}"),200);
        String id=tree(call("POST","/api/v1/bookings",auth,key(),"{\"quoteId\":\""+q+"\",\"quoteVersion\":1,\"travellers\":[{\"givenName\":\"Alex\",\"familyName\":\"Example\",\"dateOfBirth\":\"1990-01-01\"}]}"),202).get("id").asString();
        new BookingWorker(bookings,validation,clock,Duration.ofSeconds(15),4).runNext();return id;
    }
    String body(String scenario){return "{\"scenario\":\""+scenario+"\",\"consent\":true}";}
    JsonNode start(String id,String scenario,String key)throws Exception{return tree(call("POST","/api/v2/bookings/"+id+"/checkout",auth,key,body(scenario)),202);}
    JsonNode view(String id)throws Exception{return tree(call("GET","/api/v2/bookings/"+id,auth,null,""),200).get("checkout");}
    CheckoutWorker worker(){return new CheckoutWorker(store,validation,payments,supplier,clock);}
    int count(String table){return jdbc.sql("select count(*) from "+table).query(Integer.class).single();}
    void due(){jdbc.sql("update booking_checkout_work set next_attempt_at=clock_timestamp() where state='RETRY'").update();}
    void finish(String id)throws Exception{for(int i=0;i<12&&!view(id).get("terminal").asBoolean();i++){due();assertThat(worker().runNext()).isTrue();}assertThat(view(id).get("terminal").asBoolean()).isTrue();}
    void expireLease(){jdbc.sql("update booking_checkout_work set lease_until=clock_timestamp()-interval '1 second' where state='RUNNING'").update();}
    @Test void concurrentOwnedConsentCreatesOneCheckoutAndLegacyReadsFailClosed()throws Exception{
        String id=intent(),key=key();assertThat(tree(call("GET","/api/v2/bookings/"+id,auth,null,""),200).get("checkout").isNull()).isTrue();
        assertThat(call("POST","/api/v2/bookings/"+id+"/checkout",bob,key,body("SUCCESS")).statusCode()).isEqualTo(404);
        assertThat(call("GET","/api/v2/bookings/"+id,null,null,"").statusCode()).isEqualTo(401);
        // Cookie-style POSTs without CSRF fail before authentication; bearer auth still must verify.
        assertThat(call("POST","/api/v2/bookings/"+id+"/checkout",null,key,body("SUCCESS")).statusCode()).isEqualTo(403);
        assertThat(call("POST","/api/v2/bookings/"+id+"/checkout","invalid",key,body("SUCCESS")).statusCode()).isEqualTo(401);
        assertThat(call("POST","/api/v2/bookings/"+id+"/checkout",issuer.token("limited","bookings:read"),key,body("SUCCESS")).statusCode()).isEqualTo(403);
        assertThat(call("POST","/api/v2/bookings/"+id+"/checkout",auth,key,body("SUCCESS").replace("true","false")).statusCode()).isEqualTo(400);
        assertThat(call("POST","/api/v2/bookings/"+id+"/checkout",auth,key,"{\"scenario\":\"SUCCESS\",\"consent\":true,\"amount\":1}").statusCode()).isEqualTo(400);
        JsonNode first;try(var pool=Executors.newFixedThreadPool(6)){
            var calls=new ArrayList<Callable<JsonNode>>();for(int i=0;i<6;i++)calls.add(()->start(id,"SUCCESS",key));var results=pool.invokeAll(calls);first=results.getFirst().get();for(var r:results)assertThat(r.get()).isEqualTo(first);
            calls.clear();for(int i=0;i<6;i++)calls.add(()->start(id,"SUCCESS",key()));for(var r:pool.invokeAll(calls))assertThat(r.get()).isEqualTo(first);
        }
        assertThat(count("booking_checkout")).isEqualTo(1);assertThat(count("booking_checkout_outbox")).isEqualTo(1);assertThat(count("payment_account")).isZero();
        assertThat(call("POST","/api/v2/bookings/"+id+"/checkout",auth,key,body("CAPTURE_DECLINED")).statusCode()).isEqualTo(409);
        assertThat(call("GET","/api/v1/bookings/"+id,auth,null,"").statusCode()).isEqualTo(409);
        assertThat(call("GET","/api/v2/bookings/"+id,bob,null,"").statusCode()).isEqualTo(404);
        clock.now=clock.now.plusSeconds(180);assertThat(start(id,"SUCCESS",key)).isEqualTo(first);
    }
    @Test void confirmationRequiresCaptureAndSupplierWithOneBalancedImmutableJournal()throws Exception{
        String id=intent();start(id,"SUCCESS",key());var w=worker();assertThat(w.runNext()).isTrue();assertThat(w.runNext()).isTrue();
        assertThat(view(id).get("paymentStatus").asString()).isEqualTo("AUTHORIZED");assertThat(view(id).get("status").asString()).isEqualTo("RUNNING");assertThat(count("mock_supplier_reservation")).isZero();
        w.runNext();assertThat(view(id).get("reservationStatus").asString()).isEqualTo("CONFIRMED");assertThat(view(id).get("status").asString()).isEqualTo("RUNNING");assertThat(count("payment_journal")).isZero();
        w.runNext();var c=view(id);assertThat(c.get("status").asString()).isEqualTo("CONFIRMED");assertThat(c.get("paymentStatus").asString()).isEqualTo("CAPTURED");assertThat(count("payment_operation")).isEqualTo(2);assertThat(count("payment_journal")).isEqualTo(2);
        assertThat(jdbc.sql("select sum(case when direction='DEBIT' then amount else -amount end) from payment_journal").query(java.math.BigDecimal.class).single()).isEqualByComparingTo("0");
        assertThatThrownBy(()->jdbc.sql("update payment_journal set amount=1").update()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.sql("update payment_account set amount=1,version=version+1").update()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        var op=jdbc.sql("select request::text from payment_operation where operation='CAPTURE'").query(String.class).single();var command=json.readValue(op,PaymentOperations.Command.class);
        assertThat(paymentOperations.execute(command).state()).isEqualTo(Payment.State.CAPTURED);assertThat(count("payment_journal")).isEqualTo(2);
        assertThatThrownBy(()->paymentOperations.execute(new PaymentOperations.Command(command.operationId(),command.paymentId(),command.customerId(),command.operation(),command.amount().add(java.math.BigDecimal.ONE),command.currency(),command.validUntil(),false))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void declinesCompensateWithoutChargingOrFalselyConfirming()throws Exception{
        for(String scenario:List.of("PAYMENT_DECLINED","RESERVATION_REJECTED","CAPTURE_DECLINED")){
            String id=intent();start(id,scenario,key());finish(id);var c=view(id);assertThat(c.get("status").asString()).isEqualTo("FAILED");
            assertThat(c.get("paymentStatus").asString()).isEqualTo(scenario.equals("PAYMENT_DECLINED")?"DECLINED":"VOIDED");
            assertThat(c.get("reservationStatus").asString()).isEqualTo(scenario.equals("PAYMENT_DECLINED")?"NOT_REQUESTED":scenario.equals("RESERVATION_REJECTED")?"REJECTED":"CANCELLED");
        }
        assertThat(count("payment_journal")).isZero();assertThat(count("mock_supplier_operation")).isEqualTo(3);
    }
    @Test void lostReservationAndCaptureResponsesReconcileExactlyOneProviderEffect()throws Exception{
        for(String scenario:List.of("RESERVATION_RESPONSE_LOST","CAPTURE_RESPONSE_LOST")){
            String id=intent();start(id,scenario,key());worker().runNext();worker().runNext();worker().runNext();if(scenario.startsWith("CAPTURE"))worker().runNext();
            var unknown=view(id);assertThat(unknown.get("status").asString()).isEqualTo("RUNNING");assertThat(unknown.get(scenario.startsWith("CAPTURE")?"paymentStatus":"reservationStatus").asString()).isEqualTo("UNKNOWN");
            if(scenario.startsWith("CAPTURE"))clock.now=clock.now.plusSeconds(180); // Recover an already committed capture even after quote expiry.
            finish(id);assertThat(view(id).get("status").asString()).isEqualTo("CONFIRMED");
        }
        assertThat(count("mock_supplier_reservation")).isEqualTo(2);assertThat(count("mock_supplier_operation")).isEqualTo(2);assertThat(count("payment_operation")).isEqualTo(4);assertThat(count("payment_journal")).isEqualTo(4);
    }
    @Test void expiryBeforeAuthorizationAndAfterAuthorizationCannotCreateReservation()throws Exception{
        String a=intent();start(a,"SUCCESS",key());clock.now=clock.now.plusSeconds(180);finish(a);assertThat(view(a).get("status").asString()).isEqualTo("FAILED");assertThat(count("payment_account")).isZero();
        clock.now=Instant.now();String b=intent();start(b,"SUCCESS",key());worker().runNext();worker().runNext();clock.now=clock.now.plusSeconds(180);finish(b);
        assertThat(view(b).get("paymentStatus").asString()).isEqualTo("VOIDED");assertThat(count("mock_supplier_reservation")).isZero();assertThat(count("payment_journal")).isZero();
        clock.now=Instant.now();String reserved=intent();start(reserved,"SUCCESS",key());worker().runNext();worker().runNext();worker().runNext();clock.now=clock.now.plusSeconds(180);finish(reserved);
        assertThat(view(reserved).get("paymentStatus").asString()).isEqualTo("VOIDED");assertThat(view(reserved).get("reservationStatus").asString()).isEqualTo("CANCELLED");assertThat(count("payment_journal")).isZero();
        clock.now=Instant.now();String c=intent();clock.now=clock.now.plusSeconds(180);assertThat(call("POST","/api/v2/bookings/"+c+"/checkout",auth,key(),body("SUCCESS")).statusCode()).isEqualTo(409);
    }
    @Test void unknownLookupNeverTriggersMutationAndBoundedAttemptsRequireReview()throws Exception{
        String id=intent();start(id,"SUCCESS",key());worker().runNext();var effects=new AtomicInteger();
        var failing=new CheckoutPayments(){public Optional<Checkout.Effect> lookup(UUID op){throw new IllegalStateException("Provider unreachable");}public Checkout.Effect execute(Checkout c){effects.incrementAndGet();return payments.execute(c);}};
        var w=new CheckoutWorker(store,validation,failing,supplier,clock);for(int i=0;i<5;i++){due();w.runNext();}
        assertThat(view(id).get("status").asString()).isEqualTo("REVIEW");assertThat(view(id).get("paymentStatus").asString()).isEqualTo("UNKNOWN");assertThat(effects.get()).isZero();assertThat(count("payment_account")).isZero();assertThat(w.runNext()).isFalse();
    }
    @Test void staleWorkersCannotCommitAndPriceChangesAfterAuthorizationVoidSafely()throws Exception{
        String id=intent();start(id,"SUCCESS",key());var a=store.claim(Duration.ofSeconds(15)).orElseThrow();expireLease();var b=store.claim(Duration.ofSeconds(15)).orElseThrow();
        assertThat(store.finish(a,a.checkout().verified(clock.instant()))).isFalse();assertThat(store.finish(b,b.checkout().verified(clock.instant()))).isTrue();worker().runNext();
        var changed=new CheckoutWorker(store,q->{var t=validation.current(q);assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return new FlightTerms(t.supplier(),t.supplierReference(),t.airline(),t.legs(),t.passengers(),t.cabin(),t.baggage(),"Changed policy",t.base(),t.taxes());},payments,supplier,clock);
        changed.runNext();finish(id);assertThat(view(id).get("status").asString()).isEqualTo("FAILED");assertThat(view(id).get("paymentStatus").asString()).isEqualTo("VOIDED");assertThat(count("mock_supplier_reservation")).isZero();
    }
    @Test void actualJvmCrashAfterSupplierCommitRecoversByLookupWithoutSecondReservation()throws Exception{
        String id=intent();start(id,"SUCCESS",key());worker().runNext();worker().runNext();assertThat(child("crash")).isEqualTo(25);
        assertThat(count("mock_supplier_operation")).isEqualTo(1);assertThat(view(id).get("reservationStatus").asString()).isEqualTo("UNKNOWN");expireLease();
        assertThat(child("resume")).isZero();finish(id);assertThat(view(id).get("status").asString()).isEqualTo("CONFIRMED");assertThat(count("mock_supplier_operation")).isEqualTo(1);assertThat(count("payment_journal")).isEqualTo(2);
    }
    @Test void failedLocalCompletionLeavesProviderReceiptRecoverableAndOutboxAtomic()throws Exception{
        String id=intent();start(id,"SUCCESS",key());worker().runNext();
        jdbc.sql("create function test_checkout_failure() returns trigger language plpgsql as $$ begin if NEW.envelope->'payload'->>'payment'='AUTHORIZED' then raise exception 'injected completion failure'; end if; return NEW; end; $$").update();
        jdbc.sql("create trigger test_checkout_failure before insert on booking_checkout_outbox for each row execute function test_checkout_failure()").update();
        var checked=new CheckoutPayments(){public Optional<Checkout.Effect> lookup(UUID op){assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return payments.lookup(op);}public Checkout.Effect execute(Checkout c){assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return payments.execute(c);}};
        try{assertThatThrownBy(()->new CheckoutWorker(store,validation,checked,supplier,clock).runNext()).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(count("payment_operation")).isEqualTo(1);assertThat(count("booking_checkout_outbox")).isEqualTo(3);assertThat(view(id).get("paymentStatus").asString()).isEqualTo("UNKNOWN");
        }finally{jdbc.sql("drop trigger test_checkout_failure on booking_checkout_outbox").update();jdbc.sql("drop function test_checkout_failure()").update();}
        expireLease();finish(id);assertThat(view(id).get("status").asString()).isEqualTo("CONFIRMED");assertThat(count("payment_operation")).isEqualTo(2);assertThat(count("payment_journal")).isEqualTo(2);
    }
    int child(String phase)throws Exception{
        Path log=temporary.resolve(phase+".log");String cp=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",cp,"com.travbook.support.CheckoutProcess",phase).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("DATABASE_URL",postgres.getJdbcUrl());builder.environment().put("DATABASE_USERNAME",postgres.getUsername());builder.environment().put("DATABASE_PASSWORD",postgres.getPassword());
        var process=builder.start();boolean exited=process.waitFor(30,TimeUnit.SECONDS);if(!exited)process.destroyForcibly();assertThat(exited).isTrue();int code=process.exitValue();if(code!=0&&code!=25)throw new AssertionError(Files.readString(log));return code;
    }
}
