package com.travbook.booking.application.port.out;
import java.time.Duration;
import java.util.*;
public interface BookingEventOutbox {
    int MAX_ATTEMPTS=5;
    record Claim(UUID eventId,UUID aggregateId,long version,String envelope,UUID token,int attempt,boolean exhausted) {}
    record Backlog(long pending,long quarantined,double oldestSeconds) {}
    Backlog backlog();
    Optional<Claim> claim(Duration lease);
    boolean acknowledge(Claim claim);
    boolean fail(Claim claim,String code,boolean permanent);
}
