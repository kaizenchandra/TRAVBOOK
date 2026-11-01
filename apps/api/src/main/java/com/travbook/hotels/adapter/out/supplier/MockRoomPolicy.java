package com.travbook.hotels.adapter.out.supplier;
import com.travbook.hotels.domain.model.HotelOffer;
/** Deterministic provider fixture policy, never an application-port or LIVE-provider rule. */
public final class MockRoomPolicy {
 private MockRoomPolicy(){}
 public static String group(HotelOffer.Plan plan){return plan==HotelOffer.Plan.FAMILY?"FAMILY":plan==HotelOffer.Plan.STUDIO?"STUDIO":"STANDARD";}
 public static int capacity(String group){return group.equals("STANDARD")?2:4;}
}
