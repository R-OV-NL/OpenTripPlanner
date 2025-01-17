package org.opentripplanner.netex.mapping;

import javax.annotation.Nullable;
import org.opentripplanner.framework.geometry.WgsCoordinate;
import org.rutebanken.netex.model.LocationStructure;
import org.rutebanken.netex.model.SimplePoint_VersionStructure;

import java.util.List;

class WgsCoordinateMapper {

  /**
   * This utility method check if the given {@code point} or one of its sub elements is {@code null}
   * before passing the location to the given {@code locationHandler}.
   *
   * @return true if the handler is successfully invoked with a location, {@code false} if any of
   * the required data elements are {@code null}.
   */
  @Nullable
  static WgsCoordinate mapToDomain(SimplePoint_VersionStructure point) {
    if (point == null || point.getLocation() == null) {
      return null;
    }
    LocationStructure loc = point.getLocation();


    // This should not happen
    if (loc.getLongitude() == null || loc.getLatitude() == null) {

      if (loc.getPos() != null && loc.getPos().getValue().size() == 2) {
        return rdToWgs84(loc.getPos().getValue());
      }

      throw new IllegalArgumentException("Coordinate is not valid: " + loc);
    }


    // Location is safe to process
    return new WgsCoordinate(loc.getLatitude().doubleValue(), loc.getLongitude().doubleValue());
  }

  private static WgsCoordinate rdToWgs84(List<Double> rd) {
    double rdX = rd.get(0);
    double rdY = rd.get(1);

    double dX = (rdX - 155000) * Math.pow(10, -5);
    double dY = (rdY - 463000) * Math.pow(10, -5);

    double somN = (3235.65389 * dY) +
            (-32.58297 * Math.pow(dX, 2)) +
            (-0.2475 * Math.pow(dY, 2)) +
            (-0.84978 * Math.pow(dX, 2) * dY) +
            (-0.0655 * Math.pow(dY, 3)) +
            (-0.01709 * Math.pow(dX, 2) * Math.pow(dY, 2)) +
            (-0.00738 * dX) +
            (0.0053 * Math.pow(dX, 4)) +
            (-0.00039 * Math.pow(dX, 2) * Math.pow(dY, 3)) +
            (0.00033 * Math.pow(dX, 4) * dY) +
            (-0.00012 * dX * dY);

    double somE = (5260.52916 * dX) +
            (105.94684 * dX * dY) +
            (2.45656 * dX * Math.pow(dY, 2)) +
            (-0.81885 * Math.pow(dX, 3)) +
            (0.05594 * dX * Math.pow(dY, 3)) +
            (-0.05607 * Math.pow(dX, 3) * dY) +
            (0.01199 * dY) +
            (-0.00256 * Math.pow(dX, 3) * Math.pow(dY, 2)) +
            (0.00128 * dX * Math.pow(dY, 4)) +
            (0.00022 * Math.pow(dY, 2)) +
            (-0.00022 * Math.pow(dX, 2)) +
            (0.00026 * Math.pow(dX, 5));

    double latitude = 52.15517 + (somN / 3600);
    double longitude = 5.387206 + (somE / 3600);

    return new WgsCoordinate(latitude, longitude);
  }
}
