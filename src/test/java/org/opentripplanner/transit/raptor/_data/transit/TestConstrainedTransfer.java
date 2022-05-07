package org.opentripplanner.transit.raptor._data.transit;

import javax.annotation.Nullable;
import org.opentripplanner.model.transfer.TransferConstraint;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.constrainedtransfer.ConstrainedTransferBoarding;
import org.opentripplanner.transit.model.basic.ToStringBuilder;
import org.opentripplanner.transit.raptor.api.transit.RaptorConstrainedTransfer;
import org.opentripplanner.transit.raptor.api.transit.RaptorTransferConstraint;
import org.opentripplanner.transit.raptor.api.transit.RaptorTripScheduleBoardOrAlightEvent;

class TestConstrainedTransfer implements RaptorConstrainedTransfer {

  private final TransferConstraint transferConstraints;
  private final TestTripSchedule sourceTrip;
  private final int sourceStopPos;
  private final TestTripSchedule targetTrip;
  private final int targetTripIndex;
  private final int targetStopPos;
  private final int targetTime;

  TestConstrainedTransfer(
    TransferConstraint transferConstraints,
    TestTripSchedule sourceTrip,
    int sourceStopPos,
    TestTripSchedule targetTrip,
    int targetTripIndex,
    int targetStopPos,
    int targetTime
  ) {
    this.transferConstraints = transferConstraints;
    this.sourceTrip = sourceTrip;
    this.sourceStopPos = sourceStopPos;
    this.targetTrip = targetTrip;
    this.targetTripIndex = targetTripIndex;
    this.targetStopPos = targetStopPos;
    this.targetTime = targetTime;
  }

  public TestTripSchedule getTrip() {
    return targetTrip;
  }

  public int getStopPositionInPattern() {
    return targetStopPos;
  }

  public int getTime() {
    return targetTime;
  }

  public boolean isFacilitated() {
    return transferConstraints.isFacilitated();
  }

  @Nullable
  @Override
  public RaptorTransferConstraint getTransferConstraint() {
    return transferConstraints;
  }

  @Override
  public String toString() {
    return ToStringBuilder
      .of(TestConstrainedTransfer.class)
      .addObj("sourceTrip", sourceTrip)
      .addNum("sourceStopPos", sourceStopPos)
      .addObj("targetTrip", targetTrip)
      .addNum("targetTripIndex", targetTripIndex)
      .addNum("targetStopPos", targetStopPos)
      .addServiceTime("targetTime", targetTime)
      .toString();
  }

  TestTripSchedule getSourceTrip() {
    return sourceTrip;
  }

  int getSourceStopPos() {
    return sourceStopPos;
  }

  RaptorTripScheduleBoardOrAlightEvent<TestTripSchedule> boardingEvent(int earliestBoardingTime) {
    return new ConstrainedTransferBoarding<>(
      transferConstraints,
      targetTripIndex,
      targetTrip,
      targetStopPos,
      targetTime,
      earliestBoardingTime
    );
  }
}
