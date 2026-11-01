package com.travbook.buses.adapter.in.web;
import com.travbook.buses.application.port.in.DiscoverBuses;
import com.travbook.buses.domain.model.Bus.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;
@RestController public class BusController{
 private final DiscoverBuses buses;public BusController(DiscoverBuses buses){this.buses=buses;}
 public record Search(@NotNull City origin,@NotNull City destination,@NotNull LocalDate date,@NotNull @Size(min=1,max=6) List<@NotNull @Min(5) @Max(120) Integer> passengerAges){}
 public record Selection(@NotNull UUID searchId,@NotNull @Size(min=1,max=6) List<@NotBlank @Pattern(regexp="^[LU][1-8]$") String> seatIds,@NotBlank @Size(max=80) String boardingId,@NotBlank @Size(max=80) String droppingId){}
 @PostMapping("/api/v1/buses/search") public Page search(@Valid @RequestBody Search r){return buses.search(new Criteria(r.origin(),r.destination(),r.date(),r.passengerAges()));}
 @GetMapping("/api/v1/bus-searches/{id}") public Page page(@PathVariable UUID id,@RequestParam(defaultValue="") String operator,@RequestParam(defaultValue="ALL") TypeFilter type,@RequestParam(defaultValue="ALL") AcFilter ac,@RequestParam(defaultValue="") String boarding,@RequestParam(defaultValue="ALL") Period period,@RequestParam(defaultValue="PRICE") Sort sort,@RequestParam(defaultValue="99999999") BigDecimal maxFare,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="5") int size){return buses.page(id,new Filter(operator,type,ac,boarding,period,sort,maxFare,page,size));}
 @GetMapping("/api/v1/buses/trips/{id}") public Detail trip(@PathVariable UUID id,@RequestParam UUID searchId){return buses.trip(searchId,id,false);}
 @GetMapping("/api/v1/buses/trips/{id}/seats") public Detail seats(@PathVariable UUID id,@RequestParam UUID searchId){return buses.trip(searchId,id,true);}
 @PostMapping("/api/v1/buses/trips/{id}/reprice") public FareCheck reprice(@PathVariable UUID id,@Valid @RequestBody Selection r){return buses.reprice(r.searchId(),id,r.seatIds(),r.boardingId(),r.droppingId());}
}
