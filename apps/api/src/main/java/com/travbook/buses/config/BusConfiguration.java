package com.travbook.buses.config;
import com.travbook.buses.application.port.in.DiscoverBuses;
import com.travbook.buses.application.port.out.BusSupplier;
import com.travbook.buses.application.usecase.BusDiscovery;
import com.travbook.buses.adapter.out.supplier.MockBusSupplier;
import com.travbook.buses.domain.model.Bus.Snapshot;
import com.travbook.shared.adapter.out.persistence.JdbcSnapshots;
import com.travbook.shared.adapter.out.supplier.BoundedDiscovery;
import com.travbook.shared.application.port.out.SnapshotStore;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
@Configuration public class BusConfiguration{
 @Bean SnapshotStore<Snapshot> busSnapshots(JdbcClient jdbc,ObjectMapper json){return new JdbcSnapshots<>(jdbc,json,"bus_search",Snapshot.class);}
 @Bean BusSupplier busSupplier(BoundedDiscovery calls){return new MockBusSupplier(calls);}
 @Bean DiscoverBuses discoverBuses(SnapshotStore<Snapshot> store,BusSupplier supplier,Clock clock,com.travbook.buses.application.port.in.BusInventory inventory){return new BusDiscovery(store,supplier,clock,inventory);}
}
