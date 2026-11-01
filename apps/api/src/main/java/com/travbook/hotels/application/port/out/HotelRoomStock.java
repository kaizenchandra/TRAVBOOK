package com.travbook.hotels.application.port.out;
import com.travbook.hotels.domain.model.HotelOffer;
import java.time.LocalDate;
public interface HotelRoomStock {int remaining(String property,HotelOffer.Plan plan,LocalDate from,LocalDate to);default int remaining(String property,HotelOffer.Plan plan,LocalDate from,LocalDate to,java.util.UUID excluding){return remaining(property,plan,from,to);}}
