package org.opentripplanner.netex.nl;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueStore;
import org.opentripplanner.model.impl.TransitDataImportBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import org.opentripplanner.core.model.i18n.I18NString;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.model.StopTime;
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.transit.model.site.Station;
import org.opentripplanner.model.calendar.ServiceCalendar;
import org.opentripplanner.model.calendar.ServiceDateInterval;
import java.time.LocalDate;

/**
 * Specialized parser for Dutch NeTEx Flex profile.
 * Focuses strictly on:
 * - AvailabilityConditions (Timebands)
 * - ServiceJourneys (that are flexible)
 * - Lines (Booking info)
 */
public class NetexFlexNLParser {

    private void createServiceCalendar(String accessibilityConditionId, String fromDateStr, String toDateStr) {
        if (accessibilityConditionId == null)
            return;

        FeedScopedId serviceId = new FeedScopedId(feedId, accessibilityConditionId);

        LocalDate startDate;
        LocalDate endDate;

        try {
            startDate = fromDateStr != null && !fromDateStr.isEmpty() ? LocalDate.parse(fromDateStr.substring(0, 10))
                    : LocalDate.now().minusYears(1);
            endDate = toDateStr != null && !toDateStr.isEmpty() ? LocalDate.parse(toDateStr.substring(0, 10))
                    : LocalDate.now().plusYears(1);
        } catch (Exception e) {
            LOG.warn("Invalid dates for AvailabilityCondition {}: {} - {}. Using defaults.", accessibilityConditionId,
                    fromDateStr, toDateStr);
            startDate = LocalDate.now().minusYears(1);
            endDate = LocalDate.now().plusYears(1);
        }

        ServiceDateInterval interval = new ServiceDateInterval(startDate, endDate);
        ServiceCalendar calendar = new ServiceCalendar();
        calendar.setServiceId(serviceId);
        calendar.setPeriod(interval);
        calendar.setAllDays(1); // Active every day in the period

        builder.getCalendars().add(calendar);
    }

    private void createFlexTrip(String serviceJourneyId, String patternRef, String availabilityConditionRef,
            String lineRef) {
        PatternModel pattern = patternsById.get(patternRef);
        List<TimebandModel> timebands = timebandsByAvailabilityCondition.get(availabilityConditionRef);

        if (pattern == null) {
            LOG.warn("Pattern {} not found for ServiceJourney {}", patternRef, serviceJourneyId);
            return;
        }
        if (timebands == null || timebands.isEmpty()) {
            LOG.warn("Timebands not found for AvailabilityCondition {} in ServiceJourney {}", availabilityConditionRef,
                    serviceJourneyId);
            return;
        }

        // Resolve Line/Route
        FeedScopedId lineId = new FeedScopedId(feedId, lineRef);
        Route route = builder.getRoutes().get(lineId);
        if (route == null) {
            LOG.warn("Route {} not found for ServiceJourney {}", lineRef, serviceJourneyId);
            return;
        }

        // For each Timeband, create a Trip
        for (TimebandModel tb : timebands) {
            createTripForTimeband(serviceJourneyId, pattern, tb, route, availabilityConditionRef);
        }
    }

    private void createTripForTimeband(String serviceJourneyId, PatternModel pattern, TimebandModel tb, Route route,
            String availabilityConditionRef) {
        FeedScopedId tripId = new FeedScopedId(feedId, serviceJourneyId);

        Trip trip = Trip.of(tripId)
                .withRoute(route)
                .withServiceId(new FeedScopedId(feedId, availabilityConditionRef))
                .build();

        builder.getTripsById().add(trip);

        List<StopTime> stopTimes = new ArrayList<>();
        int timeStart = parseTime(tb.startTime) + (tb.dayOffset * 86400);
        int timeEnd = parseTime(tb.endTime) + (tb.dayOffset * 86400);

        for (StopPointModel sp : pattern.stops) {
            FeedScopedId stopId = new FeedScopedId(feedId, sp.stopPointRef);
            RegularStop stop = builder.stopsByScheduledStopPoints().get(stopId);

            if (stop == null) {
                LOG.warn("Stop {} not found for Pattern {}", sp.stopPointRef, pattern.id);
                continue;
            }

            StopTime st = new StopTime();
            st.setTrip(trip);
            st.setStop(stop);
            st.setStopSequence(sp.order);

            // Set Flex Windows on all stops for implicit validity
            st.setFlexWindowStart(timeStart);
            st.setFlexWindowEnd(timeEnd);

            st.setPickupType(sp.forBoarding ? PickDrop.SCHEDULED : PickDrop.NONE);
            st.setDropOffType(sp.forAlighting ? PickDrop.SCHEDULED : PickDrop.NONE);

            stopTimes.add(st);
        }

        if (stopTimes.size() < 2) {
            return;
        }

        // Register StopTimes so FlexTripsMapper can pick them up and create
        // UnscheduledTrip
        builder.getStopTimesSortedByTrip().put(trip, stopTimes);
        LOG.debug("Registered Trip {} with {} StopTimes for Flex conversion", tripId, stopTimes.size());
    }

    private int parseTime(String time) {
        // HH:mm:ss
        String[] parts = time.split(":");
        return Integer.parseInt(parts[0]) * 3600 + Integer.parseInt(parts[1]) * 60 + Integer.parseInt(parts[2]);
    }

    private static final Logger LOG = LoggerFactory.getLogger(NetexFlexNLParser.class);
    private final TransitDataImportBuilder builder;
    private final DataImportIssueStore issueStore;
    private final XMLInputFactory xmlInputFactory;

    private final String feedId;

    public NetexFlexNLParser(TransitDataImportBuilder builder, DataImportIssueStore issueStore, String feedId) {
        this.builder = builder;
        this.issueStore = issueStore;
        this.feedId = feedId;
        this.xmlInputFactory = XMLInputFactory.newInstance();
    }

    private final java.util.Map<String, java.util.List<TimebandModel>> timebandsByAvailabilityCondition = new java.util.HashMap<>();

    private String currentAvailabilityConditionId;
    private String currentFromDate;
    private String currentToDate;

    private final java.util.Map<String, PatternModel> patternsById = new java.util.HashMap<>();

    private String currentPatternId;
    private StopPointModel currentStopPoint;
    private final java.util.List<StopPointModel> currentPatternStops = new java.util.ArrayList<>();

    public void parse(InputStream in, String filename) {
        LOG.info("Parsing file for NL Flex: {}", filename);
        try {
            XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(in);

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String localName = reader.getLocalName();

                    if ("AvailabilityCondition".equals(localName)) {
                        currentAvailabilityConditionId = reader.getAttributeValue(null, "id");
                        currentFromDate = null;
                        currentToDate = null;
                    } else if ("FromDate".equals(localName)) {
                        currentFromDate = reader.getElementText();
                    } else if ("ToDate".equals(localName)) {
                        currentToDate = reader.getElementText();
                    } else if ("Timeband".equals(localName)) {
                        if (currentAvailabilityConditionId != null) {
                            parseTimeband(reader);
                        }
                    } else if ("ServiceJourneyPattern".equals(localName)) {
                        currentPatternId = reader.getAttributeValue(null, "id");
                        currentPatternStops.clear();
                    } else if ("StopPointInJourneyPattern".equals(localName)) {
                        currentStopPoint = new StopPointModel();
                        currentStopPoint.order = Integer.parseInt(reader.getAttributeValue(null, "order"));
                    } else if ("ScheduledStopPointRef".equals(localName)) {
                        if (currentStopPoint != null) {
                            currentStopPoint.stopPointRef = reader.getAttributeValue(null, "ref");
                        }
                    } else if ("ForBoarding".equals(localName)) {
                        if (currentStopPoint != null)
                            currentStopPoint.forBoarding = Boolean.parseBoolean(reader.getElementText());
                    } else if ("ForAlighting".equals(localName)) {
                        if (currentStopPoint != null)
                            currentStopPoint.forAlighting = Boolean.parseBoolean(reader.getElementText());
                    } else if ("ZoneContainingStops".equals(localName)) {
                        // isFlexZone ignored
                    } else if ("ServiceJourney".equals(localName)) {
                        parseServiceJourney(reader);
                    } else if ("ScheduledStopPoint".equals(localName)) {
                        parseScheduledStopPoint(reader);
                    } else if ("Line".equals(localName)) {
                        parseLine(reader);
                    } else if ("FlexibleStopPlace".equals(localName)) {
                        parseFlexibleStopPlace(reader);
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String localName = reader.getLocalName();
                    if ("AvailabilityCondition".equals(localName)) {
                        createServiceCalendar(currentAvailabilityConditionId, currentFromDate, currentToDate);
                        currentAvailabilityConditionId = null;
                        currentFromDate = null;
                        currentToDate = null;
                    } else if ("ServiceJourneyPattern".equals(localName)) {
                        if (currentPatternId != null) {
                            patternsById.put(currentPatternId,
                                    new PatternModel(currentPatternId, new java.util.ArrayList<>(currentPatternStops)));
                        }
                        currentPatternId = null;
                        currentPatternStops.clear();
                    } else if ("StopPointInJourneyPattern".equals(localName)) {
                        if (currentPatternId != null && currentStopPoint != null) {
                            currentPatternStops.add(currentStopPoint);
                        }
                        currentStopPoint = null;
                    }
                }
            }

            // Register all parsed stops into the site repository at end of parsing
            builder.siteRepository().withRegularStops(builder.stopsByScheduledStopPoints().values());

            // Process cached ServiceJourneys after all patterns/stops are loaded
            for (ServiceJourneyModel sj : cachedServiceJourneys) {
                createFlexTrip(sj.id, sj.patternRef, sj.availabilityConditionRef, sj.lineRef);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse file: " + filename, e);
        }
    }

    private final java.util.List<ServiceJourneyModel> cachedServiceJourneys = new java.util.ArrayList<>();

    private static class ServiceJourneyModel {
        final String id;
        final String patternRef;
        final String availabilityConditionRef;
        final String lineRef;

        ServiceJourneyModel(String id, String patternRef, String availabilityConditionRef, String lineRef) {
            this.id = id;
            this.patternRef = patternRef;
            this.availabilityConditionRef = availabilityConditionRef;
            this.lineRef = lineRef;
        }
    }

    private void parseServiceJourney(XMLStreamReader reader) throws XMLStreamException {
        String serviceJourneyId = reader.getAttributeValue(null, "id");
        String patternRef = null;
        String availabilityConditionRef = null;
        String lineRef = null;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();
                if ("ServiceJourneyPatternRef".equals(localName)) {
                    patternRef = reader.getAttributeValue(null, "ref");
                } else if ("AvailabilityConditionRef".equals(localName)) {
                    availabilityConditionRef = reader.getAttributeValue(null, "ref");
                } else if ("LineRef".equals(localName)) {
                    lineRef = reader.getAttributeValue(null, "ref");
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if ("ServiceJourney".equals(reader.getLocalName())) {
                    if (serviceJourneyId != null && patternRef != null && availabilityConditionRef != null) {
                        cachedServiceJourneys.add(new ServiceJourneyModel(serviceJourneyId, patternRef,
                                availabilityConditionRef, lineRef));
                    }
                    return;
                }
            }
        }
    }

    private void parseTimeband(XMLStreamReader reader) throws XMLStreamException {
        String startTime = null;
        String endTime = null;
        int dayOffset = 0;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();
                if ("StartTime".equals(localName)) {
                    startTime = reader.getElementText();
                } else if ("EndTime".equals(localName)) {
                    endTime = reader.getElementText();
                } else if ("DayOffset".equals(localName)) {
                    dayOffset = Integer.parseInt(reader.getElementText());
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if ("Timeband".equals(reader.getLocalName())) {
                    if (startTime != null && endTime != null) {
                        TimebandModel tb = new TimebandModel(startTime, endTime, dayOffset);
                        timebandsByAvailabilityCondition
                                .computeIfAbsent(currentAvailabilityConditionId, k -> new java.util.ArrayList<>())
                                .add(tb);
                    }
                    return;
                }
            }
        }
    }

    private static class TimebandModel {
        final String startTime;
        final String endTime;
        final int dayOffset;

        TimebandModel(String startTime, String endTime, int dayOffset) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.dayOffset = dayOffset;
        }
    }

    private static class PatternModel {
        final String id;
        final java.util.List<StopPointModel> stops;

        PatternModel(String id, java.util.List<StopPointModel> stops) {
            this.id = id;
            this.stops = stops;
        }
    }

    private static class StopPointModel {
        int order;
        String stopPointRef;
        boolean forBoarding = true;
        boolean forAlighting = true;
    }

    // --- Standalone Parsing Extension ---

    private void parseScheduledStopPoint(XMLStreamReader reader) throws XMLStreamException {
        String id = reader.getAttributeValue(null, "id");
        String name = null;
        double x = 0;
        double y = 0;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();
                if ("Name".equals(localName)) {
                    name = reader.getElementText();
                } else if ("pos".equals(localName)) { // gml:pos
                    String content = reader.getElementText();
                    if (content != null && !content.isEmpty()) {
                        String[] coords = content.trim().split("\\s+");
                        if (coords.length >= 2) {
                            try {
                                x = Double.parseDouble(coords[0]);
                                y = Double.parseDouble(coords[1]);
                            } catch (NumberFormatException e) {
                                LOG.warn("Invalid coordinate format for stop {}: {}", id, content);
                            }
                        }
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if ("ScheduledStopPoint".equals(reader.getLocalName())) {
                    createRegularStop(id, name, x, y);
                    return;
                }
            }
        }
    }

    private void createRegularStop(String id, String name, double x, double y) {
        if (x == 0 || y == 0) {
            // LOG.warn("Skipping ScheduledStopPoint {} with invalid coordinates {}.", id, x
            // + "," + y);
            // Allow 0,0 for debugging to see if indices are created
            // return;
        }

        // Use shared builder to ensure IndexCounter is incremented correctly
        FeedScopedId stopId = new FeedScopedId(feedId, id);
        double[] latLon = RdToWgs84.convert(x, y);

        org.opentripplanner.transit.model.site.RegularStopBuilder stopBuilder = builder.siteRepository()
                .regularStop(stopId);
        org.opentripplanner.transit.model.site.RegularStop stop = stopBuilder
                .withName(I18NString.of(name))
                .withCoordinate(latLon[0], latLon[1])
                .build();

        builder.stopsByScheduledStopPoints().put(stopId, stop);
    }

    private void parseLine(XMLStreamReader reader) throws XMLStreamException {
        String id = reader.getAttributeValue(null, "id");
        String name = null;
        String transportMode = null;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();
                if ("Name".equals(localName)) {
                    name = reader.getElementText();
                } else if ("TransportMode".equals(localName)) {
                    transportMode = reader.getElementText();
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if ("Line".equals(reader.getLocalName())) {
                    createRoute(id, name, transportMode);
                    return;
                }
            }
        }
    }

    private void createRoute(String id, String name, String mode) {
        FeedScopedId routeId = new FeedScopedId(feedId, id);

        // Ensure Agency exists (Stubbing for standalone if missing)
        org.opentripplanner.transit.model.organization.Agency agency = builder.getAgenciesById().values().stream()
                .findFirst().orElse(null);
        if (agency == null) {
            FeedScopedId agencyId = new FeedScopedId(feedId, "AGENCY_STUB");
            agency = org.opentripplanner.transit.model.organization.Agency.of(agencyId)
                    .withName("Stub Agency")
                    .withTimezone("Europe/Amsterdam")
                    .build();
            builder.getAgenciesById().add(agency);
        }

        Route route = org.opentripplanner.transit.model.network.Route.of(routeId)
                .withAgency(agency)
                .withShortName(name)
                .withLongName(I18NString.of(name))
                .withMode(org.opentripplanner.transit.model.basic.TransitMode.BUS) // Defaulting to Bus
                .build();

        builder.getRoutes().add(route);
    }

    private void parseFlexibleStopPlace(XMLStreamReader reader) throws XMLStreamException {
        String id = reader.getAttributeValue(null, "id");
        String name = null;
        Set<String> stopRefs = new HashSet<>();

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();
                if ("Name".equals(localName)) {
                    name = reader.getElementText();
                } else if ("ScheduledStopPointRef".equals(localName)) {
                    stopRefs.add(reader.getAttributeValue(null, "ref"));
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if ("FlexibleStopPlace".equals(reader.getLocalName())) {
                    createStation(id, name, stopRefs);
                    return;
                }
            }
        }
    }

    private void createStation(String id, String name, Set<String> stopRefs) {
        FeedScopedId stationId = new FeedScopedId(feedId, id);

        Set<org.opentripplanner.transit.model.site.RegularStop> children = new HashSet<>();
        double sumLat = 0;
        double sumLon = 0;
        int count = 0;

        for (String ref : stopRefs) {
            FeedScopedId stopRefId = new FeedScopedId(feedId, ref);
            org.opentripplanner.transit.model.site.RegularStop stop = builder.stopsByScheduledStopPoints()
                    .get(stopRefId);
            if (stop != null) {
                children.add(stop);
                sumLat += stop.getLat();
                sumLon += stop.getLon();
                count++;
            }
        }

        if (count == 0) {
            LOG.warn("FlexibleStopPlace {} has no known child stops, skipping.", id);
            return;
        }

        double centroidLat = sumLat / count;
        double centroidLon = sumLon / count;

        Station station = Station.of(stationId)
                .withName(I18NString.of(name))
                .withCoordinate(centroidLat, centroidLon)
                .build();

        // Add station to repository
        builder.siteRepository().withStation(station);

        // Update children to have parent station
        for (org.opentripplanner.transit.model.site.RegularStop child : children) {
            org.opentripplanner.transit.model.site.RegularStop updatedStop = child.copy()
                    .withParentStation(station)
                    .build();
            // Update the map
            builder.stopsByScheduledStopPoints().put(updatedStop.getId(), updatedStop);
        }
    }

    // RD New (EPSG:28992) to WGS84 Conversion
    // Approximation based on standard formulas
    private static class RdToWgs84 {
        public static double[] convert(double x, double y) {
            int referenceRdX = 155000;
            int referenceRdY = 463000;
            double dX = (x - referenceRdX) * Math.pow(10, -5);
            double dY = (y - referenceRdY) * Math.pow(10, -5);
            double sumLat = (3235.65389 * dY) + (-32.58297 * Math.pow(dX, 2)) + (-0.2475 * Math.pow(dY, 2))
                    + (-0.84978 * Math.pow(dX, 2) * dY) + (-0.0655 * Math.pow(dY, 3))
                    + (-0.01709 * Math.pow(dX, 2) * Math.pow(dY, 2)) + (-0.00738 * dX)
                    + (0.0053 * Math.pow(dX, 4)) + (-0.00039 * Math.pow(dX, 2) * Math.pow(dY, 3))
                    + (0.00033 * Math.pow(dX, 4) * dY) + (-0.00012 * dX * dY);
            double sumLon = (5260.52916 * dX) + (105.94684 * dX * dY) + (2.45656 * dX * Math.pow(dY, 2))
                    + (-0.81885 * Math.pow(dX, 3)) + (0.05594 * dX * Math.pow(dY, 3))
                    + (-0.05607 * Math.pow(dX, 3) * dY) + (0.01277 * dX * Math.pow(dY, 4))
                    + (-0.00256 * Math.pow(dX, 3) * Math.pow(dY, 2)) + (0.00128 * dX * Math.pow(dY, 5))
                    + (0.00026 * Math.pow(dX, 5)) + (-0.00022 * Math.pow(dX, 3) * Math.pow(dY, 3))
                    + (0.00026 * Math.pow(dX, 5) * dY);

            double lat = 52.15517 + (sumLat / 3600);
            double lon = 5.387206 + (sumLon / 3600);

            return new double[] { lat, lon };
        }
    }
}
