package com.travbook;
import com.tngtech.archunit.core.importer.*;
import com.tngtech.archunit.junit.*;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
@AnalyzeClasses(packages="com.travbook",importOptions=ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    @ArchTest static final ArchRule notificationBoundaries=noClasses().that().resideInAPackage("..notifications..").should().dependOnClassesThat().resideInAnyPackage("..booking..","..stays..","..railtickets..","..journeys..","..flightprovider..","..payments..","..customer.adapter..","..customer.config..","..customer.application.usecase..","..customer.application.port.out..");
    @ArchTest static final ArchRule promotionBoundaries=noClasses().that().resideInAPackage("..flightprovider..").should().dependOnClassesThat().resideInAnyPackage("..offers.adapter..","..offers.config..","..offers.application.usecase..","..offers.application.port.out..");
    @ArchTest static final ArchRule couponsDoNotReachBackIntoBooking=noClasses().that().resideInAPackage("..offers..").should().dependOnClassesThat().resideInAnyPackage("..flightprovider..","..booking..","..pricing..","..payments..");
    @ArchTest static final ArchRule fictionalFlightProviderIsIsolated=noClasses().that().resideInAPackage("..flightprovider..").should().dependOnClassesThat()
        .resideInAnyPackage("..booking..","..pricing..","..search..","..payments..","..trips..","..simulation..",
            "..customer.adapter..","..customer.config..","..customer.application.usecase..","..customer.application.port.out..");
    @ArchTest static final ArchRule simulationUsesPublishedContracts=noClasses().that().resideInAPackage("..simulation..").should().dependOnClassesThat()
        .resideInAnyPackage("..customer.adapter..","..customer.config..","..customer.application.usecase..","..customer.application.port.out..",
            "..pricing.adapter..","..pricing.config..","..pricing.application.usecase..","..pricing.application.port.out..",
            "..payments.adapter..","..payments.config..","..payments.application.usecase..","..payments.application.port.out..",
            "..journeys.adapter..","..journeys.config..","..journeys.application.usecase..","..journeys.application.port.out..");
    @ArchTest static final ArchRule pureDomain=noClasses().that().resideInAPackage("..domain..").should().dependOnClassesThat()
        .resideOutsideOfPackages("java..","com.travbook..domain..");
    @ArchTest static final ArchRule inwardApplication=noClasses().that().resideInAPackage("..application..").should().dependOnClassesThat()
        .resideInAnyPackage("..adapter..","..config..","org.springframework..","jakarta.persistence..","tools.jackson..");
    @ArchTest static final ArchRule controllersUsePorts=noClasses().that().resideInAPackage("..adapter.in..").should().dependOnClassesThat()
        .resideInAnyPackage("..adapter.out..","..application.usecase..","..application.port.out..","org.springframework.jdbc..");
    @ArchTest static final ArchRule noContextCycles=slices().matching("com.travbook.(*)..").should().beFreeOfCycles();
    @ArchTest static final ArchRule catalogEncapsulation=noClasses().that().resideInAPackage("..search..").should().dependOnClassesThat()
        .resideInAnyPackage("..catalog.adapter..","..catalog.config..","..catalog.application.usecase..","..catalog.application.port.out..");
    @ArchTest static final ArchRule isolatedPersistence=noClasses().that().resideOutsideOfPackages("..adapter.out.persistence..","..config..")
        .should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..","jakarta.persistence..");
    @ArchTest static final ArchRule pricingUsesPublishedContracts=noClasses().that().resideInAPackage("..pricing..").should().dependOnClassesThat()
        .resideInAnyPackage("..customer.adapter..","..customer.config..","..customer.application.usecase..","..customer.application.port.out..",
            "..search.adapter..","..search.config..","..search.application.usecase..","..search.application.port.out..");
    @ArchTest static final ArchRule bookingUsesPublishedContracts=noClasses().that().resideInAPackage("..booking..").should().dependOnClassesThat()
        .resideInAnyPackage("..pricing.adapter..","..pricing.config..","..pricing.application.usecase..","..pricing.application.port.out..",
            "..search.adapter..","..search.config..","..search.application.usecase..","..search.application.port.out..",
            "..customer.adapter..","..customer.config..","..customer.application.usecase..","..customer.application.port.out..",
            "..payments.adapter..","..payments.config..","..payments.application.usecase..","..payments.application.port.out..");
    @ArchTest static final ArchRule paymentsDoNotReachBackIntoBooking=noClasses().that().resideInAPackage("..payments..").should().dependOnClassesThat().resideInAPackage("..booking..");
    @ArchTest static final ArchRule tripsUsePublishedBookingContracts=noClasses().that().resideInAPackage("..trips..").should().dependOnClassesThat()
        .resideInAnyPackage("..booking.adapter..","..booking.config..","..booking.application.usecase..","..booking.application.port.out..","..payments..");
    @ArchTest static final ArchRule hotelObservationsDoNotEnterFlightOrFinancialContexts=noClasses().that().resideInAPackage("..hotels..").should().dependOnClassesThat()
        .resideInAnyPackage("..search..","..pricing..","..booking..","..payments..","..trips..");
    @ArchTest static final ArchRule railwayObservationsAreIndependent=noClasses().that().resideInAPackage("..rail..").should().dependOnClassesThat()
        .resideInAnyPackage("..search..","..hotels..","..pricing..","..booking..","..payments..","..trips..");
    @ArchTest static final ArchRule staysUsePublishedContracts=noClasses().that().resideInAPackage("..stays..").should().dependOnClassesThat()
        .resideInAnyPackage("..hotels.adapter..","..hotels.config..","..hotels.application.usecase..","..hotels.application.port.out..","..payments.adapter..","..payments.config..","..payments.application.usecase..","..payments.application.port.out..","..customer.adapter..","..customer.config..","..customer.application.usecase..","..customer.application.port.out..","..booking..","..pricing..");
    @ArchTest static final ArchRule railTicketsUsePublishedContracts=noClasses().that().resideInAPackage("..railtickets..").should().dependOnClassesThat()
        .resideInAnyPackage("..rail.adapter..","..rail.config..","..rail.application.usecase..","..rail.application.port.out..","..payments.adapter..","..payments.config..","..payments.application.usecase..","..payments.application.port.out..","..customer.adapter..","..customer.config..","..customer.application.usecase..","..customer.application.port.out..","..stays..","..booking..","..pricing..");
    @ArchTest static final ArchRule newDiscoveryDoesNotEnterFulfilment=noClasses().that().resideInAnyPackage("..buses..","..cabs..","..holidays..").should().dependOnClassesThat().resideInAnyPackage("..booking..","..stays..","..railtickets..","..payments..","..customer.adapter..","..customer.application.usecase..","..customer.application.port.out..");
    @ArchTest static final ArchRule journeysUsePublishedContracts=noClasses().that().resideInAPackage("..journeys..").should().dependOnClassesThat().resideInAnyPackage("..buses.adapter..","..buses.config..","..buses.application.usecase..","..buses.application.port.out..","..cabs.adapter..","..cabs.config..","..cabs.application.usecase..","..cabs.application.port.out..","..holidays.adapter..","..holidays.config..","..holidays.application.usecase..","..holidays.application.port.out..","..payments.adapter..","..payments.config..","..payments.application.usecase..","..payments.application.port.out..","..customer.adapter..","..customer.config..","..customer.application.usecase..","..customer.application.port.out..","..stays..","..railtickets..","..booking..","..pricing..");
    @ArchTest static final ArchRule operationsUsePublishedRecoveryOnly=noClasses().that().resideInAPackage("..operations..").should().dependOnClassesThat().resideInAnyPackage("..stays..","..railtickets..","..payments..","..booking..");
}

