package org.opentripplanner.gtfs.graphbuilder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.opentripplanner.framework.json.ObjectMappers;
import org.opentripplanner.framework.i18n.NonLocalizedString;
import org.opentripplanner.framework.geometry.WgsCoordinate;
import org.opentripplanner.model.impl.OtpTransitServiceBuilder;
import org.opentripplanner.transit.model.framework.FeedScopedId;
import org.opentripplanner.transit.model.site.GroupOfStations;
import org.opentripplanner.transit.model.site.GroupOfStationsBuilder;
import org.opentripplanner.transit.model.site.Station;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads optional sidecar configuration for GroupOfStations from a GTFS bundle. If a file named
 * {@code group_of_stations.json} is present at the root of the GTFS data source, it is parsed and
 * the defined groups are added to the {@link OtpTransitServiceBuilder}'s {@link
 * org.opentripplanner.transit.service.SiteRepositoryBuilder}.
 *
 * The JSON schema is a list of objects:
 * [
 *   {
 *     "id": "PLACE_ABC", // or "FEED:PLACE_ABC"
 *     "name": "Place ABC",
 *     "coordinate": { "lat": 52.0, "lon": 5.0 }, // optional, computed from children if missing
 *     "stations": ["ST1", "ST2"] // or feed-scoped ids
 *   }
 * ]
 */
public class GroupOfStationsSidecarLoader {

  private static final Logger LOG = LoggerFactory.getLogger(GroupOfStationsSidecarLoader.class);

  private static final String SIDECAR_FILENAME = "group_of_stations.json";

  private static final ObjectMapper MAPPER = ObjectMappers.ignoringExtraFields();

  public static void loadIntoBuilder(GtfsBundle bundle, OtpTransitServiceBuilder builder) {
    try {
      // 1) Try working directory sidecar with feed-specific filename, then default filename
      if (loadFromWorkingDirectory(bundle, builder)) {
        return;
      }

      // 2) Fallback to GTFS bundle sidecar
      var csv = bundle.getCsvInputSource();
      if (!csv.hasResource(SIDECAR_FILENAME)) {
        return;
      }
      LOG.info("Loading GroupOfStations from {} for feed {}", SIDECAR_FILENAME, bundle.getFeedId());
      try (InputStream is = csv.getResource(SIDECAR_FILENAME)) {
        loadFromStream(is, bundle, builder);
      }
    } catch (Exception e) {
      LOG.warn("Failed to load GroupOfStations sidecar for feed {}: {}", bundle.getFeedId(), e.toString());
    }
  }

  private static boolean loadFromWorkingDirectory(GtfsBundle bundle, OtpTransitServiceBuilder builder) {
    try {
      String preferred = "group_of_stations_" + bundle.getFeedId() + ".json";
      java.io.File preferredFile = new java.io.File(preferred);
      if (preferredFile.exists() && preferredFile.isFile()) {
        LOG.info(
          "Loading GroupOfStations from working directory file '{}' for feed {}",
          preferredFile.getPath(),
          bundle.getFeedId()
        );
        try (InputStream is = new java.io.FileInputStream(preferredFile)) {
          loadFromStream(is, bundle, builder);
          return true;
        }
      }

      java.io.File defaultFile = new java.io.File(SIDECAR_FILENAME);
      if (defaultFile.exists() && defaultFile.isFile()) {
        LOG.info(
          "Loading GroupOfStations from working directory file '{}' for feed {}",
          defaultFile.getPath(),
          bundle.getFeedId()
        );
        try (InputStream is = new java.io.FileInputStream(defaultFile)) {
          loadFromStream(is, bundle, builder);
          return true;
        }
      }
    } catch (Exception e) {
      LOG.warn("Failed loading working directory GroupOfStations file: {}", e.toString());
    }
    return false;
  }

  private static void loadFromStream(InputStream is, GtfsBundle bundle, OtpTransitServiceBuilder builder) throws Exception {
    List<GroupSpec> specs = MAPPER.readValue(is, new TypeReference<List<GroupSpec>>() {});
    for (GroupSpec spec : specs) {
      addGroup(spec, bundle, builder);
    }
  }

  private static void addGroup(GroupSpec spec, GtfsBundle bundle, OtpTransitServiceBuilder builder) {
    String feedId = bundle.getFeedId();
    FeedScopedId groupId = toFeedScopedId(feedId, spec.id);

    List<Station> childStations = new ArrayList<>();
    for (String s : spec.stations) {
      var stationId = toFeedScopedId(feedId, s);
      Station station = builder.siteRepository().stationById().get(stationId);
      if (station == null) {
        LOG.warn("GroupOfStations {} references unknown station {}", groupId, stationId);
      } else {
        childStations.add(station);
      }
    }
    if (childStations.isEmpty()) {
      LOG.warn("GroupOfStations {} has no valid child stations. Skipping.", groupId);
      return;
    }

    GroupOfStationsBuilder gos = GroupOfStations.of(groupId)
      .withName(new NonLocalizedString(Objects.requireNonNullElse(spec.name, groupId.toString())));

    WgsCoordinate coordinate = coordinateOrCentroid(spec, childStations);
    if (coordinate != null) {
      gos.withCoordinate(coordinate);
    }

    // Attach all stations (as StopLocationsGroup, Station implements it)
    childStations.forEach(gos::addChildStation);

    builder.siteRepository().withGroupOfStation(gos.build());

    LOG.info(
      "Registered GroupOfStations {} with {} child stations: {}",
      groupId,
      childStations.size(),
      childStations.stream().map(s -> s.getId().toString()).collect(Collectors.joining(", "))
    );
  }

  private static FeedScopedId toFeedScopedId(String defaultFeedId, String idOrScoped) {
    int idx = idOrScoped.indexOf(':');
    if (idx > 0) {
      return new FeedScopedId(idOrScoped.substring(0, idx), idOrScoped.substring(idx + 1));
    }
    return new FeedScopedId(defaultFeedId, idOrScoped);
  }

  @Nullable
  private static WgsCoordinate coordinateOrCentroid(GroupSpec spec, List<Station> stations) {
    if (spec.coordinate != null) {
      return new WgsCoordinate(spec.coordinate.lon, spec.coordinate.lat);
    }
    // Compute simple average of child station coordinates if available
    var coords = stations
      .stream()
      .map(Station::getCoordinate)
      .filter(Objects::nonNull)
      .toList();
    if (coords.isEmpty()) {
      return null;
    }
    double avgLon = coords.stream().mapToDouble(WgsCoordinate::longitude).average().orElse(0.0);
    double avgLat = coords.stream().mapToDouble(WgsCoordinate::latitude).average().orElse(0.0);
    return new WgsCoordinate(avgLat, avgLon);
  }

  static class GroupSpec {
    public String id;
    public String name;
    public List<String> stations = List.of();
    public CoordinateSpec coordinate;
  }

  static class CoordinateSpec {
    public double lat;
    public double lon;
  }
}


