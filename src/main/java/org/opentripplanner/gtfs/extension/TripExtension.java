package org.opentripplanner.gtfs.extension;

import org.onebusaway.csv_entities.schema.annotations.CsvField;

public class TripExtension {

  @CsvField(optional = true)
  private String realtimeTripId;

  @CsvField(optional = true)
  private String tripLongName;

  public String getRealtimeTripId() {
    return realtimeTripId;
  }

  public void setRealtimeTripId(String realtimeTripId) {
    this.realtimeTripId = realtimeTripId;
  }

  public String getTripLongName() {
    return tripLongName;
  }

  public void setTripLongName(String longName) {
    this.tripLongName = longName;
  }
}
