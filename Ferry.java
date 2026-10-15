import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class Ferry extends Thread {
    private final Simulation simulation;
    private final List<Vehicle> onboard = new ArrayList<>();
    private Side side;
    private int currentLoad;
    private int consecutiveDeparturesFromSameSide;
    private Side lastDepartureSide;
    private boolean lastTripWasEmpty;

    public Ferry(Simulation simulation, Side startingSide) {
        super("Ferry");
        this.simulation = simulation;
        this.side = startingSide;
    }

    @Override
    public void run() {
        simulation.logger().log("Ferry started on Side " + side);
        try {
            while (true) {
                unloadAll();
                FerryAction action = loadOrDecideNextMove();
                if (action.type == FerryAction.Type.TERMINATE) {
                    simulation.logger().log("Ferry thread detected clean termination.");
                    return;
                }
                cross(action);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            simulation.logger().log("Ferry thread interrupted.");
        }
    }

    private void unloadAll() throws InterruptedException {
        while (true) {
            Vehicle vehicleToUnload;
            simulation.lock().lock();
            try {
                if (onboard.isEmpty()) {
                    return;
                }
                vehicleToUnload = onboard.remove(0);
            } finally {
                simulation.lock().unlock();
            }

            Thread.sleep(simulation.randomBetween(
                simulation.config().unloadingDelayMinMs,
                simulation.config().unloadingDelayMaxMs
            ));

            simulation.lock().lock();
            try {
                currentLoad -= vehicleToUnload.type.capacityUnits;
                vehicleToUnload.markArrived();
                simulation.logger().log(String.format(
                    "%s unloaded on Side %s (remaining load=%d)",
                    vehicleToUnload.getName(), side, currentLoad
                ));
            } finally {
                simulation.lock().unlock();
            }
        }
    }

    private FerryAction loadOrDecideNextMove() throws InterruptedException {
        long dockOpenedAt = simulation.elapsedMillis();

        while (true) {
            simulation.lock().lock();
            try {
                if (simulation.allVehiclesDone() && simulation.noVehicleWaiting() && onboard.isEmpty() && currentLoad == 0) {
                    return FerryAction.terminate();
                }

                if (currentLoad == 0 && shouldRepositionWithoutBoarding()) {
                    Side destination = side.opposite();
                    simulation.logger().log(String.format(
                        "Ferry repositions empty from Side %s to Side %s for fairness and priority balancing.",
                        side, destination
                    ));
                    return FerryAction.cross(destination, 0);
                }

                Vehicle head = simulation.harbor(side).queue.peekFirst();
                if (head != null && head.type.capacityUnits <= remainingCapacity()) {
                    simulation.harbor(side).queue.removeFirst();
                    long waited = Math.max(0L, simulation.elapsedMillis() - head.queueEnteredAtMs);
                    head.accumulatedWaitMs += waited;

                    simulation.lock().unlock();
                    Thread.sleep(simulation.randomBetween(
                        simulation.config().boardingDelayMinMs,
                        simulation.config().boardingDelayMaxMs
                    ));
                    simulation.lock().lock();

                    onboard.add(head);
                    currentLoad += head.type.capacityUnits;
                    head.markBoarded();
                    simulation.logger().log(String.format(
                        "Ferry loaded %s on Side %s (type=%s, priority=%d, current load=%d/%d)",
                        head.getName(), side, head.type.displayName, head.priority, currentLoad, simulation.config().ferryCapacity
                    ));
                    continue;
                }

                boolean full = currentLoad == simulation.config().ferryCapacity;
                boolean blockedByCapacity = head != null && head.type.capacityUnits > remainingCapacity();
                boolean timeoutReached = simulation.elapsedMillis() - dockOpenedAt >= simulation.config().maxDockWaitMs;
                boolean nobodyWaitingHere = head == null;
                boolean someoneWaitingElsewhere = !simulation.harbor(side.opposite()).queue.isEmpty();

                if (currentLoad > 0 && (full || blockedByCapacity || timeoutReached || (nobodyWaitingHere && someoneWaitingElsewhere))) {
                    simulation.logger().log(String.format(
                        "Ferry departs from Side %s with load %d/%d (reason: %s)",
                        side,
                        currentLoad,
                        simulation.config().ferryCapacity,
                        departureReason(full, blockedByCapacity, timeoutReached, nobodyWaitingHere, someoneWaitingElsewhere)
                    ));
                    return FerryAction.cross(side.opposite(), currentLoad);
                }

                if (currentLoad == 0) {
                    if (head == null && someoneWaitingElsewhere) {
                        simulation.logger().log(String.format(
                            "Ferry leaves Side %s empty because the opposite side has waiting vehicles.",
                            side
                        ));
                        return FerryAction.cross(side.opposite(), 0);
                    }
                    if (head == null && simulation.allVehiclesDone() && simulation.noVehicleWaiting()) {
                        return FerryAction.terminate();
                    }
                }

                long waited = simulation.elapsedMillis() - dockOpenedAt;
                long remainingWait = Math.max(1L, simulation.config().maxDockWaitMs - waited);
                simulation.ferryWakeUp().await(remainingWait, TimeUnit.MILLISECONDS);
            } finally {
                if (simulation.lock().isHeldByCurrentThread()) {
                    simulation.lock().unlock();
                }
            }
        }
    }

    private void cross(FerryAction action) throws InterruptedException {
        Thread.sleep(simulation.randomBetween(
            simulation.config().travelDelayMinMs,
            simulation.config().travelDelayMaxMs
        ));

        simulation.lock().lock();
        try {
            simulation.stats().recordTrip(action.departureLoad);
            if (lastDepartureSide == side) {
                consecutiveDeparturesFromSameSide++;
            } else {
                consecutiveDeparturesFromSameSide = 1;
            }
            lastDepartureSide = side;
            lastTripWasEmpty = action.departureLoad == 0;
            side = action.destination;
            simulation.logger().log("Ferry arrived on Side " + side + " after crossing.");
            simulation.ferryWakeUp().signalAll();
        } finally {
            simulation.lock().unlock();
        }
    }

    private int remainingCapacity() {
        return simulation.config().ferryCapacity - currentLoad;
    }

    private boolean shouldRepositionWithoutBoarding() {
        HarborSide currentHarbor = simulation.harbor(side);
        HarborSide oppositeHarbor = simulation.harbor(side.opposite());
        if (oppositeHarbor.queue.isEmpty()) {
            return false;
        }
        if (currentHarbor.queue.isEmpty()) {
            return true;
        }
        if (lastTripWasEmpty) {
            return false;
        }

        long currentOldestWait = oldestWait(currentHarbor);
        long oppositeOldestWait = oldestWait(oppositeHarbor);
        int currentScore = sidePriorityScore(currentHarbor, currentOldestWait);
        int oppositeScore = sidePriorityScore(oppositeHarbor, oppositeOldestWait);
        boolean fairnessThresholdReached = oppositeOldestWait >= simulation.config().fairnessWaitMs;
        boolean tooManyConsecutiveTrips = consecutiveDeparturesFromSameSide >= simulation.config().maxConsecutiveSameSideDepartures;
        boolean oppositeIsHeavilyLoaded =
            oppositeHarbor.queue.size() - currentHarbor.queue.size() >= simulation.config().imbalanceThreshold;

        return (fairnessThresholdReached || tooManyConsecutiveTrips || oppositeIsHeavilyLoaded)
            && oppositeScore > currentScore + 15;
    }

    private long oldestWait(HarborSide harbor) {
        Vehicle head = harbor.queue.peekFirst();
        if (head == null) {
            return 0L;
        }
        return Math.max(0L, simulation.elapsedMillis() - head.queueEnteredAtMs);
    }

    private int sidePriorityScore(HarborSide harbor, long oldestWait) {
        Vehicle highestPriorityVehicle = harbor.queue.stream()
            .max(Comparator.comparingInt(v -> v.priority))
            .orElse(null);
        int highestPriority = highestPriorityVehicle == null ? 0 : highestPriorityVehicle.priority;
        return (highestPriority * 100) + (harbor.queue.size() * 10) + (int) Math.min(99L, oldestWait / 100L);
    }

    private String departureReason(
        boolean full,
        boolean blockedByCapacity,
        boolean timeoutReached,
        boolean nobodyWaitingHere,
        boolean someoneWaitingElsewhere
    ) {
        if (full) {
            return "ferry reached full capacity";
        }
        if (blockedByCapacity) {
            return "head of FIFO queue does not fit into remaining capacity";
        }
        if (timeoutReached) {
            return "maximum dock waiting threshold reached";
        }
        if (nobodyWaitingHere && someoneWaitingElsewhere) {
            return "opposite side has waiting vehicles";
        }
        return "policy trigger";
    }

    public Side currentSide() {
        return side;
    }

    public int currentLoad() {
        return currentLoad;
    }

    public List<String> onboardVehicleNames() {
        List<String> list = new ArrayList<>();
        for (Vehicle vehicle : onboard) {
            list.add(vehicle.getName() + " [" + vehicle.type.displayName + "]");
        }
        return list;
    }

    public int onboardVehicleCount() {
        return onboard.size();
    }
}
