package com.travbook.shared.domain;
import java.time.Instant;
import java.util.UUID;
public interface ObservedSnapshot {UUID id();Instant createdAt();Instant expiresAt();}
