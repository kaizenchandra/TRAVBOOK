package com.travbook;
import com.travbook.booking.application.port.in.BookingCheckouts;
import com.travbook.booking.application.port.out.*;
import com.travbook.booking.application.usecase.*;
import com.travbook.booking.domain.model.*;
import com.travbook.customer.application.port.in.Customers;
import com.travbook.shared.domain.CustomerIdentity;
import com.travbook.support.TestIssuer;
import com.travbook.trips.domain.model.TripDocument;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"travbook.booking.worker-enabled=false","travbook.booking.events-enabled=false","travbook.booking.checkout-enabled=false"})
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class DocumentIntegrationTest {
    static final TestIssuer issuer=new TestIssuer();
    @Container static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);r.add("travbook.oidc-issuer",issuer::url);r.add("travbook.local-identity",()->true);}
    @Autowired ObjectMapper json;@Autowired JdbcClient jdbc;@Autowired BookingStore bookings;@Autowired BookingCheckouts checkouts;@Autowired CheckoutStore checkoutStore;
    @Autowired CheckoutPayments payments;@Autowired CheckoutSupplier supplier;@Autowired Customers customers;@Autowired Clock clock;
    @LocalServerPort int port;String auth,bob;CustomerIdentity identity;UUID owner;
    @BeforeEach void setup()throws Exception{
        String subject="document-owner-"+UUID.randomUUID();identity=new CustomerIdentity(issuer.url(),subject);owner=customers.resolve(identity).id();auth=issuer.token(subject,"bookings:read bookings:write");bob=issuer.token("bob-"+UUID.randomUUID(),"bookings:read bookings:write");
        // Isolated Testcontainer only. No application volume/history is truncated.
        jdbc.sql("truncate mock_customer_cancellation,booking_aftercare_outbox,booking_cancellation_work,booking_cancellation_command,booking_cancellation,booking_cancellation_quote,trip_document_outbox,trip_document_command,trip_document,payment_refund_outbox,payment_refund_journal,payment_refund,payment_journal,payment_operation,payment_account,mock_supplier_operation,mock_supplier_reservation,booking_checkout_outbox,booking_checkout_work,booking_checkout_command,booking_checkout,booking_delivery_replay,booking_timeline_event,booking_event_delivery,booking_outbox,booking_command,booking_reservation,booking_workflow,booking_intent").update();
    }
    @AfterAll static void close(){issuer.close();}
    CheckoutWorker worker(){return new CheckoutWorker(checkoutStore,q->q.terms(),payments,supplier,clock);}
    UUID fixture(Checkout.Scenario scenario,int steps){
        var dep=OffsetDateTime.now(ZoneOffset.ofHoursMinutes(5,30)).plusDays(2);
        var terms=new FlightTerms("sample-aster","MOCK-fixture","Aster Air",List.of(new FlightTerms.Leg("DEL","BOM",dep,dep.plusHours(2),"Asia/Kolkata",120,0,"MOCK-101")),1,"ECONOMY","Fixture baggage","Fixture cancellation",new FlightTerms.Money(new BigDecimal("450.00"),"INR"),new FlightTerms.Money(new BigDecimal("22.50"),"INR"));
        var q=new BookingQuote(UUID.randomUUID(),1,owner,UUID.randomUUID(),UUID.randomUUID(),"MOCK",clock.instant().plusSeconds(120),1,0,0,terms);UUID id=UUID.randomUUID();
        bookings.create(new Booking(id,q,List.of(new Traveller("Élodie","Example",LocalDate.of(1990,1,1))),"a".repeat(64),clock.instant(),clock.instant(),1,Booking.Status.CREATED,null,UUID.randomUUID(),UUID.randomUUID()),key());
        new BookingWorker(bookings,quote->quote.terms(),clock,Duration.ofSeconds(15),4).runNext();
        if(scenario!=null){checkouts.start(identity,id,scenario,true,key(),UUID.randomUUID());for(int i=0;i<steps;i++)worker().runNext();}return id;
    }
    String key(){return UUID.randomUUID().toString();}
    String path(UUID booking){return "/api/v1/bookings/"+booking+"/documents";}
    HttpResponse<String> call(String method,String path,String token,String key,String body)throws Exception{
        var r=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path));if(token!=null)r.header("Authorization","Bearer "+token);if(key!=null)r.header("Idempotency-Key",key);
        if(method.equals("POST"))r.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body));else r.GET();return HttpClient.newHttpClient().send(r.build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    JsonNode tree(HttpResponse<String> r,int expected){assertThat(r.statusCode()).as(r.body()).isEqualTo(expected);return json.readTree(r.body());}
    JsonNode create(UUID id,String key)throws Exception{return tree(call("POST",path(id),auth,key,"{\"kind\":\"MOCK_CONFIRMATION_SUMMARY\"}"),201);}
    int count(String table){return jdbc.sql("select count(*) from "+table).query(Integer.class).single();}
    @Test void noDocumentUntilBothConfirmedAndCapturedAndNoClientContentAccepted()throws Exception{
        UUID id=fixture(Checkout.Scenario.SUCCESS,2);assertThat(call("POST",path(id),auth,key(),"{\"kind\":\"MOCK_CONFIRMATION_SUMMARY\"}").statusCode()).isEqualTo(409);
        worker().runNext();assertThat(call("GET",path(id),auth,null,"").statusCode()).isEqualTo(409);
        worker().runNext();assertThat(tree(call("GET",path(id),auth,null,""),200).get("items").size()).isZero();
        assertThat(call("POST",path(id),auth,key(),"{\"kind\":\"MOCK_CONFIRMATION_SUMMARY\",\"body\":\"Fake ticket\"}").statusCode()).isEqualTo(400);
        assertThat(call("POST",path(id),auth,null,"{\"kind\":\"MOCK_CONFIRMATION_SUMMARY\"}").statusCode()).isEqualTo(400);
        create(id,key());assertThat(count("trip_document")).isEqualTo(1);
        UUID failed=fixture(Checkout.Scenario.RESERVATION_REJECTED,4);assertThat(call("POST",path(failed),auth,key(),"{\"kind\":\"MOCK_CONFIRMATION_SUMMARY\"}").statusCode()).isEqualTo(409);
    }
    @Test void concurrentGenerationReplaysOneImmutableDocumentAndBalancedPaymentIsUnchanged()throws Exception{
        UUID id=fixture(Checkout.Scenario.SUCCESS,4);String key=key();JsonNode first;
        try(var pool=Executors.newFixedThreadPool(6)){
            var calls=new ArrayList<Callable<JsonNode>>();for(int i=0;i<6;i++)calls.add(()->create(id,key));var results=pool.invokeAll(calls);first=results.getFirst().get();for(var r:results)assertThat(r.get()).isEqualTo(first);
            calls.clear();for(int i=0;i<6;i++)calls.add(()->create(id,key()));for(var r:pool.invokeAll(calls))assertThat(r.get()).isEqualTo(first);
        }
        assertThat(count("trip_document")).isEqualTo(1);assertThat(count("trip_document_outbox")).isEqualTo(1);assertThat(count("payment_journal")).isEqualTo(2);
        var downloaded=call("GET",first.get("downloadUrl").asString(),auth,null,"");assertThat(downloaded.statusCode()).isEqualTo(200);
        assertThat(downloaded.body()).startsWith("MOCK CONFIRMATION SUMMARY — NOT A TICKET OR TAX INVOICE").contains("Élodie Example","472.50 INR","MOCK-SUPPLIER-","MOCK-PAY-","Asia/Kolkata","Departure (local):","+05:30").doesNotContain("1990-01-01",owner.toString(),auth);
        assertThat(TripDocument.digest(downloaded.body())).isEqualTo(first.get("sha256").asString());assertThat(downloaded.body().getBytes(StandardCharsets.UTF_8).length).isEqualTo(first.get("byteLength").asInt());
        assertThat(downloaded.headers().firstValue("Content-Type").orElse("")).contains("text/plain","charset=UTF-8");assertThat(downloaded.headers().firstValue("Content-Disposition").orElse("")).startsWith("attachment; filename=\"travbook-MOCK-summary-");
        assertThat(downloaded.headers().firstValue("Cache-Control").orElse("")).contains("no-store");assertThat(downloaded.headers().firstValue("X-Content-Type-Options").orElse("")).isEqualTo("nosniff");
        assertThat(call("GET",first.get("downloadUrl").asString(),auth,null,"").body()).isEqualTo(downloaded.body());
        assertThatThrownBy(()->jdbc.sql("update trip_document set body='changed'").update()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.sql("select envelope::text from trip_document_outbox").query(String.class).list().toString()).doesNotContain("Élodie","1990-01-01","472.50");
    }
    @Test void scopedOwnerAndBookingDocumentBindingAreEnforced()throws Exception{
        UUID id=fixture(Checkout.Scenario.SUCCESS,4);var d=create(id,key());String download=d.get("downloadUrl").asString();
        assertThat(call("GET",download,null,null,"").statusCode()).isEqualTo(401);assertThat(call("GET",download,issuer.token("limited","quotes:read"),null,"").statusCode()).isEqualTo(403);
        assertThat(call("GET",path(id),bob,null,"").statusCode()).isEqualTo(404);assertThat(call("GET",download,bob,null,"").statusCode()).isEqualTo(404);
        assertThat(call("POST",path(id),bob,key(),"{\"kind\":\"MOCK_CONFIRMATION_SUMMARY\"}").statusCode()).isEqualTo(404);
        UUID other=fixture(Checkout.Scenario.SUCCESS,4);assertThat(call("GET",download.replace(id.toString(),other.toString()),auth,null,"").statusCode()).isEqualTo(404);
        String used=key();create(id,used);assertThat(call("POST",path(other),auth,used,"{\"kind\":\"MOCK_CONFIRMATION_SUMMARY\"}").statusCode()).isEqualTo(409);assertThat(count("trip_document")).isEqualTo(1);
    }
    @Test void eventFailureRollsBackBodyCommandAndEventAndRetryRecovers()throws Exception{
        UUID id=fixture(Checkout.Scenario.SUCCESS,4);String key=key();
        jdbc.sql("create function test_document_failure() returns trigger language plpgsql as $$ begin raise exception 'injected event failure'; end; $$").update();
        jdbc.sql("create trigger test_document_failure before insert on trip_document_outbox for each row execute function test_document_failure()").update();
        try{assertThat(call("POST",path(id),auth,key,"{\"kind\":\"MOCK_CONFIRMATION_SUMMARY\"}").statusCode()).isEqualTo(503);assertThat(count("trip_document")).isZero();assertThat(count("trip_document_command")).isZero();assertThat(count("trip_document_outbox")).isZero();}
        finally{jdbc.sql("drop trigger test_document_failure on trip_document_outbox").update();jdbc.sql("drop function test_document_failure()").update();}
        create(id,key);assertThat(count("trip_document")).isEqualTo(1);assertThat(count("trip_document_command")).isEqualTo(1);assertThat(count("trip_document_outbox")).isEqualTo(1);
    }
}
