package com.travbook.search.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntegrationModeTest {
    @ParameterizedTest
    @ValueSource(strings={"SANDBOX","LIVE","mock",""})
    void unimplementedOrInvalidModesFailClosed(String mode) {
        try(var pool=Executors.newFixedThreadPool(1)) {
            assertThatThrownBy(() -> new SearchConfiguration().supplierGateway(pool,new SimpleMeterRegistry(),mode,1500))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Only explicit MOCK");
        }
    }
}
