package com.travbook.shared.domain;
public record CustomerIdentity(String issuer, String subject) {
    public CustomerIdentity {
        if(issuer==null || issuer.isBlank() || issuer.length()>512 || subject==null || subject.isBlank() || subject.length()>255)
            throw new IllegalArgumentException("Invalid authenticated identity");
    }
}

