package com.travbook.cabs.adapter.in.web;
import com.travbook.cabs.application.port.in.DiscoverCabs;
import com.travbook.cabs.domain.model.Cab.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;
@RestController public class CabController{
 private final DiscoverCabs cabs;public CabController(DiscoverCabs cabs){this.cabs=cabs;}
 public record Search(@NotNull Type type,@NotNull Place pickup,@NotNull Place dropoff,@NotNull LocalDate date,@NotBlank String time,LocalDate returnDate,String returnTime,@Min(1) @Max(6) int passengers,@Min(0) @Max(6) int luggage,@Min(0) @Max(12) int hours){}
 public record Reprice(@NotNull UUID searchId){}
 @PostMapping("/api/v1/cabs/search") public Page search(@Valid @RequestBody Search r){return cabs.search(new Criteria(r.type(),r.pickup(),r.dropoff(),r.date(),r.time(),r.returnDate(),r.returnTime(),r.passengers(),r.luggage(),r.hours()));}
 @GetMapping("/api/v1/cab-searches/{id}") public Page page(@PathVariable UUID id,@RequestParam(defaultValue="ALL") CategoryFilter category,@RequestParam(defaultValue="99999999") BigDecimal maxFare,@RequestParam(defaultValue="PRICE") Sort sort){return cabs.page(id,category,maxFare,sort);}
 @GetMapping("/api/v1/cabs/offers/{id}") public Detail detail(@PathVariable UUID id,@RequestParam UUID searchId){return cabs.detail(searchId,id);}
 @PostMapping("/api/v1/cabs/offers/{id}/reprice") public FareCheck reprice(@PathVariable UUID id,@Valid @RequestBody Reprice r){return cabs.reprice(r.searchId(),id);}
}
