package com.travbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.travbook.journeys.domain.model.Journey.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
/** Real signed test issuer + PostgreSQL API behind the production web UI; no fixture responses. */
class JourneyOpsBrowserIT extends JourneyReviewIntegrationTest {
 @TempDir Path temp;
 @Test void desktopAndMobileRecoverAllJourneyKinds()throws Exception{for(String viewport:java.util.List.of("desktop","mobile"))for(var kind:Kind.values()){
  day++;var b=reviewed(kind,true);var log=temp.resolve(viewport+"-"+kind+".log");var pb=new ProcessBuilder("node","../../scripts/verify-operations-browser.mjs").redirectErrorStream(true).redirectOutput(log.toFile());pb.environment().put("OPS_TEST_API","http://127.0.0.1:"+port);pb.environment().put("OPS_TEST_OPERATOR",operator());pb.environment().put("OPS_TEST_CUSTOMER",token);pb.environment().put("OPS_TEST_BOOKING",b.id().toString());pb.environment().put("OPS_TEST_VIEWPORT",viewport);pb.environment().put("OPS_TEST_KIND",kind.name());var child=pb.start();boolean done=child.waitFor(45,TimeUnit.SECONDS);if(!done)child.destroyForcibly();assertThat(done).isTrue();assertThat(child.exitValue()).as(Files.readString(log)).isZero();assertThat(advance(b,Status.CONFIRMED).reservation()).isEqualTo(Reservation.CONFIRMED);assertThat(jdbc.sql("select count(*) from journey_recovery_action where booking_id=:id").param("id",b.id()).query(Integer.class).single()).isEqualTo(1);assertThat(jdbc.sql("select count(*) from payment_journal where payment_id=:id").param("id",b.id()).query(Integer.class).single()).isEqualTo(2);
 }}
}
