package org.opentripplanner.netex.support;

import static java.util.Comparator.comparingInt;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import org.rutebanken.netex.model.EntityInVersionStructure;
import org.rutebanken.netex.model.ValidBetween;

/**
 * Utility class to help working with versioned NeTEx element.
 * <p>
 * This class implements <em>Norwegian profile</em> specific rules.
 */
public class NetexVersionHelper {

  /**
   * @see NetexVersionHelper#versionOf(EntityInVersionStructure)
   */
  private static final String ANY = "any";
  /**
   * A special value that represents an unknown version.
   */
  private static final int UNKNOWN_VERSION = -1;

  /**
   * private constructor to prevent instantiation of utility class
   */
  private NetexVersionHelper() {}

  /**
   * According to the <b>Norwegian Netex profile</b> the version number must be a positive
   * increasing integer. A bigger value indicates a later version.
   * However, the special value "any" is also supported and returns a constant meaning "unknown".
   * The EPIP profile at
   * http://netex.uk/netex/doc/2019.05.07-v1.1_FinalDraft/prCEN_TS_16614-PI_Profile_FV_%28E%29-2019-Final-Draft-v3.pdf (page 33)
   * defines this as follows: "Use "any" if the VERSION is unknown (note that this will trigger NeTEx's
   * XML automatic consistency check)."
   */
  public static int versionOf(EntityInVersionStructure e) {
    if (e.getVersion().equals(ANY)) {
      return UNKNOWN_VERSION;
    }

    try {
      return Integer.parseInt(e.getVersion());
    } catch (NumberFormatException ex) {
      //Get any sequence of numbers from the string
      String version = e.getVersion().replaceAll("\\D+", "");

      if (version.isEmpty()) {
        return 0;
      } else if (version.length() > 8) {
        return Integer.parseInt(version.substring(0, 8));
      } else {
        return Integer.parseInt(version);
      }
    }
  }

  /**
   * Return the latest (maximum) version number for the given {@code list} of elements. If no
   * elements exist in the collection {@code -1} is returned.
   */
  public static int latestVersionIn(Collection<? extends EntityInVersionStructure> list) {
    return list.stream().mapToInt(NetexVersionHelper::versionOf).max().orElse(UNKNOWN_VERSION);
  }

  /**
   * Return the element with the latest (maximum) version for a given {@code list} of elements. If
   * no elements exist in the collection {@code null} is returned.
   */
  public static <T extends EntityInVersionStructure> T latestVersionedElementIn(
    Collection<T> list
  ) {
    return list.stream().max(comparingVersion()).orElse(null);
  }

  /**
   * Return a comparator to compare {@link EntityInVersionStructure} elements by <b>version</b>.
   */
  public static <T extends EntityInVersionStructure> Comparator<T> comparingVersion() {
    return comparingInt(NetexVersionHelper::versionOf);
  }

  /**
   * Find the first valid datetime in the given {@code periods} after or equals to the given {@code
   * timestamp}. For example, this method can be used to find the fist point in time where the given
   * periods are defined after current time - {@code timestamp=now()}.
   */
  public static LocalDateTime firstValidDateTime(
    List<ValidBetween> periods,
    LocalDateTime timestamp
  ) {
    // If not period is defined all times are valid
    if (periods.isEmpty()) {
      return timestamp;
    }

    LocalDateTime firstTime = null;

    for (ValidBetween p : periods) {
      // Validity period is in the past (compared with timestamp)
      if (p.getToDate() != null && p.getToDate().isBefore(timestamp)) {
        continue;
      }

      // Validity period is valid at the timestamp
      if (p.getFromDate() == null || p.getFromDate().isBefore(timestamp)) {
        return timestamp;
      }

      // Validity period is in the future
      if (firstTime == null || p.getFromDate().isBefore(firstTime)) {
        firstTime = p.getFromDate();
      }
    }
    return firstTime;
  }
}
