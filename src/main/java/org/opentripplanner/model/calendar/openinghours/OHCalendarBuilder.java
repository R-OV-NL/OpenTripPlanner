package org.opentripplanner.model.calendar.openinghours;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.opentripplanner.routing.trippattern.Deduplicator;

public class OHCalendarBuilder {

  private final Deduplicator deduplicator;
  private final LocalDate startOfPeriod;
  private final LocalDate endOfPeriod;
  private final int daysInPeriod;
  private final ZoneId zoneId;
  private final List<OpeningHours> openingHours = new ArrayList<>();

  public OHCalendarBuilder(
    Deduplicator deduplicator,
    LocalDate startOfPeriod,
    int daysInPeriod,
    ZoneId zoneId
  ) {
    this.deduplicator = deduplicator;
    this.startOfPeriod = startOfPeriod;
    this.endOfPeriod = startOfPeriod.plusDays(daysInPeriod);
    this.daysInPeriod = daysInPeriod;
    this.zoneId = zoneId;
  }

  public OpeningHoursBuilder openingHours(
    String periodDescription,
    LocalTime startTime,
    LocalTime endTime
  ) {
    return new OpeningHoursBuilder(periodDescription, startTime, endTime, false);
  }

  public OpeningHoursBuilder openingHours(
    String periodDescription,
    LocalTime startTime,
    LocalTime endTime,
    boolean isAfterMidnight
  ) {
    return new OpeningHoursBuilder(periodDescription, startTime, endTime, isAfterMidnight);
  }

  public OHCalendar build() {
    // We sort the opening hours for the deduplicator to work a little better and to simplify
    // the check can Enter/Exit later. Even if the opening hours are not on the same dates they
    // will still be sorted in the right order after day filtering
    Collections.sort(openingHours);
    return new OHCalendar(
      startOfPeriod,
      endOfPeriod,
      zoneId,
      deduplicator.deduplicateImmutableList(OpeningHours.class, openingHours)
    );
  }

  public class OpeningHoursBuilder {

    private String periodDescription;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private boolean isAfterMidnight;

    private final BitSet openingDays = new BitSet(daysInPeriod);

    public OpeningHoursBuilder(
      String periodDescription,
      LocalTime startTime,
      LocalTime endTime,
      boolean isAfterMidnight
    ) {
      this.periodDescription = periodDescription;
      this.startTime = startTime;
      this.endTime = endTime;
      this.isAfterMidnight = isAfterMidnight;
    }

    public BitSet getOpeningDays() {
      return openingDays;
    }

    public String getPeriodDescription() {
      return periodDescription;
    }

    public LocalTime getStartTime() {
      return startTime;
    }

    public LocalTime getEndTime() {
      return endTime;
    }

    public BitSet getCommonDays(BitSet days) {
      var openingDaysClone = (BitSet) openingDays.clone();
      openingDaysClone.and(days);
      return openingDaysClone;
    }

    private void appendDescription(String descriptionAddition) {
      periodDescription = periodDescription + descriptionAddition;
    }

    public boolean isEverOn() {
      return !openingDays.isEmpty();
    }

    public OpeningHoursBuilder on(LocalDate date) {
      var shiftedDate = date.plusDays(isAfterMidnight ? 1 : 0);
      if (shiftedDate.isBefore(startOfPeriod) || shiftedDate.isAfter(endOfPeriod)) {
        return this;
      }
      openingDays.set((int) ChronoUnit.DAYS.between(startOfPeriod, shiftedDate));
      return this;
    }

    public OpeningHoursBuilder on(DayOfWeek dayOfWeek) {
      var shiftedDayOfWeek = dayOfWeek.plus(isAfterMidnight ? 1 : 0);
      // This counts how many days there are in between the startOfPeriod and
      // when the specified dayOfWeek occurs for the first time. Maybe there is a cleaner way to do this.
      int rawWeekDayDifference = shiftedDayOfWeek.compareTo(startOfPeriod.getDayOfWeek());
      int firstOccurrenceDaysFromStart = rawWeekDayDifference >= 0
        ? rawWeekDayDifference
        : 7 - Math.abs(rawWeekDayDifference);

      for (int i = firstOccurrenceDaysFromStart; i < daysInPeriod; i += 7) {
        openingDays.set(i);
      }
      return this;
    }

    public OpeningHoursBuilder on(DayOfWeek fromDayOfWeek, DayOfWeek untilDayOfWeek) {
      if (fromDayOfWeek == null) {
        return this;
      }
      if (untilDayOfWeek == null) {
        on(fromDayOfWeek);
        return this;
      }

      int untilAdjusted = fromDayOfWeek.getValue() > untilDayOfWeek.getValue()
        ? untilDayOfWeek.getValue() + 7
        : untilDayOfWeek.getValue();
      for (int i = fromDayOfWeek.getValue(); i <= untilAdjusted; i++) {
        int dayValue = i > 7 ? i - 7 : i;
        on(DayOfWeek.of(dayValue));
      }
      return this;
    }

    public OpeningHoursBuilder on(
      Month fromMonth,
      Month untilMonth,
      DayOfWeek fromDayOfWeek,
      DayOfWeek untilDayOfWeek
    ) {
      if (fromMonth == null || fromDayOfWeek == null) {
        return this;
      }

      Set<Month> months = new HashSet<>();
      if (untilMonth == null) {
        months.add(fromMonth);
      } else {
        int untilMonthAdjusted = fromMonth.getValue() > untilMonth.getValue()
          ? untilMonth.getValue() + 12
          : untilMonth.getValue();
        for (int i = fromMonth.getValue(); i <= untilMonthAdjusted; i++) {
          int monthValue = i > 12 ? i - 12 : i;
          months.add(Month.of(monthValue));
        }
      }

      Set<DayOfWeek> daysOfWeek = new HashSet<>();
      if (untilDayOfWeek == null) {
        daysOfWeek.add(fromDayOfWeek);
      } else {
        int untilDayAdjusted = fromDayOfWeek.getValue() > untilDayOfWeek.getValue()
          ? untilDayOfWeek.getValue() + 7
          : untilDayOfWeek.getValue();
        for (int i = fromDayOfWeek.getValue(); i <= untilDayAdjusted; i++) {
          int dayValue = i > 7 ? i - 7 : i;
          daysOfWeek.add(DayOfWeek.of(dayValue));
        }
      }

      var dateToProcess = isAfterMidnight ? startOfPeriod.minusDays(1) : startOfPeriod;
      int i = 0;
      while (i < daysInPeriod) {
        if (months.contains(dateToProcess.getMonth())) {
          if (daysOfWeek.contains(dateToProcess.getDayOfWeek())) {
            openingDays.set(i);
          }
          dateToProcess = dateToProcess.plusDays(1);
          i += 1;
        } else {
          int daysToSkip =
            YearMonth.of(dateToProcess.getYear(), dateToProcess.getMonth()).lengthOfMonth() -
            dateToProcess.getDayOfMonth() +
            1;
          dateToProcess = dateToProcess.plusDays(daysToSkip);
          i += daysToSkip;
        }
      }
      return this;
    }

    public OpeningHoursBuilder on(BitSet days) {
      if (days.size() != openingDays.size()) {
        return this;
      }
      openingDays.or(days);
      return this;
    }

    public OpeningHoursBuilder everyDay() {
      openingDays.set(0, daysInPeriod);
      return this;
    }

    public OpeningHoursBuilder off(BitSet daysOff, String offDescription) {
      if (openingDays.intersects(daysOff)) {
        openingDays.andNot(daysOff);
        appendDescription(" except " + offDescription);
      }
      return this;
    }

    public OHCalendarBuilder add() {
      var days = deduplicator.deduplicateBitSet(openingDays);
      var hours = deduplicator.deduplicateObject(
        OpeningHours.class,
        new OpeningHours(periodDescription, startTime, endTime, days)
      );
      openingHours.add(hours);
      return OHCalendarBuilder.this;
    }
  }
}
