package com.travbook.journeys.config;
import com.travbook.journeys.application.port.in.*;
import com.travbook.journeys.application.port.out.*;
import com.travbook.journeys.application.usecase.*;
import com.travbook.journeys.adapter.out.commerce.*;
import com.travbook.journeys.adapter.out.persistence.*;
import com.travbook.customer.application.port.in.Customers;
import com.travbook.buses.application.port.in.DiscoverBuses;
import com.travbook.cabs.application.port.in.DiscoverCabs;
import com.travbook.holidays.application.port.in.ExploreHolidays;
import com.travbook.payments.application.port.in.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
@Configuration public class JourneyConfiguration {
 private TransactionTemplate tx(PlatformTransactionManager m){var t=new TransactionTemplate(m);t.setTimeout(3);return t;}
 @Bean JourneyStore journeyStore(JdbcClient j,PlatformTransactionManager m,ObjectMapper json,Clock c){return new JdbcJourneyStore(j,tx(m),json,c);}
 @Bean JdbcJourneySupplier journeySupplier(JdbcClient j,PlatformTransactionManager m,ObjectMapper json,Clock c){return new JdbcJourneySupplier(j,tx(m),json,c);}
 @Bean JourneySelection journeySelection(DiscoverBuses b,DiscoverCabs c,ExploreHolidays h){return new DiscoverySelectionAdapter(b,c,h);}
 @Bean JourneyEffects journeyEffects(PaymentOperations p,PaymentRefunds r,JourneySupplier s,JourneyStore store){return new JourneyEffectAdapter(p,r,s,store);}
 @Bean Journeys journeys(Customers c,JourneySelection s,JourneyStore store,Clock clock){return new JourneyService(c,s,store,clock);}
 @Bean com.travbook.shared.application.port.in.ReviewRecovery busJourneyRecovery(JourneyStore s,JourneyEffects e,JourneyCareRecovery care){return new JourneyRecovery(com.travbook.shared.domain.Recovery.Kind.BUS,s,e,care);}
 @Bean com.travbook.shared.application.port.in.ReviewRecovery holidayJourneyRecovery(JourneyStore s,JourneyEffects e,JourneyCareRecovery care){return new JourneyRecovery(com.travbook.shared.domain.Recovery.Kind.HOLIDAY,s,e,care);}
 @Bean com.travbook.shared.application.port.in.ReviewRecovery cabJourneyRecovery(JourneyStore s,JourneyEffects e,JourneyCareRecovery care){return new JourneyRecovery(com.travbook.shared.domain.Recovery.Kind.CAB,s,e,care);}
 @Bean JourneyCareStore journeyCareStore(JdbcClient j,PlatformTransactionManager m,ObjectMapper json,Clock c,JourneyStore base){return new JdbcJourneyCare(j,tx(m),json,c,base);}
 @Bean JourneyCareSupplier journeyCareSupplier(JdbcClient j,PlatformTransactionManager m,ObjectMapper json){return new JdbcJourneyCareSupplier(j,tx(m),json);}
 @Bean JourneyCareEffects journeyCareEffects(JourneyCareSupplier s,PaymentOperations p,PaymentRefunds r,JourneySupplier current,JourneyStore store,JourneyCareStore care){return new JourneyCareEffectAdapter(s,p,r,current,store,care);}
 @Bean AdvancedJourneys advancedJourneys(Customers c,JourneyStore b,JourneyCareStore s,JourneySelection v,JourneyCabDispatch d,Clock clock){return new AdvancedJourneyService(c,b,s,v,d,clock);}
 @Bean JourneyCareRecovery careRecovery(JourneyCareStore s,JourneyCareEffects e,JourneyStore b){return new CareRecovery(s,e,b);}
 @Bean ProcessJourneyCare processJourneyCare(JourneyCareStore s,JourneyStore b,JourneyCareEffects e,Clock c){return new JourneyCareWorker(s,b,e,c);}
 @Bean ProcessJourneys processJourneys(JourneyStore s,JourneySelection v,JourneyEffects e,Clock c){return new JourneyWorker(s,v,e,c);}
}
