package org.matsim.prepare.ptRouteTrim;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.utils.gis.shp2matsim.ShpGeometryUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import static org.junit.Assert.*;

public class TransitRouteTrimmerTest {

    private TransitRouteTrimmer transitRouteTrimmer;
    private Scenario scenario;

    /**
     * This test class examines three different types of routes:
     * allIn: all stops of transitRoute are within zone
     * halfIn: one end of route is within the zone, one outside of the zone
     * middleIn: both ends of route are outside of zone, but middle in within zone
     *
     * The following enum stores the transitRoute and transitLine Ids for each type
     */
    public enum routeType {
        allIn(Id.create("265---17372_700", TransitLine.class),
                Id.create("265---17372_700_0", TransitRoute.class)),
        halfIn(Id.create("184---17340_700", TransitLine.class),
                Id.create("184---17340_700_15", TransitRoute.class)),
        middleIn(Id.create("161---17326_700", TransitLine.class),
                Id.create("161---17326_700_21", TransitRoute.class));

        public final Id<TransitLine> transitLineId;
        public final Id<TransitRoute> transitRouteId;

        routeType(Id<TransitLine> transitLineId, Id<TransitRoute> transitRouteId) {
            this.transitLineId = transitLineId;
            this.transitRouteId = transitRouteId;
        }
    }

    @Before
    public void prepare() throws MalformedURLException {
        final String inScheduleFile = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.5-10pct/input/berlin-v5.5-transit-schedule.xml.gz";
        final String inVehiclesFile = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.5-10pct/input/berlin-v5.5-transit-vehicles.xml.gz";
        final String inNetworkFile = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.5-10pct/input/berlin-v5.5-network.xml.gz";
        final String zoneShpFile = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/projects/avoev/shp-files/shp-berlin-hundekopf-areas/berlin_hundekopf.shp";

        Config config = ConfigUtils.createConfig();
        config.transit().setTransitScheduleFile(inScheduleFile);
        config.network().setInputFile(inNetworkFile);
        config.vehicles().setVehiclesFile(inVehiclesFile);

        scenario = ScenarioUtils.loadScenario(config);

        List<PreparedGeometry> geometries = ShpGeometryUtils.loadPreparedGeometries(new URL(zoneShpFile));
        transitRouteTrimmer = new TransitRouteTrimmer(scenario.getTransitSchedule(), scenario.getVehicles(), geometries);

    }


    /*
    Part 1: these tests check whether the three route types are actually configured as described above
     */

    /**
     * In the allIn scenario, the transitRoute in question should have all stops within zone.
     */
    @Test
    public void test_AllIn() {
        Id<TransitLine> transitLineId = routeType.allIn.transitLineId;
        Id<TransitRoute> transitRouteId = routeType.allIn.transitRouteId;

        TransitRoute transitRoute = scenario.getTransitSchedule().getTransitLines().get(transitLineId).getRoutes().get(transitRouteId);
        int stopsTotal = transitRoute.getStops().size();
        int stopsInZone = 0;
        int stopsOutsideZone = 0;
        for (TransitRouteStop stop : transitRoute.getStops()) {
            if (transitRouteTrimmer.getStopsInZone().contains(stop.getStopFacility().getId())) {
                stopsInZone++;
            } else {
                stopsOutsideZone++;
            }
        }

        assertEquals("There should be no stops outside of zone", 0, stopsOutsideZone);
        assertEquals("All stops should be inside the zone", stopsTotal, stopsInZone);
    }

    /**
     * In the halfIn scenario, the transitRoute in question begins outside of the zone and
     * ends within the zone.
     */
    @Test
    public void test_HalfIn() {
        Id<TransitLine> transitLineId = routeType.halfIn.transitLineId;
        Id<TransitRoute> transitRouteId = routeType.halfIn.transitRouteId;
        TransitRoute transitRoute = scenario.getTransitSchedule().getTransitLines().get(transitLineId).getRoutes().get(transitRouteId);
        int sizeOld = transitRoute.getStops().size();
        int inCnt = countStopsInZone(transitRoute);
        int outCnt = countStopsOutsideZone(transitRoute);
        Set<Id<TransitStopFacility>> stopsInZone = transitRouteTrimmer.getStopsInZone();

        Id<TransitStopFacility> firstStopId = transitRoute.getStops().get(0).getStopFacility().getId();
        Id<TransitStopFacility> lastStopId = transitRoute.getStops().get(sizeOld - 1).getStopFacility().getId();

        assertNotEquals("The Route should not be entirely inside of the zone", sizeOld, inCnt);
        assertNotEquals("The Route should not be entirely outside of the zone", sizeOld, outCnt);
        assertFalse(stopsInZone.contains(firstStopId));
        assertTrue(stopsInZone.contains(lastStopId));

    }

    /**
     * In the MiddleIn scenario, the transitRoute in question begins outside of the zone then dips
     * into the zone, and finally leaves the zone once again
     */
    @Test
    public void test_MiddleIn() {
        Id<TransitLine> transitLineId = routeType.middleIn.transitLineId;
        Id<TransitRoute> transitRouteId = routeType.middleIn.transitRouteId;
        TransitRoute transitRoute = scenario.getTransitSchedule().getTransitLines().get(transitLineId).getRoutes().get(transitRouteId);
        int sizeOld = transitRoute.getStops().size();
        int inCnt = countStopsInZone(transitRoute);
        int outCnt = countStopsOutsideZone(transitRoute);
        Set<Id<TransitStopFacility>> stopsInZone = transitRouteTrimmer.getStopsInZone();

        Id<TransitStopFacility> firstStopId = transitRoute.getStops().get(0).getStopFacility().getId();
        Id<TransitStopFacility> lastStopId = transitRoute.getStops().get(sizeOld - 1).getStopFacility().getId();

        assertNotEquals("The Route should not be entirely inside of the zone", sizeOld, inCnt);
        assertNotEquals("The Route should not be entirely outside of the zone", sizeOld, outCnt);
        assertFalse(stopsInZone.contains(firstStopId));
        assertFalse(stopsInZone.contains(lastStopId));

    }

    /*
    Part 2: These tests check functionality of all four trimming methods. For each trimming
    method, all three route types are checked.
     */

    /**
     * trimming method: DeleteRoutesEntirelyInsideZone.
     * route scenario: AllIn
     * The testRoute should be deleted since all stops are within the zone.
     */
    @Test
    public void testDeleteRoutesEntirelyInsideZone_AllIn() {

        Id<TransitLine> transitLineId = routeType.allIn.transitLineId;

        // Modification
        Set<Id<TransitLine>> linesToModify = Collections.singleton(transitLineId);
        transitRouteTrimmer.removeEmptyLines = false;
        transitRouteTrimmer.modifyTransitLinesFromTransitSchedule(linesToModify, TransitRouteTrimmer.modMethod.DeleteRoutesEntirelyInsideZone);

        // After Trim
        TransitSchedule transitScheduleNew = transitRouteTrimmer.getTransitScheduleNew();
        assertTrue("Schedule should include empty transit line",
                transitScheduleNew.getTransitLines().containsKey(transitLineId));
        assertEquals("transitLine should no longer contain any routes",
                transitScheduleNew.getTransitLines().get(transitLineId).getRoutes().size(), 0);

    }

    /**
     * trimming method: DeleteRoutesEntirelyInsideZone.
     * route scenario: HalfIn
     * The testRoute should be retained and left unmodified,
     * since some stops are outside the zone.
     */
    @Test
    public void testDeleteRoutesEntirelyInsideZone_HalfIn() {

        Id<TransitLine> transitLineId = routeType.halfIn.transitLineId;
        Id<TransitRoute> transitRouteId = routeType.halfIn.transitRouteId;

        // Before trim
        TransitRoute transitRouteOld = scenario.getTransitSchedule().getTransitLines().get(transitLineId).getRoutes().get(transitRouteId);
        int stopCntOld = transitRouteOld.getStops().size();

        // Modification
        Set<Id<TransitLine>> linesToModify = Collections.singleton(transitLineId);
        transitRouteTrimmer.removeEmptyLines = false;
        transitRouteTrimmer.modifyTransitLinesFromTransitSchedule(linesToModify, TransitRouteTrimmer.modMethod.DeleteRoutesEntirelyInsideZone);

        // After trim
        TransitSchedule transitScheduleNew = transitRouteTrimmer.getTransitScheduleNew();

        assertTrue("Schedule should include transit line",
                transitScheduleNew.getTransitLines().containsKey(transitLineId));

        TransitLine transitLine = transitScheduleNew.getTransitLines().get(transitLineId);
        assertTrue("Schedule should include transit route",
                transitLine.getRoutes().containsKey(transitRouteId));

        TransitRoute transitRoute = transitLine.getRoutes().get(transitRouteId);
        int stopCntNew = transitRoute.getStops().size();
        assertEquals("transitRoute should contain same number of stops as before modification",
                stopCntOld, stopCntNew);

    }

    /**
     * trimming method: DeleteRoutesEntirelyInsideZone.
     * route scenario: MiddleIn
     * The testRoute should be retained and left unmodified,
     * since some stops are outside the zone.
     */
    @Test
    public void testDeleteRoutesEntirelyInsideZone_MiddleIn() {

        Id<TransitLine> transitLineId = routeType.middleIn.transitLineId;
        Id<TransitRoute> transitRouteId = routeType.middleIn.transitRouteId;

        // Before trim
        TransitRoute transitRouteOld = scenario.getTransitSchedule().getTransitLines().get(transitLineId).getRoutes().get(transitRouteId);
        int stopCntOld = transitRouteOld.getStops().size();

        // Modification
        Set<Id<TransitLine>> linesToModify = Collections.singleton(transitLineId);
        transitRouteTrimmer.removeEmptyLines = false;
        transitRouteTrimmer.modifyTransitLinesFromTransitSchedule(linesToModify, TransitRouteTrimmer.modMethod.DeleteRoutesEntirelyInsideZone);

        // Aftre trim
        TransitSchedule transitScheduleNew = transitRouteTrimmer.getTransitScheduleNew();

        assertTrue("Schedule should include transit line",
                transitScheduleNew.getTransitLines().containsKey(transitLineId));

        TransitLine transitLine = transitScheduleNew.getTransitLines().get(transitLineId);
        assertTrue("Schedule should include transit route",
                transitLine.getRoutes().containsKey(transitRouteId));

        TransitRoute transitRoute = transitLine.getRoutes().get(transitRouteId);
        int stopCntNew = transitRoute.getStops().size();
        assertEquals("transitRoute should contain same number of stops as before modification",
                stopCntOld, stopCntNew);

    }

    /**
     * trimming method: TrimEnds.
     * route scenario: AllIn
     * The testRoute should be deleted since all stops are within the zone.
     */
    @Test
    public void testTrimEnds_AllIn() {

        Id<TransitLine> transitLineId = routeType.allIn.transitLineId;

        // Modification
        Set<Id<TransitLine>> linesToModify = Collections.singleton(transitLineId);
        transitRouteTrimmer.removeEmptyLines = false;
        transitRouteTrimmer.xxxTrimEnds(linesToModify);

        // After trim
        TransitSchedule transitScheduleNew = transitRouteTrimmer.getTransitScheduleNew();
        assertTrue("schedule should include empty transit line",
                transitScheduleNew.getTransitLines().containsKey(transitLineId));
        assertEquals("transitLine should no longer contain any routes",
                transitScheduleNew.getTransitLines().get(transitLineId).getRoutes().size(), 0);

    }

    /**
     * trimming method: TrimEnds.
     * route scenario: HalfIn
     * The second half of the route is outsie the zone and should be trimmed
     */
    @Test
    public void testTrimEnds_HalfIn() {

        Id<TransitLine> transitLineId = routeType.halfIn.transitLineId;
        Id<TransitRoute> transitRouteId = routeType.halfIn.transitRouteId;

        // Before trim
        TransitRoute transitRouteOld = scenario.getTransitSchedule().getTransitLines().get(Id.create("184---17340_700", TransitLine.class)).getRoutes().get(Id.create("184---17340_700_15", TransitRoute.class));
        int sizeOld = transitRouteOld.getStops().size();
        int outCntOld = countStopsOutsideZone(transitRouteOld);
        Id<TransitStopFacility> firstStopOld = transitRouteOld.getStops().get(0).getStopFacility().getId();
        Id<TransitStopFacility> lastStopOld = transitRouteOld.getStops().get(sizeOld - 1).getStopFacility().getId();

        // Modification
        Set<Id<TransitLine>> linesToModify = Collections.singleton(transitLineId);
        transitRouteTrimmer.includeFirstStopWithinZone = false;
        transitRouteTrimmer.xxxTrimEnds(linesToModify);
        TransitSchedule transitScheduleNew = transitRouteTrimmer.getTransitScheduleNew();

        // After trim
        Id<TransitRoute> transitRouteIdNew = Id.create(transitRouteId.toString() + "_mod1", TransitRoute.class);
        TransitRoute transitRouteNew = transitScheduleNew.getTransitLines().get(transitLineId).getRoutes().get(transitRouteIdNew);

        int sizeNew = transitRouteNew.getStops().size();
        int inCntNew = countStopsInZone(transitRouteNew);
        int outCntNew = countStopsOutsideZone(transitRouteNew);
        Id<TransitStopFacility> firstStopNew = transitRouteNew.getStops().get(0).getStopFacility().getId();
        Id<TransitStopFacility> lastStopNew = transitRouteNew.getStops().get(sizeNew - 1).getStopFacility().getId();

        Assert.assertTrue("modified route should have less stops as original route",
                sizeOld > sizeNew);
        assertEquals("there should be no stops within the zone",
                0, inCntNew);
        assertEquals("number of stops outside of zone should remain same",
                outCntOld, outCntNew);
        assertEquals("first stop of old and new route should be same",
                firstStopOld, firstStopNew);
        assertNotEquals("last stop of old and new route should be different",
                lastStopOld, lastStopNew);
    }

    /**
     * trimming method: TrimEnds.
     * route scenario: MiddleIn
     * Since the ends are both outside of zone, route should not be modified
     */
    @Test
    public void testTrimEnds_MiddleIn() {

        Id<TransitLine> transitLineId = routeType.middleIn.transitLineId;
        Id<TransitRoute> transitRouteId = routeType.middleIn.transitRouteId;


        // Before Trim
        TransitRoute transitRouteOld = scenario.getTransitSchedule().getTransitLines().get(transitLineId).getRoutes().get(transitRouteId);
        int numStopsOld = transitRouteOld.getStops().size();
        int numLinksOld = transitRouteOld.getRoute().getLinkIds().size();


        // Modification
        Set<Id<TransitLine>> linesToModify = Collections.singleton(transitLineId);
        transitRouteTrimmer.removeEmptyLines = false;
        transitRouteTrimmer.xxxTrimEnds(linesToModify);

        // After Trim
        TransitSchedule transitScheduleNew = transitRouteTrimmer.getTransitScheduleNew();
        Id<TransitRoute> transitRouteIdNew = Id.create(transitRouteId.toString() + "_mod1", TransitRoute.class);
        TransitRoute routeNew = transitScheduleNew.getTransitLines().get(transitLineId).getRoutes().get(transitRouteIdNew);
        int numStopsNew = routeNew.getStops().size();
        int numLinksNew = routeNew.getRoute().getLinkIds().size();

        Assert.assertTrue("line should still exist",
                transitScheduleNew.getTransitLines().containsKey(transitLineId));
        Assert.assertEquals("new route should contain same number of stops as old one",
                numStopsOld, numStopsNew);
        Assert.assertEquals("new route should contain same number of links as old one",
                numLinksOld, numLinksNew);
    }



    /**
     * trimming method: SkipStops.
     * route scenario: AllIn
     * New route should be empty
     */
    @Test
    public void testSkipStops_AllIn() {

        Id<TransitLine> transitLineId = routeType.allIn.transitLineId;

        // Modification
        Set<Id<TransitLine>> linesToModify = Collections.singleton(transitLineId);
        transitRouteTrimmer.removeEmptyLines = false;
        transitRouteTrimmer.modifyTransitLinesFromTransitSchedule(linesToModify, TransitRouteTrimmer.modMethod.SkipStopsWithinZone);

        // After trim
        TransitSchedule transitScheduleNew = transitRouteTrimmer.getTransitScheduleNew();
        assertTrue("sched should include empty transit line", transitScheduleNew.getTransitLines().containsKey(transitLineId));
        assertEquals("transitLine should not longer contain any routes", transitScheduleNew.getTransitLines().get(transitLineId).getRoutes().size(), 0);

    }

    /**
     * trimming method: SkipStops.
     * route scenario: HalfIn
     * Stops outside zone should be skipped
     */
    @Test
    public void testSkipStops_HalfIn() {

        // Before trim
        Id<TransitLine> transitLineId = routeType.halfIn.transitLineId;
        TransitRoute transitRouteOld = scenario.getTransitSchedule().getTransitLines().get(transitLineId).getRoutes().get(Id.create("184---17340_700_15", TransitRoute.class));
        int sizeOld = transitRouteOld.getStops().size();
        int outCntOld = countStopsOutsideZone(transitRouteOld);
        int numLinksOld = transitRouteOld.getRoute().getLinkIds().size();

        // Modification
        Set<Id<TransitLine>> linesToModify = Collections.singleton(transitLineId);
        transitRouteTrimmer.modifyTransitLinesFromTransitSchedule(linesToModify, TransitRouteTrimmer.modMethod.SkipStopsWithinZone);

        // After Trim
        TransitSchedule transitScheduleNew = transitRouteTrimmer.getTransitScheduleNew();
        TransitRoute transitRouteNew = transitScheduleNew.getTransitLines().get(transitLineId).getRoutes().get(Id.create("184---17340_700_15_mod1", TransitRoute.class));
        int sizeNew = transitRouteNew.getStops().size();
        int inCntNew = countStopsInZone(transitRouteNew);
        int outCntNew = countStopsOutsideZone(transitRouteNew);
        int numLinksNew = transitRouteNew.getRoute().getLinkIds().size();

        Assert.assertTrue("there should be less stops after the modificaton",
                sizeOld > sizeNew);
        assertTrue("new route should have less links than old route",
                numLinksNew < numLinksOld);
        assertEquals("there should only be one stop within the zone",
                1, inCntNew);
        assertEquals("the number of stops outside of zone should remain same",
                outCntOld, outCntNew);

    }


    /**
     * trimming method: SkipStops.
     * route scenario: MiddleIn
     * New route should have less stops than old route, but same amount of links
     */
    @Test
    public void testSkipStops_MiddleIn() {

        Id<TransitLine> transitLineId = routeType.middleIn.transitLineId;
        Id<TransitRoute> transitRouteId = routeType.middleIn.transitRouteId;

        // Before trim
        TransitRoute transitRouteOld = scenario.getTransitSchedule().getTransitLines().get(transitLineId).getRoutes().get(transitRouteId);
        int numStopsOld = transitRouteOld.getStops().size();
        int numLinksOld = transitRouteOld.getRoute().getLinkIds().size();

        // Modification
        Set<Id<TransitLine>> linesToModify = Collections.singleton(transitLineId);
        transitRouteTrimmer.removeEmptyLines = false;
        transitRouteTrimmer.modifyTransitLinesFromTransitSchedule(linesToModify, TransitRouteTrimmer.modMethod.SkipStopsWithinZone);

        // After trim
        TransitSchedule transitScheduleNew = transitRouteTrimmer.getTransitScheduleNew();
        TransitRoute transitRouteNew = transitScheduleNew.getTransitLines().get(transitLineId).getRoutes().get(Id.create("161---17326_700_21_mod1", TransitRoute.class));
        int numStopsNew = transitRouteNew.getStops().size();
        int numLinksNew = transitRouteNew.getRoute().getLinkIds().size();
        int inCntNew = countStopsInZone(transitRouteNew);

        Assert.assertTrue("line should still exist",
                transitScheduleNew.getTransitLines().containsKey(transitLineId));
        Assert.assertNotEquals("new route should NOT contain same number of stops as old one",
                numStopsOld, numStopsNew);
        Assert.assertEquals("new route should contain same number of links as old one",
                numLinksOld, numLinksNew);
        Assert.assertEquals("new route should only have two stops within zone, one per zone entrance/exit",
                2, inCntNew);

    }

    /**
     * trimming method: SplitRoutes.
     * route scenario: AllIn
     * route will be deleted
     */
    @Test
    public void testSplitRoutes_AllIn() {

        Id<TransitLine> transitLineId = routeType.allIn.transitLineId;

        // Modification
        Set<Id<TransitLine>> linesToModify = Collections.singleton(transitLineId);
        transitRouteTrimmer.removeEmptyLines = false;
        transitRouteTrimmer.modifyTransitLinesFromTransitSchedule(linesToModify, TransitRouteTrimmer.modMethod.SplitRoute);

        // After trim
        TransitSchedule transitScheduleNew = transitRouteTrimmer.getTransitScheduleNew();
        assertTrue("schedule should include empty transit line",
                transitScheduleNew.getTransitLines().containsKey(transitLineId));
        assertEquals("transitLine should not longer contain any routes",
                0, transitScheduleNew.getTransitLines().get(transitLineId).getRoutes().size());

    }

    /**
     * trimming method: SplitRoutes.
     * route scenario: HalfIn
     * New route should have less stops than old route
     */
    @Test
    public void testSplitRoutes_HalfIn() {

        Id<TransitLine> transitLineId = routeType.halfIn.transitLineId;

        // Before trim
        TransitRoute transitRouteOld = scenario.getTransitSchedule().getTransitLines().get(transitLineId).getRoutes().get(Id.create("184---17340_700_15", TransitRoute.class));
        int sizeOld = transitRouteOld.getStops().size();
        int outCntOld = countStopsOutsideZone(transitRouteOld);

        // Modification
        Set<Id<TransitLine>> linesToModify = Collections.singleton(transitLineId);
        transitRouteTrimmer.xxxSplitRoute(linesToModify);

        // After trim
        TransitSchedule transitScheduleNew = transitRouteTrimmer.getTransitScheduleNew();

        assertTrue(transitScheduleNew.getTransitLines().containsKey(transitLineId));
        TransitLine transitLine = transitScheduleNew.getTransitLines().get(transitLineId);
        assertTrue(transitLine.getRoutes().containsKey(Id.create("184---17340_700_15_mod1", TransitRoute.class)));

        TransitRoute transitRouteNew = transitScheduleNew.getTransitLines().get(Id.create("184---17340_700", TransitLine.class)).getRoutes().get(Id.create("184---17340_700_15_mod1", TransitRoute.class));
        int sizeNew = transitRouteNew.getStops().size();
        int inCntNew = countStopsInZone(transitRouteNew);
        int outCntNew = countStopsOutsideZone(transitRouteNew);

        assertTrue("new route should have less stops than old route",
                sizeOld > sizeNew);
        assertEquals("there should only be one stop within the zone",
                1, inCntNew);
        assertEquals("# of stops outside of zone should remain same",
                outCntOld, outCntNew);

    }

    /**
     * trimming method: SplitRoutes.
     * route scenario: MiddleIn
     * Two routes should be created, each with only one stop within zone
     */
    @Test
    public void testSplitRoutes_MiddleIn() {

        Id<TransitLine> transitLineId = routeType.middleIn.transitLineId;
        Id<TransitRoute> transitRouteId = routeType.middleIn.transitRouteId;

        // Before trim
        TransitRoute transitRouteOld = scenario.getTransitSchedule().getTransitLines().get(transitLineId).getRoutes().get(transitRouteId);

        // Modification
        Set<Id<TransitLine>> linesToModify = Collections.singleton(transitLineId);
        transitRouteTrimmer.removeEmptyLines = false;
        transitRouteTrimmer.modifyTransitLinesFromTransitSchedule(linesToModify, TransitRouteTrimmer.modMethod.SplitRoute);

        // After trim
        TransitSchedule transitScheduleNew = transitRouteTrimmer.getTransitScheduleNew();

        assertTrue("line should still exist", transitScheduleNew.getTransitLines().containsKey(transitLineId));
        TransitLine transitLineNew = transitScheduleNew.getTransitLines().get(transitLineId);

        assertTrue(transitLineNew.getRoutes().containsKey(Id.create("161---17326_700_21_mod1", TransitRoute.class)));
        assertTrue(transitLineNew.getRoutes().containsKey(Id.create("161---17326_700_21_mod2", TransitRoute.class)));
        assertFalse(transitLineNew.getRoutes().containsKey(Id.create("161---17326_700_21_mod0", TransitRoute.class)));
        assertFalse(transitLineNew.getRoutes().containsKey(Id.create("161---17326_700_21_mod3", TransitRoute.class)));
        assertFalse(transitLineNew.getRoutes().containsKey(Id.create("161---17326_700_21", TransitRoute.class)));


        TransitRoute transitRouteNew1 = transitLineNew.getRoutes().get(Id.create("161---17326_700_21_mod1", TransitRoute.class));
        TransitRoute transitRouteNew2 = transitLineNew.getRoutes().get(Id.create("161---17326_700_21_mod2", TransitRoute.class));

        assertNotEquals(transitRouteOld.getStops().size(), transitRouteNew1.getStops().size() + transitRouteNew2.getStops().size());
        assertNotEquals(transitRouteNew1.getStops().size(), transitRouteNew2.getStops().size());

        int inCntNew1 = countStopsInZone(transitRouteNew1);
        int inCntNew2 = countStopsInZone(transitRouteNew2);

        Assert.assertEquals("new route #1 should only have one stop within zone", 1, inCntNew1);
        Assert.assertEquals("new route #2 should only have one stop within zone", 1, inCntNew2);

    }


    /*
    Part 3: tests hub functionality for SplitRoutes trimming method (using route type "middleIn")
    Hubs allow route to extend into zone to reach a import transit stop (like a major transfer point)
     */


    /**
     * Test Hub functionality
     * trimming method: SplitRoutes.
     * route scenario: MiddleIn
     * tests reach of hubs. Left hub should be included in route 1, while right hub should not be
     * included in route 2, due to lacking reach
     */
    @Test
    public void testSplitRoutes_MiddleIn_Hub_ValidateReach() {

        Id<TransitLine> transitLineId = routeType.middleIn.transitLineId;
        Id<TransitRoute> transitRouteId = routeType.middleIn.transitRouteId;

        // Before trim
        TransitRoute transitRouteOld = scenario.getTransitSchedule().getTransitLines().get(transitLineId).getRoutes().get(transitRouteId);

        // add hub attributes
        // Stop 070101005700 is a hub with reach of 3. This stop (as well as the intermediary stops)
        // should be included in route1
        Id<TransitStopFacility> facIdLeft = Id.create("070101005700", TransitStopFacility.class);
        scenario.getTransitSchedule().getFacilities().get(facIdLeft).getAttributes().putAttribute("hub-reach", 3);

        // Stop 070101006207 is a hub with reach of 3. This stop is therfore just out of range for route 2
        // Therefore it should not be included.
        Id<TransitStopFacility> facIdRight = Id.create("070101006207", TransitStopFacility.class);
        scenario.getTransitSchedule().getFacilities().get(facIdRight).getAttributes().putAttribute("hub-reach", 3);


        // Modification
        Set<Id<TransitLine>> linesToModify = Collections.singleton(transitLineId);

        transitRouteTrimmer.removeEmptyLines = false;
        transitRouteTrimmer.modifyTransitLinesFromTransitSchedule(linesToModify, TransitRouteTrimmer.modMethod.SplitRoute);
        TransitSchedule transitScheduleNew = transitRouteTrimmer.getTransitScheduleNew();

        assertTrue("line should still exist", transitScheduleNew.getTransitLines().containsKey(transitLineId));
        TransitLine transitLineNew = transitScheduleNew.getTransitLines().get(transitLineId);

        assertTrue(transitLineNew.getRoutes().containsKey(Id.create("161---17326_700_21_mod1", TransitRoute.class)));
        assertTrue(transitLineNew.getRoutes().containsKey(Id.create("161---17326_700_21_mod2", TransitRoute.class)));
        assertFalse(transitLineNew.getRoutes().containsKey(Id.create("161---17326_700_21_mod0", TransitRoute.class)));
        assertFalse(transitLineNew.getRoutes().containsKey(Id.create("161---17326_700_21_mod3", TransitRoute.class)));
        assertFalse(transitLineNew.getRoutes().containsKey(Id.create("161---17326_700_21", TransitRoute.class)));


        TransitRoute transitRouteNew1 = transitLineNew.getRoutes().get(Id.create("161---17326_700_21_mod1", TransitRoute.class));
        TransitRoute transitRouteNew2 = transitLineNew.getRoutes().get(Id.create("161---17326_700_21_mod2", TransitRoute.class));

        assertNotEquals(transitRouteOld.getStops().size(), transitRouteNew1.getStops().size() + transitRouteNew2.getStops().size());
        assertNotEquals(transitRouteNew1.getStops().size(), transitRouteNew2.getStops().size());

        int inCntNew1 = countStopsInZone(transitRouteNew1);
        int inCntNew2 = countStopsInZone(transitRouteNew2);

        assertEquals("new route #1 should have three stop within zone", 3, inCntNew1);
        assertEquals("new route #2 should have one stop within zone", 1, inCntNew2);

    }

    /**
     * Test Hub functionality
     * trimming method: SplitRoutes.
     * route scenario: MiddleIn
     * tests parameter to include first nearest hub, even if reach is insufficient.
     * Right hub should be included, even though reach is too low.
     */
    @Test
    public void testSplitRoutes_MiddleIn_Hub_IncludeFirstHubInZone() {
        Id<TransitLine> transitLineId = routeType.middleIn.transitLineId;
        Id<TransitRoute> transitRouteId = routeType.middleIn.transitRouteId;

        // Before trim
        TransitRoute transitRouteOld = scenario.getTransitSchedule().getTransitLines().get(transitLineId).getRoutes().get(transitRouteId);

        // Stop 070101005700 is a hub with reach of 3. This stop (as well as the intermediary stops)
        // should be included in route1
        Id<TransitStopFacility> facIdLeft = Id.create("070101005700", TransitStopFacility.class);
        scenario.getTransitSchedule().getFacilities().get(facIdLeft).getAttributes().putAttribute("hub-reach", 3);

        // Stop 070101006207 is a hub with reach of 3. This stop is therefore just out of range for route 2
        // However, since includeFirstHubInZone is true, it should be included anyway.
        Id<TransitStopFacility> facIdRight = Id.create("070101006207", TransitStopFacility.class);
        scenario.getTransitSchedule().getFacilities().get(facIdRight).getAttributes().putAttribute("hub-reach", 3);


        // Modification
        Set<Id<TransitLine>> linesToModify = Collections.singleton(transitLineId);
        transitRouteTrimmer.includeFirstHubInZone = true;
        transitRouteTrimmer.removeEmptyLines = false;
        transitRouteTrimmer.modifyTransitLinesFromTransitSchedule(linesToModify, TransitRouteTrimmer.modMethod.SplitRoute);
        TransitSchedule transitScheduleNew = transitRouteTrimmer.getTransitScheduleNew();

        assertTrue("line should still exist", transitScheduleNew.getTransitLines().containsKey(transitLineId));
        TransitLine transitLineNew = transitScheduleNew.getTransitLines().get(transitLineId);

        assertTrue(transitLineNew.getRoutes().containsKey(Id.create("161---17326_700_21_mod1", TransitRoute.class)));
        assertTrue(transitLineNew.getRoutes().containsKey(Id.create("161---17326_700_21_mod2", TransitRoute.class)));
        assertFalse(transitLineNew.getRoutes().containsKey(Id.create("161---17326_700_21_mod0", TransitRoute.class)));
        assertFalse(transitLineNew.getRoutes().containsKey(Id.create("161---17326_700_21_mod3", TransitRoute.class)));
        assertFalse(transitLineNew.getRoutes().containsKey(Id.create("161---17326_700_21", TransitRoute.class)));


        TransitRoute transitRouteNew1 = transitLineNew.getRoutes().get(Id.create("161---17326_700_21_mod1", TransitRoute.class));
        TransitRoute transitRouteNew2 = transitLineNew.getRoutes().get(Id.create("161---17326_700_21_mod2", TransitRoute.class));

        assertNotEquals(transitRouteOld.getStops().size(), transitRouteNew1.getStops().size() + transitRouteNew2.getStops().size());
        assertNotEquals(transitRouteNew1.getStops().size(), transitRouteNew2.getStops().size());

        int inCntNew1 = countStopsInZone(transitRouteNew1);
        int inCntNew2 = countStopsInZone(transitRouteNew2);


        assertEquals("new route #1 should have three stops within zone",
                3, inCntNew1);
        assertEquals("new route #2 should have four stops within zone",
                4, inCntNew2);
        Id<TransitStopFacility> idRoute1 = transitRouteNew1.getStops().get(transitRouteNew1.getStops().size() - 1).getStopFacility().getId();
        assertEquals("last stop of route #1 should be the left hub",
                facIdLeft, idRoute1);

        Id<TransitStopFacility> idRoute2 = transitRouteNew2.getStops().get(0).getStopFacility().getId();
        assertEquals("first stop of route #2 should be the right hub",
                facIdRight, idRoute2);

    }

    /**
     * Test Hub functionality
     * trimming method: SplitRoutes.
     * route scenario: MiddleIn
     * if multiple hubs are within reach of route, the route should go to further one
     */
    @Test
    public void testSplitRoutes_MiddleIn_Hub_MultipleHubs() {

        Id<TransitLine> transitLineId = routeType.middleIn.transitLineId;
        Id<TransitRoute> transitRouteId = routeType.middleIn.transitRouteId;
        Set<Id<TransitStopFacility>> stopsInZone = transitRouteTrimmer.getStopsInZone();

        // Before trim
        TransitRoute transitRouteOld = scenario.getTransitSchedule().getTransitLines().get(transitLineId).getRoutes().get(transitRouteId);
        int numStopsOld = transitRouteOld.getStops().size();

        assertFalse(stopsInZone.contains(transitRouteOld.getStops().get(0).getStopFacility().getId()));
        assertFalse(stopsInZone.contains(transitRouteOld.getStops().get(numStopsOld - 1).getStopFacility().getId()));


        // Stop 070101005700 is a hub with reach of 3. This stop (as well as the intermediary stops)
        // should be included in route1
        Id<TransitStopFacility> facIdLeft = Id.create("070101005700", TransitStopFacility.class);
        scenario.getTransitSchedule().getFacilities().get(facIdLeft).getAttributes().putAttribute("hub-reach", 3);

        // Stop 070101006207 is a hub with reach of 5. This stop is therfore in range for route 1
        // Therefore it should not be included.
        Id<TransitStopFacility> facIdRight = Id.create("070101005702", TransitStopFacility.class);
        scenario.getTransitSchedule().getFacilities().get(facIdRight).getAttributes().putAttribute("hub-reach", 5);


        // Modification
        Set<Id<TransitLine>> linesToModify = Collections.singleton(transitLineId);
        transitRouteTrimmer.removeEmptyLines = false;
        transitRouteTrimmer.modifyTransitLinesFromTransitSchedule(linesToModify, TransitRouteTrimmer.modMethod.SplitRoute);
        TransitSchedule transitScheduleNew = transitRouteTrimmer.getTransitScheduleNew();

        assertTrue("line should still exist", transitScheduleNew.getTransitLines().containsKey(transitLineId));
        TransitLine transitLineNew = transitScheduleNew.getTransitLines().get(transitLineId);

        assertTrue(transitLineNew.getRoutes().containsKey(Id.create("161---17326_700_21_mod1", TransitRoute.class)));
        assertTrue(transitLineNew.getRoutes().containsKey(Id.create("161---17326_700_21_mod2", TransitRoute.class)));
        assertFalse(transitLineNew.getRoutes().containsKey(Id.create("161---17326_700_21_mod0", TransitRoute.class)));
        assertFalse(transitLineNew.getRoutes().containsKey(Id.create("161---17326_700_21_mod3", TransitRoute.class)));
        assertFalse(transitLineNew.getRoutes().containsKey(Id.create("161---17326_700_21", TransitRoute.class)));


        TransitRoute transitRouteNew1 = transitLineNew.getRoutes().get(Id.create("161---17326_700_21_mod1", TransitRoute.class));
        TransitRoute transitRouteNew2 = transitLineNew.getRoutes().get(Id.create("161---17326_700_21_mod2", TransitRoute.class));

        assertNotEquals(transitRouteOld.getStops().size(), transitRouteNew1.getStops().size() + transitRouteNew2.getStops().size());
        assertNotEquals(transitRouteNew1.getStops().size(), transitRouteNew2.getStops().size());

        int inCntNew1 = countStopsInZone(transitRouteNew1);
        int inCntNew2 = countStopsInZone(transitRouteNew2);

        assertEquals("new route #1 should have five stop within zone", 5, inCntNew1);
        assertEquals("new route #2 should have one stop within zone", 1, inCntNew2);

    }

    /**
     * Test Hub functionality
     * trimming method: SplitRoutes.
     * route scenario: MiddleIn
     * if two new routes overlap (because they both reach to same hub)
     * then they should be combined into one route
     */
    @Test
    public void testSplitRoutes_MiddleIn_Hub_OverlapRoutes() {

        Id<TransitLine> transitLineId = routeType.middleIn.transitLineId;
        Id<TransitRoute> transitRouteId = routeType.middleIn.transitRouteId;

        // Before trim
        TransitRoute transitRouteOld = scenario.getTransitSchedule().getTransitLines().get(transitLineId).getRoutes().get(transitRouteId);

        // Stop 070101005708 = S Wilhelmshagen (Berlin) - Hub
        Id<TransitStopFacility> facId = Id.create("070101005708", TransitStopFacility.class);
        scenario.getTransitSchedule().getFacilities().get(facId).getAttributes().putAttribute("hub-reach", 11);

        // Modification
        Set<Id<TransitLine>> linesToModify = Collections.singleton(transitLineId);
        transitRouteTrimmer.removeEmptyLines = false;
        transitRouteTrimmer.modifyTransitLinesFromTransitSchedule(linesToModify, TransitRouteTrimmer.modMethod.SplitRoute);

        // After trim
        TransitSchedule transitScheduleNew = transitRouteTrimmer.getTransitScheduleNew();

        assertTrue("line should still exist", transitScheduleNew.getTransitLines().containsKey(transitLineId));
        TransitLine transitLineNew = transitScheduleNew.getTransitLines().get(transitLineId);

        assertTrue(transitLineNew.getRoutes().containsKey(Id.create("161---17326_700_21_mod1", TransitRoute.class)));
        assertFalse(transitLineNew.getRoutes().containsKey(Id.create("161---17326_700_21_mod2", TransitRoute.class)));
        assertFalse(transitLineNew.getRoutes().containsKey(Id.create("161---17326_700_21_mod0", TransitRoute.class)));
        assertFalse(transitLineNew.getRoutes().containsKey(Id.create("161---17326_700_21_mod3", TransitRoute.class)));
        assertFalse(transitLineNew.getRoutes().containsKey(Id.create("161---17326_700_21", TransitRoute.class)));


        TransitRoute transitRouteNew1 = transitLineNew.getRoutes().get(Id.create("161---17326_700_21_mod1", TransitRoute.class));
        assertEquals(transitRouteOld.getStops().size(), transitRouteNew1.getStops().size());

        int inCntNew1 = countStopsInZone(transitRouteNew1);
        assertEquals("new route #1 should have 19 stops within zone", 19, inCntNew1);


    }

    /*
    Part 4: Tests individual user-defined parameters
     */

    /**
     * Test parameter allowableStopsWithinZone
     * trimming method: SplitRoutes.
     * route scenario: MiddleIn
     * route should not be split, since the parameter allowableStopsWithinZone is equal to the number
     * of stops within zone
     */
    @Test
    public void testSplitRoutes_MiddleIn_AllowableStopsWithin() {

        Id<TransitLine> transitLineId = routeType.middleIn.transitLineId;
        Id<TransitRoute> transitRouteId = routeType.middleIn.transitRouteId;

        // Before trim
        TransitRoute transitRouteOld = scenario.getTransitSchedule().getTransitLines().get(transitLineId).getRoutes().get(transitRouteId);

        // Modification
        Set<Id<TransitLine>> linesToModify = Collections.singleton(transitLineId);
        transitRouteTrimmer.removeEmptyLines = false;
        transitRouteTrimmer.includeFirstStopWithinZone = false;
        transitRouteTrimmer.allowableStopsWithinZone = 19;
        transitRouteTrimmer.modifyTransitLinesFromTransitSchedule(linesToModify, TransitRouteTrimmer.modMethod.SplitRoute);

        // After trim
        TransitSchedule transitScheduleNew = transitRouteTrimmer.getTransitScheduleNew();

        assertTrue("line should still exist", transitScheduleNew.getTransitLines().containsKey(transitLineId));
        TransitLine transitLineNew = transitScheduleNew.getTransitLines().get(transitLineId);

        assertTrue(transitLineNew.getRoutes().containsKey(Id.create("161---17326_700_21_mod1", TransitRoute.class)));
        assertFalse(transitLineNew.getRoutes().containsKey(Id.create("161---17326_700_21_mod2", TransitRoute.class)));
        assertFalse(transitLineNew.getRoutes().containsKey(Id.create("161---17326_700_21_mod0", TransitRoute.class)));
        assertFalse(transitLineNew.getRoutes().containsKey(Id.create("161---17326_700_21_mod3", TransitRoute.class)));
        assertFalse(transitLineNew.getRoutes().containsKey(Id.create("161---17326_700_21", TransitRoute.class)));


        TransitRoute routeNew1 = transitLineNew.getRoutes().get(Id.create("161---17326_700_21_mod1", TransitRoute.class));

        assertEquals(transitRouteOld.getStops().size(), routeNew1.getStops().size());

        int inCntNew1 = countStopsInZone(routeNew1);

        assertEquals("new route #1 should have 19 stops within zone", 19, inCntNew1);


    }



    private int countStopsInZone(TransitRoute transitRoute) {
        int inCnt = 0;
        Set<Id<TransitStopFacility>> stopsInZone = transitRouteTrimmer.getStopsInZone();
        for (TransitRouteStop stop : transitRoute.getStops()) {
            if (stopsInZone.contains(stop.getStopFacility().getId())) {
                inCnt++;
            }
        }
        return inCnt;
    }

    private int countStopsOutsideZone(TransitRoute transitRoute) {
        int outCnt = 0;
        Set<Id<TransitStopFacility>> stopsInZone = transitRouteTrimmer.getStopsInZone();
        for (TransitRouteStop stop : transitRoute.getStops()) {
            if (!stopsInZone.contains(stop.getStopFacility().getId())) {
                outCnt++;
            }
        }
        return outCnt;
    }
}
