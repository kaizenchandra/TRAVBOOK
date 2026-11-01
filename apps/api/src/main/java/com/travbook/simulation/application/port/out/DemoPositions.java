package com.travbook.simulation.application.port.out;
import java.util.Optional;
/** Simulator-owned coordinate source; no person/vehicle location is queried. */
public interface DemoPositions {
    record Point(String latitude,String longitude){}
    Optional<Point> atStage(String stage);
}
