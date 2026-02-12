package org.opentripplanner.osm.tagmapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.osm.model.TraverseDirection.FORWARD;

import org.junit.jupiter.api.Test;
import org.opentripplanner.osm.model.OsmEntityForTest;
import org.opentripplanner.osm.wayproperty.WayPropertySet;

class NetherlandsMapperTest {

    private static final WayPropertySet wps = new NetherlandsMapper().buildWayPropertySet();
    private static final float EPSILON = 0.01f;

    @Test
    void testCarSpeeds() {
        // Motorway 100 km/h
        assertEquals(27.78f, wps.getCarSpeedForWay(way("highway", "motorway"), FORWARD), EPSILON);

        // Primary 80 km/h
        assertEquals(22.22f, wps.getCarSpeedForWay(way("highway", "primary"), FORWARD), EPSILON);

        // Secondary 60 km/h
        assertEquals(16.67f, wps.getCarSpeedForWay(way("highway", "secondary"), FORWARD), EPSILON);

        // Residential 30 km/h
        assertEquals(8.33f, wps.getCarSpeedForWay(way("highway", "residential"), FORWARD), EPSILON);

        // Living Street 15 km/h
        assertEquals(4.17f, wps.getCarSpeedForWay(way("highway", "living_street"), FORWARD), EPSILON);
    }

    @Test
    void testCycleSafety() {
        // Cyclestreet should be safer than regular road
        var residential = wps.getDataForEntity(way("highway", "residential"));
        var cycleStreet = wps.getDataForEntity(way("cyclestreet", "yes"));

        assertTrue(cycleStreet.bicycleSafety() < residential.bicycleSafety(),
                "Cycle street should be safer (lower cost) than residential road");

        // Designated cycleway should be very safe for bikes, allowed for pedestrians
        var cycleWay = wps.getDataForEntity(way("highway", "cycleway"));
        assertEquals(0.6, cycleWay.bicycleSafety(), 0.01);
        assertEquals(1.2, cycleWay.walkSafety(), 0.01);
    }

    @Test
    void testPermissions() {
        // Motorway: No cycling
        var motorway = new NetherlandsMapper();
        assertTrue(motorway.isBicycleThroughTrafficExplicitlyDisallowed(way("highway", "motorway")));
        assertTrue(motorway.isWalkThroughTrafficExplicitlyDisallowed(way("highway", "motorway")));

        var cycleWayProps = wps.getDataForEntity(way("highway", "cycleway"));
        assertTrue(cycleWayProps.bicycleSafety() > 0);
    }

    private OsmEntityForTest way(String key, String value) {
        var way = new OsmEntityForTest();
        way.addTag(key, value);
        return way;
    }
}
