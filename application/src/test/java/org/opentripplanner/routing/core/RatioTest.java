package org.opentripplanner.routing.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model.basic.Ratio;

public class RatioTest {
  private static final Double HALF = 0.5d;
  private static final Double ZERO = 0d;
  private static final Double ONE = 1d;
  private static final Double TOO_HIGH = 1.1d;
  private static final Double TOO_LOW = -1.1d;

  @Test
  void validRatios() {
    assertDoesNotThrow(() -> new Ratio(HALF));
    assertDoesNotThrow(() -> new Ratio(ZERO));
    assertDoesNotThrow(() -> new Ratio(ONE));
  }

  @Test
  void invalidRatios() {
    assertThrows(IllegalArgumentException.class, () -> new Ratio(TOO_HIGH));
    assertThrows(IllegalArgumentException.class, () -> new Ratio(TOO_LOW));
  }

  @Test
  void testHashCode() {
    Ratio half = new Ratio(HALF);

    Ratio half2 = new Ratio(HALF);
    assertEquals(half.hashCode(), half2.hashCode());

    Double halfDouble = 2d;
    assertNotEquals(half.hashCode(), halfDouble);
  }
}
