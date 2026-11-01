package com.travbook.flightprovider.domain.model;
import com.travbook.shared.domain.TravelMoney;
import java.time.Instant;
import java.util.UUID;
public record FlightPromotion(UUID awardId,UUID owner,UUID quoteId,String code,int campaignVersion,TravelMoney originalTotal,TravelMoney discount,TravelMoney total,Instant validUntil,String terms) {}
