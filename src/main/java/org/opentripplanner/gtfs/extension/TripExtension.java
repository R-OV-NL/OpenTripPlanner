package org.opentripplanner.gtfs.extension;

import org.onebusaway.csv_entities.schema.annotations.CsvField;

public class TripExtension {
  @CsvField(optional = true)
  private String realtimeTripId;

  public String getRealtimeTripId() {
    return realtimeTripId;
  }

  public void setRealtimeTripId(String realtimeTripId) {
    this.realtimeTripId = realtimeTripId;
  }
}
