package com.travbook;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
/** Explicit browser gate, run after the production web image is available; no response fixtures. */
class OpsBrowserIT extends ReviewIntegrationTest {
 @Test void desktopAndMobileUseRealMfaClaimProtectedApi()throws Exception{
  for(String viewport:java.util.List.of("desktop","mobile")){
   var b=reviewed(true);Path log=temp.resolve("ops-"+viewport+".log");
   var process=new ProcessBuilder("node","../../scripts/verify-operations-browser.mjs").redirectErrorStream(true).redirectOutput(log.toFile());
   process.environment().put("OPS_TEST_API","http://127.0.0.1:"+port);
   process.environment().put("OPS_TEST_OPERATOR",operator("browser-operator"));
   process.environment().put("OPS_TEST_CUSTOMER",token);
   process.environment().put("OPS_TEST_BOOKING",b.id().toString());
   process.environment().put("OPS_TEST_VIEWPORT",viewport);
   var child=process.start();boolean done=child.waitFor(45,TimeUnit.SECONDS);if(!done)child.destroyForcibly();assertThat(done).as("Browser test deadline").isTrue();assertThat(child.exitValue()).as(Files.readString(log)).isZero();
   assertThat(finish(b.id()).status()).isEqualTo(com.travbook.railtickets.domain.model.RailTicket.Status.ISSUED);
   assertThat(count("rail_ticket_recovery_action","booking_id",b.id())).isEqualTo(1);
   assertThat(count("rail_provider_operation","reservation_id",b.id())).isEqualTo(1);
   assertThat(count("payment_journal","payment_id",b.id())).isEqualTo(2);
  }
 }
 @Test void desktopAndMobileReconcileProvenLegacyOutcomes()throws Exception{
  for(String viewport:java.util.List.of("desktop","mobile")){
   var b=legacy(finish(book(quote(),com.travbook.railtickets.domain.model.RailTicket.Scenario.SUCCESS).id()),true);Path log=temp.resolve("ops-"+viewport+".log");
   var process=new ProcessBuilder("node","../../scripts/verify-operations-browser.mjs").redirectErrorStream(true).redirectOutput(log.toFile());
   process.environment().put("OPS_TEST_LEGACY","true");
   process.environment().put("OPS_TEST_API","http://127.0.0.1:"+port);
   process.environment().put("OPS_TEST_OPERATOR",operator("browser-operator"));
   process.environment().put("OPS_TEST_CUSTOMER",token);
   process.environment().put("OPS_TEST_BOOKING",b.id().toString());
   process.environment().put("OPS_TEST_VIEWPORT",viewport);
   var child=process.start();boolean done=child.waitFor(45,TimeUnit.SECONDS);if(!done)child.destroyForcibly();assertThat(done).as("Browser test deadline").isTrue();assertThat(child.exitValue()).as(Files.readString(log)).isZero();
   assertThat(finish(b.id()).status()).isEqualTo(com.travbook.railtickets.domain.model.RailTicket.Status.ISSUED);
   assertThat(count("rail_ticket_legacy_proof","booking_id",b.id())).isEqualTo(1);
   assertThat(count("rail_ticket_review_checkpoint","booking_id",b.id())).isZero();
   assertThat(count("rail_ticket_recovery_action","booking_id",b.id())).isEqualTo(1);
   assertThat(count("rail_provider_operation","reservation_id",b.id())).isEqualTo(1);
   assertThat(count("payment_journal","payment_id",b.id())).isEqualTo(2);
  }
 }
}
