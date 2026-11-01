package com.travbook.flightprovider.config;
import com.travbook.flightprovider.adapter.in.web.FlightProviderController;
import com.travbook.flightprovider.application.port.in.FlightProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
class FlightProviderModeTest {
    @Test void disabledDoesNotExposeProvider(){new ApplicationContextRunner().withUserConfiguration(FlightProviderConfiguration.class,FlightProviderController.class).withPropertyValues("travbook.demo.enabled=false").run(c->{assertThat(c).doesNotHaveBean(FlightProvider.class);assertThat(c).doesNotHaveBean(FlightProviderController.class);});}
    @Test void rejectsOtherModes(){for(String mode:java.util.List.of("SANDBOX","LIVE","mock",""))assertThatThrownBy(()->new FlightProviderConfiguration().flightProviderStore(null,null,null,mode,null)).isInstanceOf(IllegalStateException.class).hasMessageContaining("MOCK");}
}
