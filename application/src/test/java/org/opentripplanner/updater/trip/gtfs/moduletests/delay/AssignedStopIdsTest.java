package org.opentripplanner.updater.trip.gtfs.moduletests.delay;

import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model._data.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model._data.TripInput;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.trip.GtfsRtTestHelper;
import org.opentripplanner.updater.trip.RealtimeTestConstants;

/**
 * Tests that stops can be SKIPPED for a trip which repeats times for consecutive stops.
 *
 * @link <a href="https://github.com/opentripplanner/OpenTripPlanner/issues/6848">issue</a>
 */
class AssignedStopIdsTest implements RealtimeTestConstants {

  private final TransitTestEnvironmentBuilder ENV_BUILDER = TransitTestEnvironment.of();
  private final RegularStop STOP_A = ENV_BUILDER.stop(STOP_A_ID);
  private final RegularStop STOP_B = ENV_BUILDER.stop(STOP_B_ID);
  private final RegularStop STOP_C = ENV_BUILDER.stop(STOP_C_ID);

  private final TripInput TRIP_INPUT = TripInput.of(TRIP_1_ID)
    .addStop(STOP_A, "10:00:00", "10:01:00")
    .addStop(STOP_B, "10:01:00", "10:01:00")
    .addStop(STOP_C, "10:01:00", "10:02:00");

  @Test
  void assignedThenRevertedStopIds() {
    ENV_BUILDER.stop(STOP_D_ID);
    ENV_BUILDER.stop(STOP_E_ID);

    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var rt = GtfsRtTestHelper.of(env);

    assertFalse(tripPattern(env).isCreatedByRealtimeUpdater());
    assertEquals("F:Pattern1", tripPatternId(env));

    var tripUpdate1 = rt
      .tripUpdate(TRIP_1_ID, SCHEDULED)
      .addAssignedStopTime(0, "09:50:00", STOP_D_ID)
      .addStopTime(1, "10:01:00")
      .addStopTime(2, "10:02:00")
      .build();

    assertSuccess(rt.applyTripUpdate(tripUpdate1));
    assertEquals(
      "UPDATED | D 9:50 9:50 | B 10:01 10:01 | C 10:02 10:02",
      env.tripData(TRIP_1_ID).showTimetable()
    );
    assertTrue(tripPattern(env).isCreatedByRealtimeUpdater());
    assertEquals("F:Route1::rt#1", tripPatternId(env));

    var tripUpdate2 = rt
      .tripUpdate(TRIP_1_ID, SCHEDULED)
      .addAssignedStopTime(0, "09:55:00", STOP_E_ID)
      .addStopTime(1, "10:01:00")
      .addStopTime(2, "10:02:00")
      .build();

    assertSuccess(rt.applyTripUpdate(tripUpdate2));
    assertEquals(
      "UPDATED | E 9:55 9:55 | B 10:01 10:01 | C 10:02 10:02",
      env.tripData(TRIP_1_ID).showTimetable()
    );
    assertTrue(tripPattern(env).isCreatedByRealtimeUpdater());
    assertEquals("F:Route1::rt#2", tripPatternId(env));

    var tripUpdate3 = rt
      .tripUpdate(TRIP_1_ID, SCHEDULED)
      .addAssignedStopTime(0, "10:01:00", STOP_A_ID)
      .addStopTime(1, "10:02:00")
      .addStopTime(2, "10:03:00")
      .build();

    assertSuccess(rt.applyTripUpdate(tripUpdate3));
    assertEquals(
      "UPDATED | A 10:01 10:01 | B 10:02 10:02 | C 10:03 10:03",
      env.tripData(TRIP_1_ID).showTimetable()
    );

    assertFalse(tripPattern(env).isCreatedByRealtimeUpdater());
    assertEquals("F:Pattern1", tripPatternId(env));
  }

  private TripPattern tripPattern(TransitTestEnvironment env) {
    return env.tripData(TRIP_1_ID).tripPattern();
  }

  private String tripPatternId(TransitTestEnvironment env) {
    return tripPattern(env).getId().toString();
  }
}
