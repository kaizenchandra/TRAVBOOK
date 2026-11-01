package com.travbook.operations.config;
import org.springframework.context.annotation.*;
@Configuration public class OperationsConfiguration {
 @Bean com.travbook.operations.application.port.in.Operations operations(java.util.List<com.travbook.shared.application.port.in.ReviewRecovery> providers){return new com.travbook.operations.application.usecase.OperationsService(providers);}
}
