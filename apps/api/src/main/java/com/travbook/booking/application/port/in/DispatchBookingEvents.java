package com.travbook.booking.application.port.in;
public interface DispatchBookingEvents {
    enum Outcome { IDLE, DELIVERED, RETRY, DEAD, STALE }
    record Health(long pending,long quarantined,double oldestSeconds) {}
    Health health();
    Outcome runNext();
}
