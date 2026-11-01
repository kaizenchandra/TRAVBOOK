package com.travbook.hotels.adapter.out.persistence;
import com.travbook.hotels.application.port.out.HotelRoomStock;
import com.travbook.hotels.domain.model.HotelOffer;
import org.springframework.jdbc.core.simple.JdbcClient;
import java.time.LocalDate;
public final class JdbcHotelRoomStock implements HotelRoomStock {
 private final JdbcClient jdbc;public JdbcHotelRoomStock(JdbcClient jdbc){this.jdbc=jdbc;}
 public int remaining(String property,HotelOffer.Plan plan,LocalDate from,LocalDate to){return remaining(property,plan,from,to,new java.util.UUID(0,0));}
 public int remaining(String property,HotelOffer.Plan plan,LocalDate from,LocalDate to,java.util.UUID excluding){String group=com.travbook.hotels.adapter.out.supplier.MockRoomPolicy.group(plan);int used=jdbc.sql("select coalesce(max(used),0) from (select sum(quantity)::integer used from hotel_room_allocation where property_id=:property and room_group=:group and night>=:start and night<:end and active and reservation_id<>:excluding group by night) n").param("excluding",excluding).param("property",property).param("group",group).param("start",from).param("end",to).query(Integer.class).single();return Math.max(0,com.travbook.hotels.adapter.out.supplier.MockRoomPolicy.capacity(group)-used);}
}
