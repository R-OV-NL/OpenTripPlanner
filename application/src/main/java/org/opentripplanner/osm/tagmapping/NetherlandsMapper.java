package org.opentripplanner.osm.tagmapping;

import static org.opentripplanner.osm.wayproperty.WayPropertiesBuilder.withModes;
import static org.opentripplanner.street.model.StreetTraversalPermission.ALL;
import static org.opentripplanner.street.model.StreetTraversalPermission.CAR;
import static org.opentripplanner.street.model.StreetTraversalPermission.PEDESTRIAN_AND_BICYCLE;

import org.opentripplanner.osm.model.OsmEntity;
import org.opentripplanner.osm.wayproperty.WayPropertySet;

/**
 * OSM way properties for Netherlands roads.
 * <p>
 * Speed limits are approximated based on standard Dutch limits:
 * - Motorway: 100-130 km/h (Defaulted to 100 km/h = ~27.8 m/s)
 * - Trunk: 100 km/h
 * - Primary: 80 km/h
 * - Secondary: 60-80 km/h (Defaulted to 60 km/h = ~16.7 m/s)
 * - Tertiary: 50 km/h
 * - Built-up areas (Residential/Unclassified): 30-50 km/h (Defaulted to 30 km/h
 * = ~8.3 m/s)
 * - Living Street: 15 km/h
 * <p>
 * Cycling rules:
 * - Extensive segregated cycle infrastructure.
 * - 'cyclestreet=yes' (fietsstraat) implies high priority for cyclists.
 * - Cycling prohibited on motorways and trunks.
 *
 * @see OsmTagMapper
 */
class NetherlandsMapper extends OsmTagMapper {

    @Override
    public WayPropertySet buildWayPropertySet() {
        var props = WayPropertySet.of();

        // 1. Speeds
        // 100 km/h ~ 27.78 m/s
        props.setCarSpeed("highway=motorway", 27.78f);
        props.setCarSpeed("highway=motorway_link", 22.22f); // 80 km/h
        props.setCarSpeed("highway=trunk", 27.78f);
        props.setCarSpeed("highway=trunk_link", 13.89f); // 50 km/h

        // 80 km/h ~ 22.22 m/s
        props.setCarSpeed("highway=primary", 22.22f);
        props.setCarSpeed("highway=primary_link", 13.89f); // 50 km/h

        // 60 km/h ~ 16.67 m/s (Conservative average for secondary roads, often 60 or
        // 80)
        props.setCarSpeed("highway=secondary", 16.67f);
        props.setCarSpeed("highway=secondary_link", 13.89f); // 50 km/h

        // 50 km/h ~ 13.89 m/s
        props.setCarSpeed("highway=tertiary", 13.89f);
        props.setCarSpeed("highway=tertiary_link", 13.89f);

        // 30 km/h ~ 8.33 m/s (Residential/Unclassified often 30 in cities)
        props.setCarSpeed("highway=residential", 8.33f);
        props.setCarSpeed("highway=residential_link", 8.33f);
        props.setCarSpeed("highway=unclassified", 8.33f);
        props.setCarSpeed("highway=road", 8.33f);

        // 15 km/h ~ 4.17 m/s
        props.setCarSpeed("highway=living_street", 4.17f);
        props.setCarSpeed("highway=service", 4.17f);

        // 2. Cycling Safety & Permissions
        // Base safety for generic roads
        props.setProperties("highway=residential", withModes(ALL).bicycleSafety(0.95)); // Quite safe
        props.setProperties("highway=tertiary", withModes(ALL).bicycleSafety(1.1));
        props.setProperties("highway=secondary", withModes(ALL).bicycleSafety(1.5)); // Less safe without segregation
        props.setProperties("highway=primary", withModes(ALL).bicycleSafety(2.0)); // Unsafe without segregation

        // Explicit cycle infrastructure
        props.setProperties("highway=cycleway", withModes(PEDESTRIAN_AND_BICYCLE).bicycleSafety(0.6).walkSafety(1.2));
        props.setProperties("bicycle=designated", withModes(PEDESTRIAN_AND_BICYCLE).bicycleSafety(0.6).walkSafety(1.2));

        // Cyclestreets (fietsstraat) - treat effectively as a cycleway for safety
        props.setProperties("cyclestreet=yes", withModes(ALL).bicycleSafety(0.7));

        // 3. Access Restrictions
        // No walking/cycling on motorways/trunks by default
        props.setProperties("highway=motorway", withModes(CAR));
        props.setProperties("highway=motorway_link", withModes(CAR));
        props.setProperties("highway=trunk", withModes(CAR));
        props.setProperties("highway=trunk_link", withModes(CAR));

        // Pedestrians on cycleways? Generally yes in NL unless foot=no
        // (Handled by implicit mapping, but we can be explicit if needed)
        // The super class allows walking on cycleways if not forbidden.

        // Mixins
        // Living streets are very safe for walking and cycling
        props.setProperties("highway=living_street", withModes(ALL).walkSafety(0.9).bicycleSafety(0.9));

        props.addPickers(super.buildWayPropertySet());
        return props.build();
    }

    private static final java.util.Set<String> MOTOR_TRAFFIC_ONLY_ROADS = java.util.Set.of(
            "motorway",
            "motorway_link",
            "trunk",
            "trunk_link");

    @Override
    public boolean isBicycleThroughTrafficExplicitlyDisallowed(OsmEntity way) {
        // In NL, cycling on trunk/motorway is definitely disallowed
        if (way.isOneOfTags("highway", MOTOR_TRAFFIC_ONLY_ROADS)) {
            // Unless there is a specific bike tag allowing it (unlikely on main
            // carriageway)
            if (!way.hasTag("bicycle")) {
                return true;
            }
        }
        return super.isBicycleThroughTrafficExplicitlyDisallowed(way);
    }

    @Override
    public boolean isWalkThroughTrafficExplicitlyDisallowed(OsmEntity way) {
        if (way.isOneOfTags("highway", MOTOR_TRAFFIC_ONLY_ROADS)) {
            if (!way.hasTag("foot")) {
                return true;
            }
        }
        return super.isWalkThroughTrafficExplicitlyDisallowed(way);
    }
}
