package com.travbook;
import com.travbook.booking.application.port.in.*;
import com.travbook.booking.application.port.out.*;
import com.travbook.booking.application.usecase.*;
import com.travbook.booking.domain.model.*;
import com.travbook.customer.application.port.in.Customers;
import com.travbook.payments.application.port.in.PaymentRefunds;
import com.travbook.payments.adapter.out.persistence.JdbcMockRefunds;
import com.travbook.shared.domain.*;
import com.travbook.support.TestIssuer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
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
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"travbook.booking.worker-enabled=false","travbook.booking.events-enabled=false","travbook.booking.checkout-enabled=false","travbook.booking.cancellation-enabled=false","travbook.payments.refund-worker-enabled=false"})
@Import(RefundIntegrationTest.TimeConfiguration.class)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class RefundIntegrationTest {
    static final TestIssuer issuer=new TestIssuer();
    @Container static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine");
    @DynamicPropertySource static void props(DynamicPropertyRegistry r){r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);r.add("travbook.oidc-issuer",issuer::url);r.add("travbook.local-identity",()->true);}
    static class MutableClock extends Clock {volatile Instant now=Instant.now();public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return Clock.fixed(now,z);}public Instant instant(){return now;}}
    @TestConfiguration static class TimeConfiguration {@Bean @Primary MutableClock refundClock(){return new MutableClock();}}
    @Autowired MutableClock clock;@Autowired ObjectMapper json;@Autowired JdbcClient jdbc;@Autowired BookingStore bookings;@Autowired BookingCheckouts checkouts;
    @Autowired CheckoutStore checkoutStore;@Autowired CheckoutPayments checkoutPayments;@Autowired CheckoutSupplier checkoutSupplier;@Autowired Customers customers;
    @Autowired CancellationStore store;@Autowired CancellationSupplier supplier;@Autowired CancellationRefunds refundPort;@Autowired PaymentRefunds payments;@Autowired PlatformTransactionManager manager;
    @LocalServerPort int port;@TempDir Path temp;String auth,bob;CustomerIdentity identity;UUID owner;
    @BeforeEach void setup()throws Exception{
        clock.now=Instant.now();String subject="refund-owner-"+UUID.randomUUID();identity=new CustomerIdentity(issuer.url(),subject);owner=customers.resolve(identity).id();auth=issuer.token(subject,"bookings:read bookings:write");bob=issuer.token("other-"+UUID.randomUUID(),"bookings:read bookings:write");
        // Isolated Testcontainer fixtures only. Never reset the local application's financial history.
        jdbc.sql("truncate trip_document_outbox,trip_document_command,trip_document,mock_customer_cancellation,booking_aftercare_outbox,booking_cancellation_work,booking_cancellation_command,booking_cancellation,booking_cancellation_quote,payment_refund_outbox,payment_refund_journal,payment_refund,payment_journal,payment_operation,payment_account,mock_supplier_operation,mock_supplier_reservation,booking_checkout_outbox,booking_checkout_work,booking_checkout_command,booking_checkout,booking_delivery_replay,booking_timeline_event,booking_event_delivery,booking_outbox,booking_command,booking_reservation,booking_workflow,booking_intent").update();
    }
    @AfterAll static void close(){issuer.close();}
    UUID booking(String policy){
        var dep=clock.instant().atZone(ZoneId.of("Asia/Kolkata")).plusDays(2).toOffsetDateTime();var t=new FlightTerms("sample-aster","MOCK-fixture","Aster Air",List.of(new FlightTerms.Leg("DEL","BOM",dep,dep.plusHours(2),"Asia/Kolkata",120,0,"MOCK-101")),1,"ECONOMY","Fixture baggage",policy,new FlightTerms.Money(new BigDecimal("400.00"),"INR"),new FlightTerms.Money(new BigDecimal("20.00"),"INR"));
        var q=new BookingQuote(UUID.randomUUID(),1,owner,UUID.randomUUID(),UUID.randomUUID(),"MOCK",clock.instant().plusSeconds(120),1,0,0,t);UUID id=UUID.randomUUID();
        bookings.create(new Booking(id,q,List.of(new Traveller("Alex","Example",LocalDate.of(1990,1,1))),"a".repeat(64),clock.instant(),clock.instant(),1,Booking.Status.CREATED,null,UUID.randomUUID(),UUID.randomUUID()),key());
        new BookingWorker(bookings,s->s.terms(),clock,Duration.ofSeconds(15),4).runNext();checkouts.start(identity,id,Checkout.Scenario.SUCCESS,true,key(),UUID.randomUUID());
        var w=new CheckoutWorker(checkoutStore,s->s.terms(),checkoutPayments,checkoutSupplier,clock);for(int i=0;i<4;i++)w.runNext();return id;
    }
    String key(){return UUID.randomUUID().toString();}
    String path(UUID id,String suffix){return "/api/v1/bookings/"+id+"/"+suffix;}
    HttpResponse<String> call(String method,String path,String token,String key,String body)throws Exception{
        var r=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(15));if(token!=null)r.header("Authorization","Bearer "+token);if(key!=null)r.header("Idempotency-Key",key);
        if(method.equals("POST"))r.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body));else r.GET();return HttpClient.newHttpClient().send(r.build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode tree(HttpResponse<String> r,int code){assertThat(r.statusCode()).as(r.body()).isEqualTo(code);return json.readTree(r.body());}
    JsonNode quote(UUID id,String key)throws Exception{return tree(call("POST",path(id,"cancellation-quotes"),auth,key,"{\"scope\":\"FULL_BOOKING\"}"),201);}
    String request(JsonNode q,String scenario){return "{\"quoteId\":\""+q.get("id").asString()+"\",\"quoteVersion\":1,\"confirmed\":true,\"scenario\":\""+scenario+"\"}";}
    JsonNode start(UUID id,JsonNode q,String scenario,String key)throws Exception{return tree(call("POST",path(id,"cancellations"),auth,key,request(q,scenario)),202);}
    JsonNode view(UUID id)throws Exception{return tree(call("GET","/api/v3/bookings/"+id,auth,null,""),200).get("cancellation");}
    CancellationWorker worker(){return new CancellationWorker(store,supplier,refundPort,clock);}
    void due(){jdbc.sql("update booking_cancellation_work set next_attempt_at=clock_timestamp() where state='RETRY'").update();}
    void expireLease(){jdbc.sql("update booking_cancellation_work set lease_until=clock_timestamp()-interval '1 second' where state='RUNNING'").update();}
    int count(String table){return jdbc.sql("select count(*) from "+table).query(Integer.class).single();}
    void finish(UUID id)throws Exception{for(int i=0;i<12&&!view(id).get("terminal").asBoolean();i++){due();worker().runNext();payments.settleNext();}assertThat(view(id).get("terminal").asBoolean()).isTrue();}
    @Test void explicitPoliciesOwnershipExpiryAndPriceInputAreEnforced()throws Exception{
        UUID legacy=booking("Demonstration policy only: fare non-refundable; no reservation or charge is available.");assertThat(call("POST",path(legacy,"cancellation-quotes"),auth,key(),"{\"scope\":\"FULL_BOOKING\"}").statusCode()).isEqualTo(409);
        UUID id=booking(MockCancellationPolicy.FULL.text());var q=quote(id,key());assertThat(q.get("refundAmount").get("amount").asString()).isEqualTo("420.00");assertThat(q.get("retainedAmount").get("amount").asString()).isEqualTo("0.00");
        assertThat(call("POST",path(id,"cancellations"),bob,key(),request(q,"SUCCESS")).statusCode()).isEqualTo(404);
        assertThat(call("POST",path(id,"cancellations"),auth,key(),request(q,"SUCCESS").replace("true","false")).statusCode()).isEqualTo(400);
        assertThat(call("POST",path(id,"cancellations"),auth,key(),request(q,"SUCCESS").replace("\"quoteVersion\":1","\"quoteVersion\":1,\"refundAmount\":9999")).statusCode()).isEqualTo(400);
        assertThat(call("POST",path(id,"cancellation-quotes"),issuer.token("limited","bookings:read"),key(),"{\"scope\":\"FULL_BOOKING\"}").statusCode()).isEqualTo(403);
        assertThat(call("GET",path(id,"refunds"),null,null,"").statusCode()).isEqualTo(401);assertThat(call("GET",path(id,"refunds"),bob,null,"").statusCode()).isEqualTo(404);
        clock.now=clock.now.plusSeconds(301);assertThat(call("POST",path(id,"cancellations"),auth,key(),request(q,"SUCCESS")).statusCode()).isEqualTo(409);assertThat(count("booking_cancellation")).isZero();assertThat(count("payment_refund")).isZero();
    }
    @Test void concurrentRequestsCreateOneCancellationAndRefundOnlyAfterSupplierConfirmation()throws Exception{
        UUID id=booking(MockCancellationPolicy.FULL.text());String qkey=key(),key=key();JsonNode q,ack;
        try(var pool=Executors.newFixedThreadPool(6)){
            var quotes=new ArrayList<Callable<JsonNode>>();for(int i=0;i<6;i++)quotes.add(()->quote(id,qkey));var qr=pool.invokeAll(quotes);q=qr.getFirst().get();for(var r:qr)assertThat(r.get()).isEqualTo(q);
            var calls=new ArrayList<Callable<JsonNode>>();for(int i=0;i<6;i++)calls.add(()->start(id,q,"SUCCESS",key));var results=pool.invokeAll(calls);ack=results.getFirst().get();for(var r:results)assertThat(r.get()).isEqualTo(ack);
            calls.clear();for(int i=0;i<6;i++)calls.add(()->start(id,q,"SUCCESS",key()));for(var r:pool.invokeAll(calls))assertThat(r.get()).isEqualTo(ack);
        }
        assertThat(call("GET","/api/v2/bookings/"+id,auth,null,"").statusCode()).isEqualTo(409);
        assertThat(call("GET","/api/v2/me/bookings",auth,null,"").statusCode()).isEqualTo(409);
        assertThat(count("booking_cancellation_quote")).isEqualTo(1);assertThat(count("booking_cancellation")).isEqualTo(1);assertThat(count("payment_refund")).isZero();
        assertThat(call("POST",path(id,"cancellations"),auth,key,request(q,"REFUND_DELAYED")).statusCode()).isEqualTo(409);
        assertThat(call("POST",path(id,"documents"),auth,key(),"{\"kind\":\"MOCK_CONFIRMATION_SUMMARY\"}").statusCode()).isEqualTo(409);
        worker().runNext();assertThat(view(id).get("cancellationStatus").asString()).isEqualTo("CANCELLED");assertThat(view(id).get("refundStatus").asString()).isEqualTo("PENDING");assertThat(count("payment_refund")).isZero();
        worker().runNext();assertThat(count("payment_refund")).isEqualTo(1);assertThat(count("payment_refund_journal")).isZero();
        clock.now=clock.now.plusSeconds(301);payments.settleNext();due();worker().runNext();assertThat(view(id).get("refundStatus").asString()).isEqualTo("SUCCEEDED");assertThat(start(id,q,"SUCCESS",key)).isEqualTo(ack);
        assertThat(count("payment_journal")).isEqualTo(2);assertThat(count("payment_refund_journal")).isEqualTo(2);assertThat(count("mock_customer_cancellation")).isEqualTo(1);
        assertThat(jdbc.sql("select sum(case when direction='DEBIT' then amount else -amount end) from payment_refund_journal").query(BigDecimal.class).single()).isEqualByComparingTo("0");
        assertThat(jdbc.sql("select status from payment_account").query(String.class).single()).isEqualTo("CAPTURED");
        assertThat(tree(call("GET",path(id,"refunds"),auth,null,""),200).get("items").size()).isEqualTo(1);
        assertThatThrownBy(()->jdbc.sql("update payment_refund_journal set amount=1").update()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void zeroRefundCancelsButNeverCreatesARefundTransaction()throws Exception{
        UUID id=booking(MockCancellationPolicy.NONE.text());var q=quote(id,key());assertThat(q.get("refundAmount").get("amount").asString()).isEqualTo("0.00");start(id,q,"SUCCESS",key());finish(id);
        assertThat(view(id).get("cancellationStatus").asString()).isEqualTo("CANCELLED");assertThat(view(id).get("refundStatus").asString()).isEqualTo("NOT_DUE");assertThat(count("payment_refund")).isZero();assertThat(tree(call("GET",path(id,"refunds"),auth,null,""),200).get("items").isEmpty()).isTrue();
    }
    @Test void lostResponsesAndDelayedRefundsNeverDuplicateEffects()throws Exception{
        for(String scenario:List.of("CANCEL_RESPONSE_LOST","REFUND_RESPONSE_LOST","REFUND_DELAYED")){
            UUID id=booking(MockCancellationPolicy.FULL.text());start(id,quote(id,key()),scenario,key());worker().runNext();
            if(scenario.equals("CANCEL_RESPONSE_LOST")){assertThat(view(id).get("cancellationStatus").asString()).isEqualTo("PENDING");due();worker().runNext();}
            worker().runNext();assertThat(view(id).get("refundStatus").asString()).isEqualTo("PENDING");
            if(scenario.equals("REFUND_DELAYED")){assertThat(payments.settleNext()).isFalse();clock.now=clock.now.plusSeconds(6);}
            finish(id);assertThat(view(id).get("refundStatus").asString()).isEqualTo("SUCCEEDED");
        }
        assertThat(count("payment_refund")).isEqualTo(3);assertThat(count("payment_refund_journal")).isEqualTo(6);assertThat(count("mock_customer_cancellation")).isEqualTo(3);
    }
    @Test void pendingEntitlementLimitsAndKeyReuseAreEnforced()throws Exception{
        UUID id=booking(MockCancellationPolicy.FULL.text());UUID payment=checkouts.get(identity,id).checkout().id();var command=new PaymentRefunds.Command(UUID.randomUUID(),payment,owner,UUID.randomUUID(),new BigDecimal("300.00"),"INR",true,UUID.randomUUID(),UUID.randomUUID());
        var first=payments.submit(command);assertThat(first.completed()).isFalse();assertThat(payments.submit(command)).isEqualTo(first);
        assertThat(payments.submit(new PaymentRefunds.Command(command.operationId(),payment,owner,command.entitlementId(),new BigDecimal("300.0"),"INR",true,command.correlationId(),command.causationId()))).isEqualTo(first);
        var second=new PaymentRefunds.Command(UUID.randomUUID(),payment,owner,UUID.randomUUID(),new BigDecimal("300.00"),"INR",false,UUID.randomUUID(),UUID.randomUUID());
        assertThatThrownBy(()->payments.submit(second)).isInstanceOf(IllegalArgumentException.class);assertThat(count("payment_refund")).isEqualTo(1);
        assertThatThrownBy(()->payments.submit(new PaymentRefunds.Command(command.operationId(),payment,owner,command.entitlementId(),new BigDecimal("1.00"),"INR",true,command.correlationId(),command.causationId()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->jdbc.sql("update payment_refund set status='SUCCEEDED',version=version+1,completed_at=now()").update()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(count("payment_refund_journal")).isZero();clock.now=clock.now.plusSeconds(6);assertThat(payments.settleNext()).isTrue();assertThat(count("payment_refund_journal")).isEqualTo(2);
    }
    @Test void concurrentRefundReservationsCannotOvercommitCapturedFunds()throws Exception{
        UUID id=booking(MockCancellationPolicy.FULL.text());UUID payment=checkouts.get(identity,id).checkout().id();
        try(var pool=Executors.newFixedThreadPool(2)){
            var calls=new ArrayList<Callable<Boolean>>();for(int i=0;i<2;i++)calls.add(()->{try{payments.submit(new PaymentRefunds.Command(UUID.randomUUID(),payment,owner,UUID.randomUUID(),new BigDecimal("300.00"),"INR",true,UUID.randomUUID(),UUID.randomUUID()));return true;}catch(IllegalArgumentException denied){return false;}});
            var outcomes=pool.invokeAll(calls);assertThat(outcomes.stream().map(f->{try{return f.get();}catch(Exception e){throw new IllegalStateException(e);}}).toList()).containsExactlyInAnyOrder(true,false);
        }
        assertThat(count("payment_refund")).isEqualTo(1);assertThat(count("payment_refund_journal")).isZero();
    }
    @Test void unknownRefundLookupPreservesCancelledBookingAndRequiresReview()throws Exception{
        UUID id=booking(MockCancellationPolicy.FULL.text());start(id,quote(id,key()),"SUCCESS",key());worker().runNext();var effects=new AtomicInteger();
        var unavailable=new CancellationRefunds(){public Optional<Cancellation.RefundReceipt> lookup(UUID op){throw new IllegalStateException("Unknown refund outcome");}public Cancellation.RefundReceipt submit(Cancellation c){effects.incrementAndGet();return refundPort.submit(c);}};
        var w=new CancellationWorker(store,supplier,unavailable,clock);for(int i=0;i<5;i++){due();w.runNext();}
        assertThat(view(id).get("cancellationStatus").asString()).isEqualTo("CANCELLED");assertThat(view(id).get("refundStatus").asString()).isEqualTo("REVIEW");assertThat(effects.get()).isZero();assertThat(count("payment_refund")).isZero();
    }
    @Test void pendingReceiptOnLastAttemptKeepsItsReferenceWhenEscalating()throws Exception{
        UUID id=booking(MockCancellationPolicy.FULL.text());start(id,quote(id,key()),"SUCCESS",key());worker().runNext();var reads=new AtomicInteger();
        var delayed=new CancellationRefunds(){public Optional<Cancellation.RefundReceipt> lookup(UUID op){if(reads.incrementAndGet()<5)throw new IllegalStateException("Temporary lookup outage");return refundPort.lookup(op);}public Cancellation.RefundReceipt submit(Cancellation c){return refundPort.submit(c);}};
        var w=new CancellationWorker(store,supplier,delayed,clock);for(int i=0;i<5;i++){due();w.runNext();}
        var state=view(id);assertThat(state.get("refundStatus").asString()).isEqualTo("REVIEW");assertThat(state.get("refundReference").asString()).startsWith("MOCK-REFUND-");assertThat(count("payment_refund")).isEqualTo(1);assertThat(count("payment_refund_journal")).isZero();
    }
    @Test void unknownSupplierDoesNotRefundAndStaleClaimCannotCommit()throws Exception{
        UUID id=booking(MockCancellationPolicy.FULL.text());start(id,quote(id,key()),"SUCCESS",key());var a=store.claim(Duration.ofSeconds(15)).orElseThrow();expireLease();var b=store.claim(Duration.ofSeconds(15)).orElseThrow();
        assertThat(store.finish(a,a.cancellation().reject("OLD",clock.instant()))).isFalse();assertThat(store.finish(b,b.cancellation().uncertain(false,clock.instant()))).isTrue();
        var effects=new AtomicInteger();var unavailable=new CancellationSupplier(){public void verifyCancellable(Checkout c){supplier.verifyCancellable(c);}public Optional<Cancellation.SupplierReceipt> lookup(UUID op){throw new IllegalStateException("Unknown supplier outcome");}public Cancellation.SupplierReceipt cancel(Cancellation c){effects.incrementAndGet();return supplier.cancel(c);}};
        var w=new CancellationWorker(store,unavailable,refundPort,clock);for(int i=0;i<5;i++){due();w.runNext();}
        assertThat(view(id).get("cancellationStatus").asString()).isEqualTo("REVIEW");assertThat(effects.get()).isZero();assertThat(count("payment_refund")).isZero();assertThat(w.runNext()).isFalse();
    }
    @Test void expiredUnexecutedCancellationRejectsWithoutTouchingReservation()throws Exception{
        UUID id=booking(MockCancellationPolicy.FULL.text());start(id,quote(id,key()),"SUCCESS",key());clock.now=clock.now.plusSeconds(301);worker().runNext();
        assertThat(view(id).get("cancellationStatus").asString()).isEqualTo("REJECTED");assertThat(count("mock_customer_cancellation")).isZero();assertThat(count("payment_refund")).isZero();assertThat(jdbc.sql("select status from mock_supplier_reservation").query(String.class).single()).isEqualTo("CONFIRMED");
    }
    @Test void settlementLeaseExpiryRollsBackJournalAndAttemptsAreBounded(){
        UUID id=booking(MockCancellationPolicy.FULL.text());UUID payment=checkouts.get(identity,id).checkout().id();payments.submit(new PaymentRefunds.Command(UUID.randomUUID(),payment,owner,UUID.randomUUID(),new BigDecimal("420.00"),"INR",false,UUID.randomUUID(),UUID.randomUUID()));
        jdbc.sql("create function test_slow_refund() returns trigger language plpgsql as $$ begin perform pg_sleep(0.05); return NEW; end; $$").update();jdbc.sql("create trigger test_slow_refund before insert on payment_refund_journal for each row execute function test_slow_refund()").update();
        var shortLease=new JdbcMockRefunds(jdbc,new TransactionTemplate(manager),json,clock,Duration.ofMillis(10));
        try{for(int i=0;i<5;i++)assertThat(shortLease.settleNext()).isFalse();assertThat(shortLease.settleNext()).isFalse();assertThat(count("payment_refund_journal")).isZero();assertThat(jdbc.sql("select settlement_attempts from payment_refund").query(Integer.class).single()).isEqualTo(5);}
        finally{jdbc.sql("drop trigger test_slow_refund on payment_refund_journal").update();jdbc.sql("drop function test_slow_refund()").update();}
    }
    @Test void realJvmCrashAfterCancellationAndAfterRefundCommitRecoversOnce()throws Exception{
        for(String stage:List.of("cancel","refund")){
            UUID id=booking(MockCancellationPolicy.FULL.text());start(id,quote(id,key()),"SUCCESS",key());if(stage.equals("refund"))worker().runNext();
            assertThat(child("crash-"+stage)).isEqualTo(stage.equals("cancel")?27:28);expireLease();assertThat(child("resume")).isZero();finish(id);assertThat(view(id).get("refundStatus").asString()).isEqualTo("SUCCEEDED");
        }
        assertThat(count("mock_customer_cancellation")).isEqualTo(2);assertThat(count("payment_refund")).isEqualTo(2);assertThat(count("payment_refund_journal")).isEqualTo(4);
    }
    int child(String phase)throws Exception{
        Path log=temp.resolve(phase+".log");String cp=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",cp,"com.travbook.support.CancellationProcess",phase).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("DATABASE_URL",postgres.getJdbcUrl());builder.environment().put("DATABASE_USERNAME",postgres.getUsername());builder.environment().put("DATABASE_PASSWORD",postgres.getPassword());var process=builder.start();boolean done=process.waitFor(30,TimeUnit.SECONDS);if(!done)process.destroyForcibly();assertThat(done).isTrue();int code=process.exitValue();if(code!=0&&code!=27&&code!=28)throw new AssertionError(Files.readString(log));return code;
    }
}
