package com.travbook;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
@Testcontainers class PassengerMigrationTest {
 @Container static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine");
 @Test void v15AllocationAndCompletedRefundHistoryArePreservedWithoutGuessingEntitlements(){
  var source=new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());Flyway.configure().dataSource(source).target("15").load().migrate();var jdbc=JdbcClient.create(source);var json=JsonMapper.builder().build();UUID id=UUID.randomUUID(),quote=UUID.randomUUID(),owner=UUID.randomUUID(),cancel=UUID.randomUUID();
  var quotation=Map.of("id",quote,"owner",owner,"version",1,"selection",Map.of("passengers",List.of(Map.of("name","One"),Map.of("name","Two"))));
  jdbc.sql("insert into rail_ticket_quote(id,customer_id,created_at,expires_at,payload) values(:id,:owner,now(),now()+interval '1 minute',cast(:payload as jsonb))").param("id",quote).param("owner",owner).param("payload",json.writeValueAsString(quotation)).update();
  var refundAmount=Map.of("amount","1400.00","currency","INR");var cancellation=Map.of("id",cancel,"bookingId",id,"owner",owner,"refundAmount",refundAmount);
  var booking=new HashMap<String,Object>();booking.put("id",id);booking.put("version",8);booking.put("quote",quotation);booking.put("status","CANCELLED");booking.put("payment","CAPTURED");booking.put("reservation","CANCELLED");booking.put("step","DONE");booking.put("refund","SUCCEEDED");booking.put("refundReference","MOCK-REFUND-fixture");booking.put("updatedAt","2026-10-31T00:00:00Z");booking.put("cancellation",Map.of("quote",cancellation));
  jdbc.sql("insert into rail_ticket_booking(id,quote_id,customer_id,version,created_at,updated_at,status,payload) values(:id,:quote,:owner,8,now(),now(),'CANCELLED',cast(:payload as jsonb))").param("id",id).param("quote",quote).param("owner",owner).param("payload",json.writeValueAsString(booking)).update();
  jdbc.sql("insert into rail_ticket_cancellation_quote(id,booking_id,customer_id,payload) values(:id,:booking,:owner,cast(:payload as jsonb))").param("id",cancel).param("booking",id).param("owner",owner).param("payload",json.writeValueAsString(cancellation)).update();
  jdbc.sql("insert into rail_provider_reservation(id,stock_key,status,reference,quantity,capacity,active) values(:id,'fixture','CANCELLED',:ref,2,12,false)").param("id",id).param("ref","MOCK-PNR-"+id).update();String before=jdbc.sql("select payload::text from rail_ticket_booking where id=:id").param("id",id).query(String.class).single();
  assertThat(Flyway.configure().dataSource(source).target("16").load().migrate().migrationsExecuted).isEqualTo(1);
  assertThat(jdbc.sql("select payload::text from rail_ticket_booking where id=:id").param("id",id).query(String.class).single()).isEqualTo(before);
  assertThat(jdbc.sql("select count(*) from rail_provider_passenger where reservation_id=:id and not active").param("id",id).query(Integer.class).single()).isEqualTo(2);
  var history=json.readTree(jdbc.sql("select payload::text from rail_ticket_cancellation_result where quote_id=:id").param("id",cancel).query(String.class).single());assertThat(history.get("passengerIndexes").toString()).isEqualTo("[0,1]");assertThat(history.get("refundAmount").get("amount").asString()).isEqualTo("1400.00");assertThat(history.get("reference").asString()).isEqualTo("MOCK-REFUND-fixture");assertThat(jdbc.sql("select count(*) from payment_refund").query(Integer.class).single()).isZero();
 }
}
