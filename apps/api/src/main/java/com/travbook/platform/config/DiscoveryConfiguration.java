package com.travbook.platform.config;
import com.travbook.shared.adapter.out.supplier.BoundedDiscovery;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.*;
@Configuration public class DiscoveryConfiguration{
 @Bean(destroyMethod="shutdownNow") ExecutorService multimodalDiscoveryExecutor(){return new ThreadPoolExecutor(4,4,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(8),new ThreadPoolExecutor.AbortPolicy());}
 @Bean BoundedDiscovery boundedDiscovery(@Qualifier("multimodalDiscoveryExecutor") ExecutorService pool,MeterRegistry metrics,@Value("${travbook.integration-mode}") String mode){if(!"MOCK".equals(mode))throw new IllegalStateException("Bus, holiday and cab providers require explicit MOCK until authorized adapters are configured.");return new BoundedDiscovery(pool,metrics,1500);}
}
