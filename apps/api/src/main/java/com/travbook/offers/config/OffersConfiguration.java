package com.travbook.offers.config;
import com.travbook.offers.application.port.in.Coupons;
import com.travbook.offers.application.port.out.CouponStore;
import com.travbook.offers.application.usecase.CouponService;
import com.travbook.offers.adapter.out.persistence.JdbcCoupons;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
@Configuration
public class OffersConfiguration {
    @Bean CouponStore couponStore(JdbcClient jdbc,PlatformTransactionManager manager,ObjectMapper json){var tx=new TransactionTemplate(manager);tx.setTimeout(3);return new JdbcCoupons(jdbc,tx,json);}
    @Bean Coupons coupons(CouponStore store,Clock clock){return new CouponService(store,clock);}
}
