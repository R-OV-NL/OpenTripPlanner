package org.opentripplanner.routing.algorithm.raptoradapter.transit;

import static java.util.stream.Collectors.toMap;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.opentripplanner.framework.application.OTPFeature;
import org.opentripplanner.raptor.api.model.RaptorTransfer;
import org.opentripplanner.street.search.request.StreetSearchRequest;

public class RaptorTransferIndex {

  private final List<RaptorTransfer>[] forwardTransfers;

  private final List<RaptorTransfer>[] reversedTransfers;

  public RaptorTransferIndex(
    List<List<RaptorTransfer>> forwardTransfers,
    List<List<RaptorTransfer>> reversedTransfers
  ) {
    // Create immutable copies of the lists for each stop to make them immutable and faster to iterate
    this.forwardTransfers = forwardTransfers.stream().map(List::copyOf).toArray(List[]::new);
    this.reversedTransfers = reversedTransfers.stream().map(List::copyOf).toArray(List[]::new);
  }

  public static RaptorTransferIndex create(
    List<List<Transfer>> transfersByStopIndex,
    StreetSearchRequest request
  ) {
    var forwardTransfers = new ArrayList<List<RaptorTransfer>>(transfersByStopIndex.size());
    var reversedTransfers = new ArrayList<List<RaptorTransfer>>(transfersByStopIndex.size());

    for (int i = 0; i < transfersByStopIndex.size(); i++) {
      forwardTransfers.add(new ArrayList<>());
      reversedTransfers.add(new ArrayList<>());
    }

    var stopIndices = IntStream.range(0, transfersByStopIndex.size());
    if (OTPFeature.ParallelRouting.isOn()) {
      stopIndices = stopIndices.parallel();
    }
    stopIndices.forEach(fromStop -> {
      // The transfers are filtered so that there is only one possible directional transfer
      // for a stop pair.
      var transfers = transfersByStopIndex
        .get(fromStop)
        .stream()
        .flatMap(s -> s.asRaptorTransfer(request).stream())
        .collect(
          toMap(RaptorTransfer::stop, Function.identity(), (a, b) -> a.c1() < b.c1() ? a : b)
        )
        .values();

      // forwardTransfers is not modified here, and no two threads will access the same element
      // in it, so this is still thread safe.
      forwardTransfers.get(fromStop).addAll(transfers);
    });

    for (int fromStop = 0; fromStop < transfersByStopIndex.size(); fromStop++) {
      for (var forwardTransfer : forwardTransfers.get(fromStop)) {
        reversedTransfers
          .get(forwardTransfer.stop())
          .add(DefaultRaptorTransfer.reverseOf(fromStop, forwardTransfer));
      }
    }
    return new RaptorTransferIndex(forwardTransfers, reversedTransfers);
  }

  public List<RaptorTransfer> getForwardTransfers(int stopIndex) {
    return forwardTransfers[stopIndex];
  }

  public List<RaptorTransfer> getReversedTransfers(int stopIndex) {
    return reversedTransfers[stopIndex];
  }
}
