package com.travbook.cabs.application.port.out;
import com.travbook.cabs.domain.model.Cab.*;
import java.time.Instant;
public interface CabSupplier{Inventory search(Criteria criteria,Instant now);Offer reprice(Criteria criteria,Offer offer,Instant now);}
