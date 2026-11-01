package com.travbook.customer.domain.model;
import java.time.Instant;
import java.util.UUID;
public record Customer(UUID id, Instant createdAt) {}

