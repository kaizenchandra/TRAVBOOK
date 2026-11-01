package com.travbook.cabs.application.port.in;
import com.travbook.cabs.domain.model.Cab.*;
import java.util.UUID;
import java.math.BigDecimal;
public interface DiscoverCabs{Page search(Criteria criteria);Page page(UUID id,CategoryFilter category,BigDecimal maxFare,Sort sort);Detail detail(UUID search,UUID offer);FareCheck reprice(UUID search,UUID offer);}
