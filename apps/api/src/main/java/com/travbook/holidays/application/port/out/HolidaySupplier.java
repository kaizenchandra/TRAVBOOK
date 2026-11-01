package com.travbook.holidays.application.port.out;
import com.travbook.holidays.domain.model.Holiday;
import java.time.Instant;
public interface HolidaySupplier{Holiday.Inventory catalog(String destination,Instant now);Holiday.Package detail(String id,Instant now);}
