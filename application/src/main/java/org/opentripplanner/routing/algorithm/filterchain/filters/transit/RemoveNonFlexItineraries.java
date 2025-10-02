package org.opentripplanner.routing.algorithm.filterchain.filters.transit;

import java.util.function.Predicate;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.model.plan.Leg;
import org.opentripplanner.routing.algorithm.filterchain.framework.spi.RemoveItineraryFlagger;

/**
 * Remove itineraries that do not contain any flexible leg.
 */
public class RemoveNonFlexItineraries implements RemoveItineraryFlagger {

  @Override
  public String name() {
    return "remove-non-flex-itineraries";
  }

  @Override
  public Predicate<Itinerary> shouldBeFlaggedForRemoval() {
    return itinerary -> itinerary.legs().stream().noneMatch(Leg::isFlexibleTrip);
  }
}
