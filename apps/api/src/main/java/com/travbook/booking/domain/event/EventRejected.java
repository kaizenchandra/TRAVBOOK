package com.travbook.booking.domain.event;
public final class EventRejected extends RuntimeException {
    public enum Code { INVALID_ENVELOPE, EVENT_CONFLICT, ORDER_GAP, INVALID_TRANSITION }
    private final Code code;
    public EventRejected(Code code){super(code.name());this.code=code;}
    public Code code(){return code;}
}
