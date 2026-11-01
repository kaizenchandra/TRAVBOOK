package com.travbook.search.adapter.in.web;
import com.travbook.search.application.port.in.DiscoverFlights;
import com.travbook.search.domain.model.FlightQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/flights")
public class FlightController {
    private final DiscoverFlights flights;
    public FlightController(DiscoverFlights flights) { this.flights=flights; }
    public record SearchRequest(@NotBlank @Pattern(regexp="[A-Z]{3}") String origin,@NotBlank @Pattern(regexp="[A-Z]{3}") String destination,
        @NotNull LocalDate departureDate,LocalDate returnDate,@Min(1) @Max(9) int adults,@Min(0) @Max(8) int children,
        @Min(0) @Max(9) int infants,@NotNull FlightQuery.Cabin cabin) {
        FlightQuery query() { return new FlightQuery(origin,destination,departureDate,returnDate,adults,children,infants,cabin); }
    }
    @PostMapping("/search") public FlightViews.PageView search(@Valid @RequestBody SearchRequest request) { return FlightViews.PageView.of(flights.search(request.query())); }
    @GetMapping("/searches/{id}") public FlightViews.PageView page(@PathVariable UUID id,@RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="5") int size,@RequestParam(defaultValue="false") boolean nonstop,
        @RequestParam(defaultValue="PRICE") DiscoverFlights.Sort sort,@RequestParam(defaultValue="") String airline) {
        return FlightViews.PageView.of(flights.page(id,page,size,nonstop,sort,airline));
    }
    @GetMapping("/offers/{id}") public FlightViews.OfferView offer(@PathVariable UUID id) { return FlightViews.OfferView.of(flights.offer(id)); }
    @PostMapping("/offers/{id}/reprice") public FlightViews.RepriceView reprice(@PathVariable UUID id) { return FlightViews.RepriceView.of(flights.reprice(id)); }
}

