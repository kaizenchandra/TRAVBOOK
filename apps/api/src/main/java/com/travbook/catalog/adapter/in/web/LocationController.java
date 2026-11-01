package com.travbook.catalog.adapter.in.web;
import com.travbook.catalog.application.port.in.LookupLocations;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {
    private final LookupLocations locations;
    public LocationController(LookupLocations locations) { this.locations = locations; }
    public record LocationView(String id,String name,String city,String country,String timezone) {}
    private LocationView view(com.travbook.catalog.domain.model.Location l) { return new LocationView(l.id(),l.name(),l.city(),l.country(),l.timezone()); }
    @GetMapping public List<LocationView> search(@RequestParam(defaultValue="") String q) { return locations.search(q).stream().map(this::view).toList(); }
    @GetMapping("/{id}") public LocationView get(@PathVariable String id) { return view(locations.get(id)); }
}

