package com.travbook.cabs.config;
import com.travbook.cabs.application.port.in.DiscoverCabs;
import com.travbook.cabs.application.port.out.CabSupplier;
import com.travbook.cabs.application.usecase.CabDiscovery;
import com.travbook.cabs.adapter.out.supplier.MockCabSupplier;
import com.travbook.cabs.domain.model.Cab.Snapshot;
import com.travbook.shared.adapter.out.persistence.JdbcSnapshots;
import com.travbook.shared.adapter.out.supplier.BoundedDiscovery;
import com.travbook.shared.application.port.out.SnapshotStore;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
@Configuration public class CabConfiguration{
 @Bean SnapshotStore<Snapshot> cabSnapshots(JdbcClient jdbc,ObjectMapper json){return new JdbcSnapshots<>(jdbc,json,"cab_search",Snapshot.class);}
 @Bean CabSupplier cabSupplier(BoundedDiscovery calls){return new MockCabSupplier(calls);}
 @Bean DiscoverCabs discoverCabs(SnapshotStore<Snapshot> store,CabSupplier supplier,Clock clock){return new CabDiscovery(store,supplier,clock);}
}
