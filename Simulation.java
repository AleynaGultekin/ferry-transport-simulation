import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public final class Simulation {
    private final SimulationConfig config;
    private final Random masterRandom;
    private final ReentrantLock lock;
    private final Condition ferryWakeUp;
    private final HarborSide sideA;
    private final HarborSide sideB;
    private final EventLogger logger;
    private final SimulationStats stats;
    private VisualizationFrame visualization;
    private boolean visualizationStarted;
    private final List<String> bufferedVisualizationLogs;
    private final AtomicInteger globalQueueSequence;
    private final AtomicInteger completedVehicles;
    private final List<Vehicle> vehicles;
    private final Ferry ferry;
    private final long startNano;
    private volatile long lastVisualizationUpdateMs;

    public Simulation(SimulationConfig config) {
        this.config = config;
        this.masterRandom = new Random(config.randomSeed);
        this.lock = new ReentrantLock(true);
        this.ferryWakeUp = lock.newCondition();
        this.sideA = new HarborSide(Side.A, config.tollBoothsPerSide);
        this.sideB = new HarborSide(Side.B, config.tollBoothsPerSide);
        this.logger = new EventLogger(this);
        this.stats = new SimulationStats();
        this.visualization = null;
        this.visualizationStarted = false;
        this.bufferedVisualizationLogs = new ArrayList<>();
        this.globalQueueSequence = new AtomicInteger(0);
        this.completedVehicles = new AtomicInteger(0);
        this.startNano = System.nanoTime();
        this.lastVisualizationUpdateMs = 0;
        this.vehicles = buildVehicles();
        this.ferry = new Ferry(this, config.initialFerrySide(masterRandom));
    }

    public void start() throws InterruptedException {
        logger.log("Simulation started with seed = " + config.randomSeed);
        logger.log(String.format(
            "Configuration: Cars=%d, Minibuses=%d, Trucks=%d, FerryCapacity=%d, TollBoothsPerSide=%d",
            config.cars, config.minibuses, config.trucks, config.ferryCapacity, config.tollBoothsPerSide
        ));
        logger.log(
            "Bonus features enabled: priority-based vehicle types, configurable parameters via input file, and real-time visualization."
        );
        ferry.start();
        for (Vehicle vehicle : vehicles) {
            vehicle.start();
        }
        for (Vehicle vehicle : vehicles) {
            vehicle.join();
        }
        ferry.join();

        pushVisualizationSnapshot();
        stats.printSummary(elapsedMillis(), vehicles.size(), completedVehicles.get(), config.ferryCapacity);
    }

    public SimulationConfig config() {
        return config;
    }

    public ReentrantLock lock() {
        return lock;
    }

    public Condition ferryWakeUp() {
        return ferryWakeUp;
    }

    public HarborSide harbor(Side side) {
        return side == Side.A ? sideA : sideB;
    }

    public EventLogger logger() {
        return logger;
    }

    public SimulationStats stats() {
        return stats;
    }

    public long elapsedMillis() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano);
    }

    public int nextQueueSequence() {
        return globalQueueSequence.incrementAndGet();
    }

    public void incrementCompletedVehicles() {
        completedVehicles.incrementAndGet();
        pushVisualizationSnapshot();
    }

    public boolean allVehiclesDone() {
        return completedVehicles.get() == vehicles.size();
    }

    public boolean noVehicleWaiting() {
        return sideA.queue.isEmpty() && sideB.queue.isEmpty();
    }

    public long randomBetween(long min, long max) {
        if (max <= min) {
            return min;
        }
        return min + (long) (masterRandom.nextDouble() * (max - min + 1));
    }

    public void pushLogToVisualization(String line) {
        if (!config.visualizationEnabled) {
            return;
        }
        if (!visualizationStarted) {
            bufferedVisualizationLogs.add(line);
            return;
        }
        if (visualization != null) {
            visualization.appendEvent(line);
            pushVisualizationSnapshot();
        }
    }

    public void pushVisualizationSnapshot() {
        if (!config.visualizationEnabled) {
            return;
        }

        long now = elapsedMillis();
        if (now - lastVisualizationUpdateMs < 100) {
            return;
        }
        lastVisualizationUpdateMs = now;

        VisualizationSnapshot snapshot;
        lock.lock();
        try {
            snapshot = new VisualizationSnapshot(
                elapsedMillis(),
                ferry.currentSide(),
                ferry.currentLoad(),
                config.ferryCapacity,
                ferry.onboardVehicleCount(),
                sideDemand(ferry.currentSide()),
                sideA.queue.size(),
                sideB.queue.size(),
                sideDemand(Side.A),
                sideDemand(Side.B),
                vehiclesByStage(Side.A, VehicleStage.WAITING_FOR_TOLL),
                vehiclesByStage(Side.A, VehicleStage.IN_TOLL),
                queuePreview(sideA.queue),
                vehiclesByStage(Side.B, VehicleStage.WAITING_FOR_TOLL),
                vehiclesByStage(Side.B, VehicleStage.IN_TOLL),
                queuePreview(sideB.queue),
                ferry.onboardVehicleNames(),
                completedVehicles.get(),
                vehicles.size(),
                stats.totalTrips(),
                vehicles.size() - completedVehicles.get(),
                logger.eventCount()
            );
        } finally {
            lock.unlock();
        }

        if (!visualizationStarted) {
            if (snapshot.onboardVehicleCount() == 0) {
                return;
            }
            visualization = VisualizationFrame.createIfAvailable();
            visualizationStarted = visualization != null;
            if (visualizationStarted) {
                for (String line : bufferedVisualizationLogs) {
                    visualization.appendEvent(line);
                }
                bufferedVisualizationLogs.clear();
            }
        }

        if (visualization != null) {
            visualization.updateSnapshot(snapshot);
        }
    }

    private List<String> queuePreview(Iterable<Vehicle> queue) {
        List<String> list = new ArrayList<>();
        for (Vehicle vehicle : queue) {
            list.add("#" + vehicle.queueSequence + " " + vehicle.getName()
                + " [P" + vehicle.priority + ", U" + vehicle.type.capacityUnits + "]");
        }
        return list;
    }

    private List<String> vehiclesByStage(Side side, VehicleStage stage) {
        List<String> list = new ArrayList<>();
        for (Vehicle vehicle : vehicles) {
            if (vehicle.stage == stage && vehicle.stageSide == side) {
                list.add(vehicle.getName() + " [P" + vehicle.priority + ", U" + vehicle.type.capacityUnits + "]");
            }
        }
        return list;
    }

    private int sideDemand(Side side) {
        int total = 0;
        for (Vehicle vehicle : vehicles) {
            if (vehicle.stageSide == side && (
                vehicle.stage == VehicleStage.WAITING_FOR_TOLL
                    || vehicle.stage == VehicleStage.IN_TOLL
                    || vehicle.stage == VehicleStage.IN_QUEUE
                    || vehicle.stage == VehicleStage.AT_DESTINATION
            )) {
                total++;
            }
        }
        return total;
    }

    private List<Vehicle> buildVehicles() {
        List<Vehicle> list = new ArrayList<>();
        int id = 1;
        id = appendVehicles(list, VehicleType.CAR, config.cars, id);
        id = appendVehicles(list, VehicleType.MINIBUS, config.minibuses, id);
        appendVehicles(list, VehicleType.TRUCK, config.trucks, id);
        Collections.shuffle(list, masterRandom);
        return list;
    }

    private int appendVehicles(List<Vehicle> list, VehicleType type, int count, int startId) {
        int nextId = startId;
        for (int i = 0; i < count; i++) {
            long seed = masterRandom.nextLong();
            Side initialSide = masterRandom.nextBoolean() ? Side.A : Side.B;
            list.add(new Vehicle(this, nextId, type, initialSide, seed));
            nextId++;
        }
        return nextId;
    }
}
