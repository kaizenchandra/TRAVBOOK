package com.travbook.hotels.adapter.in.web;
import com.travbook.hotels.application.port.in.DiscoverHotels;
import com.travbook.hotels.domain.model.HotelCriteria;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
@RestController
public class HotelController {
    private final DiscoverHotels hotels;
    public HotelController(DiscoverHotels hotels){this.hotels=hotels;}
    public record Occupancy(@Min(1) @Max(3) int adults,@NotNull @Size(max=2) List<@NotNull @Min(0) @Max(17) Integer> childAges){HotelCriteria.Occupancy domain(){return new HotelCriteria.Occupancy(adults,childAges);}}
    public record Search(@NotBlank @Size(min=2,max=80) String destination,@NotNull LocalDate checkIn,@NotNull LocalDate checkOut,@NotNull @Size(min=1,max=4) List<@NotNull @Valid Occupancy> rooms) {}
    public record Selection(@NotNull @Size(min=1,max=4) List<@NotNull UUID> offerIds) {}
    @PostMapping("/api/v1/hotels/search") public HotelViews.Page search(@Valid @RequestBody Search r){return HotelViews.Page.of(hotels.search(new HotelCriteria(r.destination(),r.checkIn(),r.checkOut(),r.rooms().stream().map(o->o.domain()).toList())));}
    @GetMapping("/api/v1/hotel-searches/{id}") public HotelViews.Page page(@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="5") int size,
        @RequestParam(defaultValue="PRICE") DiscoverHotels.Sort sort,@RequestParam(defaultValue="ALL") DiscoverHotels.TypeFilter type,@RequestParam(defaultValue="0") int minStars,
        @RequestParam(defaultValue="ALL") DiscoverHotels.Amenity amenity,@RequestParam(defaultValue="false") boolean refundable,@RequestParam(defaultValue="9999999") BigDecimal maxTotal){
        return HotelViews.Page.of(hotels.page(id,new DiscoverHotels.Filters(page,size,sort,type,minStars,amenity,refundable,maxTotal)));
    }
    @GetMapping({"/api/v1/hotels/{id}","/api/v1/hotels/{id}/rooms"}) public HotelViews.Rooms rooms(@PathVariable String id,@RequestParam UUID searchId){return HotelViews.Rooms.of(hotels.rooms(searchId,id));}
    @PostMapping("/api/v1/hotels/offers/{id}/reprice") public HotelViews.Reprice reprice(@PathVariable UUID id,@Valid @RequestBody Selection r){return HotelViews.Reprice.of(hotels.reprice(id,r.offerIds()));}
}
