package com.travbook.buses.application.port.out;
import com.travbook.buses.domain.model.Bus.*;
import java.time.Instant;
public interface BusSupplier{Inventory search(Criteria criteria,Instant now);Trip fresh(Trip trip,Instant now);}
