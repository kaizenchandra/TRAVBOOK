package com.travbook.buses.application.port.in;
import java.time.Instant;
import java.util.*;
/** Published provider inventory observation; no booking or customer details leak. */
public interface BusInventory { Set<String> unavailable(String supplier,UUID trip,Instant now); }
