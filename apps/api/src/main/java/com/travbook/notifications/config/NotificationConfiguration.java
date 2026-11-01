package com.travbook.notifications.config;
import com.travbook.notifications.application.port.out.*;
import com.travbook.notifications.application.usecase.NotificationService;
import com.travbook.notifications.adapter.out.persistence.*;
import com.travbook.customer.application.port.in.*;
import com.travbook.shared.application.port.LifecycleSource;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
@Configuration public class NotificationConfiguration {
    private TransactionTemplate tx(PlatformTransactionManager manager){var tx=new TransactionTemplate(manager);tx.setTimeout(3);return tx;}
    @Bean NotificationStore notificationStore(JdbcClient jdbc,PlatformTransactionManager manager,ObjectMapper json){return new JdbcNotificationStore(jdbc,tx(manager),json);}
    @Bean NotificationGateway notificationGateway(JdbcClient jdbc,PlatformTransactionManager manager,@Value("${travbook.integration-mode}")String mode){if(!"MOCK".equals(mode))throw new IllegalStateException("Notification delivery requires MOCK until an approved adapter is configured");return new MockNotificationGateway(jdbc,tx(manager));}
    @Bean NotificationService notificationService(NotificationStore store,NotificationGateway gateway,Customers customers,CustomerOrganizer organizer,List<LifecycleSource> sources,Clock clock){return new NotificationService(store,gateway,customers,organizer,sources,clock);}
}
