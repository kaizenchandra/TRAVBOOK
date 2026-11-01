package com.travbook.holidays.config;
import com.travbook.holidays.application.port.in.ExploreHolidays;
import com.travbook.holidays.application.port.out.*;
import com.travbook.holidays.application.usecase.HolidayService;
import com.travbook.holidays.adapter.out.supplier.MockHolidaySupplier;
import com.travbook.holidays.adapter.out.persistence.JdbcHolidays;
import com.travbook.shared.adapter.out.supplier.BoundedDiscovery;
import com.travbook.customer.application.port.in.Customers;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
@Configuration public class HolidayConfiguration{
 @Bean HolidaySupplier holidaySupplier(BoundedDiscovery calls){return new MockHolidaySupplier(calls);}
 @Bean HolidayStore holidayStore(JdbcClient jdbc,ObjectMapper json,PlatformTransactionManager manager,Clock clock){var tx=new TransactionTemplate(manager);tx.setTimeout(3);return new JdbcHolidays(jdbc,tx,json,clock);}
 @Bean ExploreHolidays exploreHolidays(HolidaySupplier suppliers,HolidayStore store,Customers customers,Clock clock){return new HolidayService(suppliers,store,customers,clock);}
}
