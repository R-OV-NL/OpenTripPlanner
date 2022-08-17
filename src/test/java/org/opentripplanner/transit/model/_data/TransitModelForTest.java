package org.opentripplanner.transit.model._data;

import static org.opentripplanner.transit.model.basic.WheelchairAccessibility.NO_INFORMATION;

import org.opentripplanner.model.PickDrop;
import org.opentripplanner.model.StopTime;
import org.opentripplanner.transit.model.basic.NonLocalizedString;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.basic.WgsCoordinate;
import org.opentripplanner.transit.model.basic.WheelchairAccessibility;
import org.opentripplanner.transit.model.framework.FeedScopedId;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.RouteBuilder;
import org.opentripplanner.transit.model.network.StopPattern;
import org.opentripplanner.transit.model.organization.Agency;
import org.opentripplanner.transit.model.site.Station;
import org.opentripplanner.transit.model.site.StationBuilder;
import org.opentripplanner.transit.model.site.Stop;
import org.opentripplanner.transit.model.site.StopBuilder;
import org.opentripplanner.transit.model.site.StopTransferPriority;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripBuilder;

/**
 * Test utility class to help construct valid transit model objects.
 */
public class TransitModelForTest {

  public static final String FEED_ID = "F";
  public static final String TIME_ZONE_ID = "Europe/Paris";

  public static final String OTHER_TIME_ZONE_ID = "America/Los_Angeles";
  public static final Agency AGENCY = Agency
    .of(id("A1"))
    .withName("Agency Test")
    .withTimezone(TIME_ZONE_ID)
    .withUrl("https://www.agency.com")
    .build();

  public static FeedScopedId id(String id) {
    return new FeedScopedId(FEED_ID, id);
  }

  public static Agency agency(String name) {
    return AGENCY.copy().withId(id(name)).withName(name).build();
  }

  /** Create a valid Bus Route to use in unit tests */
  public static RouteBuilder route(String id) {
    return Route.of(id(id)).withAgency(AGENCY).withShortName("R" + id).withMode(TransitMode.BUS);
  }

  /** Create a valid Bus Route to use in unit tests */
  public static TripBuilder trip(String id) {
    return Trip.of(id(id)).withRoute(route("R" + id).build());
  }

  public static Stop stopForTest(
    String idAndName,
    WheelchairAccessibility wheelchair,
    double lat,
    double lon
  ) {
    return stopForTest(idAndName, null, lat, lon, null, wheelchair);
  }

  public static StopBuilder stop(String idAndName) {
    return Stop
      .of(id(idAndName))
      .withName(new NonLocalizedString(idAndName))
      .withCode(idAndName)
      .withCoordinate(new WgsCoordinate(60.0, 10.0));
  }

  public static Stop stopForTest(String idAndName, double lat, double lon) {
    return stopForTest(idAndName, null, lat, lon, null, NO_INFORMATION);
  }

  public static Stop stopForTest(String idAndName, String desc, double lat, double lon) {
    return stopForTest(idAndName, desc, lat, lon, null, NO_INFORMATION);
  }

  public static Stop stopForTest(String idAndName, double lat, double lon, Station parent) {
    return stopForTest(idAndName, null, lat, lon, parent, NO_INFORMATION);
  }

  public static Stop stopForTest(
    String idAndName,
    String desc,
    double lat,
    double lon,
    Station parent
  ) {
    return stopForTest(idAndName, desc, lat, lon, parent, null);
  }

  public static Stop stopForTest(
    String idAndName,
    String desc,
    double lat,
    double lon,
    Station parent,
    WheelchairAccessibility wheelchair
  ) {
    return Stop
      .of(id(idAndName))
      .withName(new NonLocalizedString(idAndName))
      .withCode(idAndName)
      .withDescription(NonLocalizedString.ofNullable(desc))
      .withCoordinate(new WgsCoordinate(lat, lon))
      .withWheelchairAccessibility(wheelchair)
      .withParentStation(parent)
      .build();
  }

  public static StationBuilder station(String idAndName) {
    return Station
      .of(new FeedScopedId(FEED_ID, idAndName))
      .withName(new NonLocalizedString(idAndName))
      .withCode(idAndName)
      .withCoordinate(60.0, 10.0)
      .withDescription(new NonLocalizedString("Station " + idAndName))
      .withPriority(StopTransferPriority.ALLOWED);
  }

  public static StopTime stopTime(Trip trip, int seq) {
    var stopTime = new StopTime();
    stopTime.setTrip(trip);
    stopTime.setStopSequence(seq);

    var stop = TransitModelForTest.stopForTest("stop-" + seq, 0, 0);
    stopTime.setStop(stop);

    return stopTime;
  }

  public static StopTime stopTime(Trip trip, int seq, int time) {
    var stopTime = TransitModelForTest.stopTime(trip, seq);
    stopTime.setArrivalTime(time);
    stopTime.setDepartureTime(time);
    return stopTime;
  }

  public static StopPattern stopPattern(int numberOfStops) {
    var builder = StopPattern.create(numberOfStops);
    for (int i = 0; i < numberOfStops; i++) {
      builder.stops[i] = TransitModelForTest.stop("Stop_" + i).build();
      builder.pickups[i] = PickDrop.SCHEDULED;
      builder.dropoffs[i] = PickDrop.SCHEDULED;
    }
    return builder.build();
  }
}
