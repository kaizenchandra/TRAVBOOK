package com.travbook.simulation.adapter.out.supplier;
import com.travbook.simulation.application.port.out.DemoPositions;
import java.util.*;
/** Explicit fictional route fixtures, isolated from application policy. */
public final class FixedDemoPositions implements DemoPositions {
    private static final Map<String,Point> POINTS=Map.of(
        "ASSIGNED",new Point("28.550000","77.090000"),
        "EN_ROUTE",new Point("28.552000","77.095000"),
        "ARRIVED",new Point("28.556000","77.100000"),
        "IN_PROGRESS",new Point("28.580000","77.130000"),
        "COMPLETED",new Point("28.610000","77.200000"));
    public Optional<Point> atStage(String stage){return Optional.ofNullable(POINTS.get(stage));}
}
