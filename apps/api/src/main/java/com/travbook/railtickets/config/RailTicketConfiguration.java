package com.travbook.railtickets.config;
import com.travbook.railtickets.application.port.in.*;
import com.travbook.railtickets.application.port.out.*;
import com.travbook.railtickets.application.usecase.*;
import com.travbook.railtickets.adapter.out.commerce.*;
import com.travbook.railtickets.adapter.out.persistence.*;
import com.travbook.customer.application.port.in.Customers;
import com.travbook.rail.application.port.in.DiscoverTrains;
import com.travbook.payments.application.port.in.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
@Configuration
public class RailTicketConfiguration {
 @Bean RailTicketStore railTicketStore(JdbcClient jdbc,PlatformTransactionManager manager,ObjectMapper json,Clock clock){var tx=new TransactionTemplate(manager);tx.setTimeout(3);return new JdbcRailTicketStore(jdbc,tx,json,clock);}
 @Bean RailTicketSelection railTicketSelection(DiscoverTrains trains){return new RailSelectionAdapter(trains);}
 @Bean RailReservations railReservations(JdbcClient jdbc,PlatformTransactionManager manager,ObjectMapper json,Clock clock,RailTicketSelection selection){var tx=new TransactionTemplate(manager);tx.setTimeout(3);return new DurableMockRailReservations(jdbc,tx,json,clock,selection);}
 @Bean RailTicketEffects railTicketEffects(PaymentOperations payments,PaymentRefunds refunds,RailReservations supplier){return new RailTicketEffectAdapter(payments,refunds,supplier);}
 @Bean RailTickets railTickets(Customers customers,RailTicketSelection selection,RailTicketStore store,Clock clock){return new RailTicketService(customers,selection,store,clock);}
 @Bean ProcessRailTickets processRailTickets(RailTicketStore store,RailTicketSelection selection,RailTicketEffects effects,Clock clock){return new RailTicketWorker(store,selection,effects,clock);}
 @Bean com.travbook.shared.application.port.in.ReviewRecovery rail_ticketRecovery(RailTicketStore store,RailTicketEffects effects){return new RailTicketRecovery(store,effects);}
}
