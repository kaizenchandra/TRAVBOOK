package com.travbook.rail.adapter.in.web;
import com.travbook.rail.application.port.in.DiscoverTrains;
import com.travbook.rail.domain.model.Rail.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;
@RestController
public class TrainController {
    private final DiscoverTrains trains;public TrainController(DiscoverTrains trains){this.trains=trains;}
    public record Search(@NotBlank @Pattern(regexp="[A-Z]{2,5}") String origin,@NotBlank @Pattern(regexp="[A-Z]{2,5}") String destination,@NotNull LocalDate departureDate,@NotNull Quota quota){}
    @GetMapping("/api/v1/railway-stations") public List<Station> stations(@RequestParam(defaultValue="") String q){return trains.stations(q);}
    @PostMapping("/api/v1/trains/search") public RailViews.Page search(@Valid @RequestBody Search r){return RailViews.Page.of(trains.search(new Criteria(r.origin(),r.destination(),r.departureDate(),r.quota())));}
    @GetMapping("/api/v1/train-searches/{id}") public RailViews.Page page(@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="5") int size,@RequestParam(defaultValue="DEPARTURE") DiscoverTrains.Sort sort,@RequestParam(defaultValue="ALL") DiscoverTrains.ClassFilter travelClass,@RequestParam(defaultValue="ALL") DiscoverTrains.StatusFilter status){return RailViews.Page.of(trains.page(id,page,size,sort,travelClass,status));}
    @GetMapping("/api/v1/trains/{id}") public RailViews.Details journey(@PathVariable UUID id){return RailViews.Details.of(trains.journey(id));}
    @GetMapping("/api/v1/trains/{id}/availability") public RailViews.Check availability(@PathVariable UUID id,@RequestParam TravelClass travelClass,@RequestParam Quota quota){return RailViews.Check.of(trains.availability(id,travelClass,quota));}
}
