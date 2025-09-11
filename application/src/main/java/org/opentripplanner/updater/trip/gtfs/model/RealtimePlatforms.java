package org.opentripplanner.updater.trip.gtfs.model;

import com.google.transit.realtime.GtfsRealtimeOVapi;

public class RealtimePlatforms {

  private final String stationCode;
  private final String scheduledPlatform;
  private final String actualPlatform;

  private RealtimePlatforms(String scheduledPlatform, String actualPlatform, String stationCode) {
    this.stationCode = stationCode;
    this.scheduledPlatform = scheduledPlatform;
    this.actualPlatform = actualPlatform;
  }

  public String scheduledPlatform() {
    return scheduledPlatform;
  }

  public String actualPlatform() {
    return actualPlatform;
  }

  public String stationCode() {
    return stationCode;
  }

  public static RealtimePlatforms ofStopTimeUpdate(StopTimeUpdate stopTimeUpdate) {
    var rawStopTimeUpdate = stopTimeUpdate.original();

    if (!rawStopTimeUpdate.hasExtension(GtfsRealtimeOVapi.ovapiStopTimeUpdate)) {
      // The OVapi extension is not present, return null
      return null;
    }

    var ext = rawStopTimeUpdate.getExtension(GtfsRealtimeOVapi.ovapiStopTimeUpdate);
    var scheduledPlatform = ext.getScheduledTrack();
    var actualPlatform = ext.getActualTrack();
    var stationCode = ext.getStationId();

    if(scheduledPlatform.isEmpty() && actualPlatform.isEmpty()){
      return null;
    }

    return new RealtimePlatforms(scheduledPlatform, actualPlatform, stationCode);
  }
}
