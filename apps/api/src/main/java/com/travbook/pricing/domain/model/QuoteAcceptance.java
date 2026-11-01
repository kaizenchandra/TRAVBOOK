package com.travbook.pricing.domain.model;
import java.time.Instant;
import java.util.UUID;
public record QuoteAcceptance(UUID id,UUID quoteId,int quoteVersion,Instant acceptedAt,String mode) {}

