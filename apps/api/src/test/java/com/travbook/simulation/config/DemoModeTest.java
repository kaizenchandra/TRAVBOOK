package com.travbook.simulation.config;
import com.travbook.simulation.config.SimulationConfiguration;
import com.travbook.simulation.adapter.in.web.DemoController;
import com.travbook.simulation.application.port.in.DemoServices;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
class DemoModeTest {
    @Test void disabledConfigurationDoesNotExposeTheSimulator(){new ApplicationContextRunner().withUserConfiguration(SimulationConfiguration.class,DemoController.class).withPropertyValues("travbook.demo.enabled=false").run(c->{assertThat(c).doesNotHaveBean(DemoServices.class);assertThat(c).doesNotHaveBean(DemoController.class);});}
    @Test void liveOrSandboxCannotUseDummyConfiguration(){for(String mode:java.util.List.of("LIVE","SANDBOX","mock",""))assertThatThrownBy(()->new SimulationConfiguration().demoStore(null,null,null,mode)).isInstanceOf(IllegalStateException.class).hasMessageContaining("MOCK");}
}
