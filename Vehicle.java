import java.util.Random;
import java.util.concurrent.locks.Condition;

public final class Vehicle extends Thread {
    public final int id;
    public final VehicleType type;
    public final int priority;
    public final Side homeSide;
    public final Random random;
    public final Condition stateChanged;

    private final Simulation simulation;

    public Side currentSide;
    public int completedLegs;
    public int boardedLegs;
    public int queueSequence;
    public long queueEnteredAtMs;
    public long accumulatedWaitMs;
    public VehicleStage stage;
    public Side stageSide;

    public Vehicle(Simulation simulation, int id, VehicleType type, Side homeSide, long seed) {
        super(type.displayName + "-" + id);
        this.simulation = simulation;
        this.id = id;
        this.type = type;
        this.priority = simulation.config().getPriority(type);
        this.homeSide = homeSide;
        this.currentSide = homeSide;
        this.random = new Random(seed);
        this.stateChanged = simulation.lock().newCondition();
        this.stage = VehicleStage.CREATED;
        this.stageSide = homeSide;
    }

    @Override
    public void run() {
        simulation.logger().log(String.format(
            "%s created on Side %s (capacity=%d, priority=%d)",
            getName(), currentSide, type.capacityUnits, priority
        ));
        try {
            performLeg(1);
            randomSleep(simulation.config().returnDelayMinMs, simulation.config().returnDelayMaxMs);
            simulation.logger().log(getName() + " is ready to return from Side " + currentSide);
            performLeg(2);

            simulation.lock().lock();
            try {
                simulation.stats().recordCompletedVehicleWait(accumulatedWaitMs);
                simulation.incrementCompletedVehicles();
                markCompleted();
                simulation.logger().log(getName() + " completed the full round trip and returned to Side " + currentSide);
                simulation.ferryWakeUp().signalAll();
            } finally {
                simulation.lock().unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            simulation.logger().log(getName() + " was interrupted.");
        }
    }

    private void performLeg(int legNumber) throws InterruptedException {
        updateStage(VehicleStage.WAITING_FOR_TOLL, currentSide);
        passToll(currentSide);
        enterQueue(currentSide);

        simulation.lock().lock();
        try {
            while (boardedLegs < legNumber) {
                stateChanged.await();
            }
            while (completedLegs < legNumber) {
                stateChanged.await();
            }
            currentSide = currentSide.opposite();
            if (legNumber == 1) {
                updateStage(VehicleStage.AT_DESTINATION, currentSide);
            }
        } finally {
            simulation.lock().unlock();
        }
    }

    private void passToll(Side side) throws InterruptedException {
        HarborSide harbor = simulation.harbor(side);
        harbor.tolls.acquire();
        try {
            updateStage(VehicleStage.IN_TOLL, side);
            simulation.logger().log(getName() + " entered toll on Side " + side);
            randomSleep(simulation.config().tollDelayMinMs, simulation.config().tollDelayMaxMs);
            simulation.logger().log(getName() + " exited toll on Side " + side);
        } finally {
            harbor.tolls.release();
        }
    }

    private void enterQueue(Side side) {
        simulation.lock().lock();
        try {
            queueSequence = simulation.nextQueueSequence();
            queueEnteredAtMs = simulation.elapsedMillis();
            simulation.harbor(side).queue.addLast(this);
            updateStage(VehicleStage.IN_QUEUE, side);
            simulation.logger().log(String.format(
                "%s joined FIFO queue on Side %s (sequence=%d, priority=%d)",
                getName(), side, queueSequence, priority
            ));
            simulation.ferryWakeUp().signalAll();
        } finally {
            simulation.lock().unlock();
        }
    }

    public void markBoarded() {
        boardedLegs++;
        updateStage(VehicleStage.ON_FERRY, null);
        stateChanged.signalAll();
    }

    public void markArrived() {
        completedLegs++;
        stateChanged.signalAll();
    }

    public void markCompleted() {
        updateStage(VehicleStage.COMPLETED, currentSide);
    }

    private void updateStage(VehicleStage nextStage, Side nextSide) {
        this.stage = nextStage;
        this.stageSide = nextSide;
        simulation.pushVisualizationSnapshot();
    }

    private void randomSleep(long min, long max) throws InterruptedException {
        long delay = min + (long) (random.nextDouble() * (max - min + 1));
        Thread.sleep(delay);
    }
}
