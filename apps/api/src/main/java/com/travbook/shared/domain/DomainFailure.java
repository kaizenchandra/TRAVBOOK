package com.travbook.shared.domain;
public final class DomainFailure extends RuntimeException {
    public enum Code { VALIDATION_FAILED, NOT_FOUND, SEARCH_EXPIRED, OFFER_EXPIRED, OFFER_UNAVAILABLE, SUPPLIERS_UNAVAILABLE, QUOTE_EXPIRED, QUOTE_VERSION_CONFLICT, IDEMPOTENCY_CONFLICT, QUOTE_NOT_ACCEPTED, BOOKING_CONFLICT, PROFILE_VERSION_CONFLICT, PROFILE_IDENTITY_CONFLICT }
    private final Code code;
    public DomainFailure(Code code, String message) { super(message); this.code = code; }
    public Code code() { return code; }
}

