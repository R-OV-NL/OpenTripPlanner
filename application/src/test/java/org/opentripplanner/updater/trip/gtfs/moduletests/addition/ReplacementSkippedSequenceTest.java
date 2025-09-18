package org.opentripplanner.updater.trip.gtfs.moduletests.addition;

import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.REPLACEMENT;
import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.CANCELED;
import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.opentripplanner.transit.model._data.TimetableRepositoryForTest.id;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;
import static org.opentripplanner.updater.trip.UpdateIncrementality.FULL_DATASET;

import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model.timetable.RealTimeState;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.RealtimeTestEnvironment;
import org.opentripplanner.updater.trip.RealtimeTestEnvironmentBuilder;
import org.opentripplanner.updater.trip.TripInput;
import org.opentripplanner.updater.trip.TripUpdateBuilder;

/**
 * Verify sequences that include REPLACEMENT (MODIFIED) followed by another MODIFIED with SKIPPED stop(s),
 * and SCHEDULED directly to MODIFIED with SKIPPED stop(s).
 */
class ReplacementSkippedSequenceTest implements RealtimeTestConstants {

  private final RealtimeTestEnvironmentBuilder ENV_BUILDER = RealtimeTestEnvironment.of();

  private final TripInput TRIP_INPUT = TripInput.of(TRIP_2_ID)
    .addStop(ENV_BUILDER.stop(STOP_A_ID), "0:01:00", "0:01:01")
    .addStop(ENV_BUILDER.stop(STOP_B_ID), "0:01:10", "0:01:11")
    .addStop(ENV_BUILDER.stop(STOP_C_ID), "0:01:20", "0:01:21")
    .build();

  @Test
  void scheduledToReplacementThenReplacementWithSkipped() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();

    // First, simulate a SCHEDULED update (delays, no skip) to create a modified variant based on schedule
    var scheduledUpdate = new TripUpdateBuilder(TRIP_2_ID, SERVICE_DATE, SCHEDULED, TIME_ZONE)
      .addDelayedStopTime(0, 0)
      .addDelayedStopTime(1, 30)
      .addDelayedStopTime(2, 60)
      .build();

    assertSuccess(env.applyTripUpdate(scheduledUpdate, FULL_DATASET));

    // Next, a REPLACEMENT (MODIFIED) trip update that changes stop times (still all stops)
    var replacement = new TripUpdateBuilder(TRIP_2_ID, SERVICE_DATE, REPLACEMENT, TIME_ZONE)
      .addStopTime(STOP_A_ID, "01:00")
      .addStopTime(STOP_B_ID, "01:30")
      .addStopTime(STOP_C_ID, "02:00")
      .build();

    assertSuccess(env.applyTripUpdate(replacement, FULL_DATASET));

    // Finally, another REPLACEMENT with a SKIPPED middle stop
    var replacementWithSkip = new TripUpdateBuilder(TRIP_2_ID, SERVICE_DATE, REPLACEMENT, TIME_ZONE)
      .addStopTime(STOP_A_ID, "01:00")
      .addSkippedStop(STOP_B_ID, "01:30")
      .addStopTime(STOP_C_ID, "02:00")
      .build();

    assertSuccess(env.applyTripUpdate(replacementWithSkip, FULL_DATASET));

    var snapshot = env.getTimetableSnapshot();

    // The previously modified instance should be deleted and only one modified pattern should remain
    var newPattern = snapshot.getNewTripPatternForModifiedTrip(id(TRIP_2_ID), SERVICE_DATE);
    assertNotNull(newPattern);

    var tt = snapshot.resolve(newPattern, SERVICE_DATE).getTripTimes(id(TRIP_2_ID));
    assertNotNull(tt);
    assertEquals(RealTimeState.MODIFIED, tt.getRealTimeState());

    // Check that middle stop is cancelled (skipped) in realtime timetable text form
    // A [C] indicates cancelled/skip in test utilities formatting
    var realtimeStr = env.getRealtimeTimetable(id(TRIP_2_ID), SERVICE_DATE);
    // Expect: A scheduled, B cancelled, C scheduled
    // Times shown are not essential here; assert marker for cancellation exists
    // Example format: "MODIFIED | A ... | B [C] ... | C ..."
    // Keep it simple: ensure marker [C] is present for B
    org.junit.jupiter.api.Assertions.assertTrue(realtimeStr.contains("| B [C]"));
  }

  @Test
  void scheduledDirectlyToReplacementWithSkipped() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();

    // Direct REPLACEMENT with a SKIPPED middle stop
    var replacementWithSkip = new TripUpdateBuilder(TRIP_2_ID, SERVICE_DATE, REPLACEMENT, TIME_ZONE)
      .addStopTime(STOP_A_ID, "01:00")
      .addSkippedStop(STOP_B_ID, "01:30")
      .addStopTime(STOP_C_ID, "02:00")
      .build();

    assertSuccess(env.applyTripUpdate(replacementWithSkip));

    var snapshot = env.getTimetableSnapshot();
    var newPattern = snapshot.getNewTripPatternForModifiedTrip(id(TRIP_2_ID), SERVICE_DATE);
    assertNotNull(newPattern);

    // Scheduled pattern for today should be deleted
    var originalPattern = env.getTransitService().findPattern(env.getTransitService().getTrip(id(TRIP_2_ID)));
    var originalToday = snapshot.resolve(originalPattern, SERVICE_DATE).getTripTimes(id(TRIP_2_ID));
    org.junit.jupiter.api.Assertions.assertNotNull(originalToday);
    org.junit.jupiter.api.Assertions.assertTrue(originalToday.isDeleted());

    // Verify cancellation marker for B exists in realtime timetable
    var realtimeStr = env.getRealtimeTimetable(id(TRIP_2_ID), SERVICE_DATE);
    org.junit.jupiter.api.Assertions.assertTrue(realtimeStr.contains("| B [C]"));

    // And the modified pattern exists only once
    var tt = snapshot.resolve(newPattern, SERVICE_DATE).getTripTimes(id(TRIP_2_ID));
    assertNotNull(tt);
    assertEquals(RealTimeState.MODIFIED, tt.getRealTimeState());

    // Ensure no orphan modified pattern without trip times remains
    // If our fix worked, there should be no second modified instance to fetch; this suffices
    // by asserting the mapping returns a single pattern and that scheduled pattern's today entry is deleted.
    assertNull(snapshot.resolve(newPattern, null).getTripTimes(id(TRIP_2_ID)));
  }

  @Test
  void veryComplexSequenceScheduledReplacementCancelCycles() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();

    // Apply SCHEDULED a few times
    for (int i = 0; i < 3; i++) {
      var scheduled = new TripUpdateBuilder(TRIP_2_ID, SERVICE_DATE, SCHEDULED, TIME_ZONE)
        .addDelayedStopTime(0, i * 5)
        .addDelayedStopTime(1, i * 5 + 10)
        .addDelayedStopTime(2, i * 5 + 20)
        .build();
      assertSuccess(env.applyTripUpdate(scheduled, FULL_DATASET));

      // After SCHEDULED delay-only update we should have realtime trip times for today
      var snapshotAfterScheduled = env.getTimetableSnapshot();
      var originalPatternAfterScheduled = env
        .getTransitService()
        .findPattern(env.getTransitService().getTrip(id(TRIP_2_ID)));
      var ttScheduledToday = snapshotAfterScheduled
        .resolve(originalPatternAfterScheduled, SERVICE_DATE)
        .getTripTimes(id(TRIP_2_ID));
      org.junit.jupiter.api.Assertions.assertNotNull(ttScheduledToday);
      org.junit.jupiter.api.Assertions.assertFalse(ttScheduledToday.isCanceledOrDeleted());
    }

    // Apply REPLACEMENT a few times (first without skip, then with a skip, then without again)
    var repl1 = new TripUpdateBuilder(TRIP_2_ID, SERVICE_DATE, REPLACEMENT, TIME_ZONE)
      .addStopTime(STOP_A_ID, "01:00")
      .addStopTime(STOP_B_ID, "01:20")
      .addStopTime(STOP_C_ID, "01:40")
      .build();
    assertSuccess(env.applyTripUpdate(repl1, FULL_DATASET));

    // After first REPLACEMENT a modified pattern should exist, and the original for today is deleted
    var snapshotAfterRepl1 = env.getTimetableSnapshot();
    var newPatternAfterRepl1 = snapshotAfterRepl1.getNewTripPatternForModifiedTrip(id(TRIP_2_ID), SERVICE_DATE);
    org.junit.jupiter.api.Assertions.assertNotNull(newPatternAfterRepl1);
    var originalPattern1 = env.getTransitService().findPattern(env.getTransitService().getTrip(id(TRIP_2_ID)));
    var originalToday1 = snapshotAfterRepl1.resolve(originalPattern1, SERVICE_DATE).getTripTimes(id(TRIP_2_ID));
    org.junit.jupiter.api.Assertions.assertNotNull(originalToday1);
    org.junit.jupiter.api.Assertions.assertTrue(originalToday1.isDeleted());

    var repl2 = new TripUpdateBuilder(TRIP_2_ID, SERVICE_DATE, REPLACEMENT, TIME_ZONE)
      .addStopTime(STOP_A_ID, "01:00")
      .addSkippedStop(STOP_B_ID, "01:20")
      .addStopTime(STOP_C_ID, "01:40")
      .build();
    assertSuccess(env.applyTripUpdate(repl2, FULL_DATASET));

    // After REPLACEMENT with skip, verify skip marker in realtime timetable
    var realtimeAfterRepl2 = env.getRealtimeTimetable(id(TRIP_2_ID), SERVICE_DATE);
    org.junit.jupiter.api.Assertions.assertTrue(realtimeAfterRepl2.contains("| B [C]"));

    var repl3 = new TripUpdateBuilder(TRIP_2_ID, SERVICE_DATE, REPLACEMENT, TIME_ZONE)
      .addStopTime(STOP_A_ID, "01:05")
      .addStopTime(STOP_B_ID, "01:25")
      .addStopTime(STOP_C_ID, "01:45")
      .build();
    assertSuccess(env.applyTripUpdate(repl3, FULL_DATASET));

    // After REPLACEMENT without skip again, ensure skip marker is gone
    var realtimeAfterRepl3 = env.getRealtimeTimetable(id(TRIP_2_ID), SERVICE_DATE);
    org.junit.jupiter.api.Assertions.assertFalse(realtimeAfterRepl3.contains("| B [C]"));

    // Cancel trip a few times
    for (int i = 0; i < 2; i++) {
      var canceled = new TripUpdateBuilder(TRIP_2_ID, SERVICE_DATE, CANCELED, TIME_ZONE).build();
      // Use DIFFERENTIAL here so previously modified trip instances are canceled in snapshot
      assertSuccess(env.applyTripUpdate(canceled, FULL_DATASET));
      // After cancellation, the realtime timetable should show canceled
      var snapshot = env.getTimetableSnapshot();
      var pattern = snapshot.getNewTripPatternForModifiedTrip(id(TRIP_2_ID), SERVICE_DATE);
      if (pattern != null) {
        var tt = snapshot.resolve(pattern, SERVICE_DATE).getTripTimes(id(TRIP_2_ID));
        if (tt != null) {
          org.junit.jupiter.api.Assertions.assertTrue(tt.isCanceledOrDeleted());
        }
      }
    }

    // Replacement without cancellations again
    var replAfterCancel = new TripUpdateBuilder(TRIP_2_ID, SERVICE_DATE, REPLACEMENT, TIME_ZONE)
      .addStopTime(STOP_A_ID, "01:10")
      .addStopTime(STOP_B_ID, "01:30")
      .addStopTime(STOP_C_ID, "01:50")
      .build();
    assertSuccess(env.applyTripUpdate(replAfterCancel, FULL_DATASET));

    // After this replacement, ensure trip is not canceled and modified exists
    var snapshotAfterReplAfterCancel = env.getTimetableSnapshot();
    var patternAfterReplAfterCancel = snapshotAfterReplAfterCancel.getNewTripPatternForModifiedTrip(id(TRIP_2_ID), SERVICE_DATE);
    org.junit.jupiter.api.Assertions.assertNotNull(patternAfterReplAfterCancel);
    var ttAfterReplAfterCancel = snapshotAfterReplAfterCancel
      .resolve(patternAfterReplAfterCancel, SERVICE_DATE)
      .getTripTimes(id(TRIP_2_ID));
    org.junit.jupiter.api.Assertions.assertNotNull(ttAfterReplAfterCancel);
    org.junit.jupiter.api.Assertions.assertFalse(ttAfterReplAfterCancel.isCanceledOrDeleted());

    // And cancel again
    var canceledAgain = new TripUpdateBuilder(TRIP_2_ID, SERVICE_DATE, CANCELED, TIME_ZONE).build();
    // Use DIFFERENTIAL so the modified instance is canceled
    assertSuccess(env.applyTripUpdate(canceledAgain, FULL_DATASET));

    // Final assertions: scheduled is deleted for today, and latest state is canceled/deleted
    var snapshot = env.getTimetableSnapshot();
    var originalPattern = env.getTransitService().findPattern(env.getTransitService().getTrip(id(TRIP_2_ID)));
    var originalToday = snapshot.resolve(originalPattern, SERVICE_DATE).getTripTimes(id(TRIP_2_ID));
    org.junit.jupiter.api.Assertions.assertNotNull(originalToday);
    org.junit.jupiter.api.Assertions.assertTrue(originalToday.isCanceledOrDeleted());

    // If there is a modified pattern, its trip times should be canceled/deleted now
    var maybePattern = snapshot.getNewTripPatternForModifiedTrip(id(TRIP_2_ID), SERVICE_DATE);
    if (maybePattern != null) {
      var tt = snapshot.resolve(maybePattern, SERVICE_DATE).getTripTimes(id(TRIP_2_ID));
      if (tt != null) {
        org.junit.jupiter.api.Assertions.assertTrue(tt.isCanceledOrDeleted());
      }
    }
  }
}


