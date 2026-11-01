package com.travbook.journeys.application.port.out;
import com.travbook.journeys.domain.model.Journey.*;
import com.travbook.shared.domain.CustomerIdentity;
public interface JourneySelection { Selection select(CustomerIdentity identity,Request request); void validate(Quote quote); }
