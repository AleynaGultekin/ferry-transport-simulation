import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.Random;

public final class SimulationConfig {
    public final int cars;
    public final int minibuses;
    public final int trucks;
    public final int ferryCapacity;
    public final int tollBoothsPerSide;
    public final long maxDockWaitMs;
    public final long fairnessWaitMs;
    public final int maxConsecutiveSameSideDepartures;
    public final int imbalanceThreshold;
    public final long tollDelayMinMs;
    public final long tollDelayMaxMs;
    public final long boardingDelayMinMs;
    public final long boardingDelayMaxMs;
    public final long unloadingDelayMinMs;
    public final long unloadingDelayMaxMs;
    public final long travelDelayMinMs;
    public final long travelDelayMaxMs;
    public final long returnDelayMinMs;
    public final long returnDelayMaxMs;
    public final int carPriority;
    public final int minibusPriority;
    public final int truckPriority;
    public final boolean visualizationEnabled;
    public final long randomSeed;
    public final Side startingSideOverride;

    private SimulationConfig(Properties p) {
        this.cars = getInt(p, "vehicles.cars", 12);
        this.minibuses = getInt(p, "vehicles.minibuses", 10);
        this.trucks = getInt(p, "vehicles.trucks", 8);
        this.ferryCapacity = getInt(p, "ferry.capacity", 20);
        this.tollBoothsPerSide = getInt(p, "toll.booths.per.side", 2);
        this.maxDockWaitMs = getLong(p, "ferry.max.wait.ms", 1200);
        this.fairnessWaitMs = getLong(p, "ferry.fairness.wait.ms", 2400);
        this.maxConsecutiveSameSideDepartures = getInt(p, "ferry.max.consecutive.same.side", 2);
        this.imbalanceThreshold = getInt(p, "ferry.imbalance.threshold", 3);
        this.tollDelayMinMs = getLong(p, "delay.toll.min.ms", 80);
        this.tollDelayMaxMs = getLong(p, "delay.toll.max.ms", 220);
        this.boardingDelayMinMs = getLong(p, "delay.boarding.min.ms", 70);
        this.boardingDelayMaxMs = getLong(p, "delay.boarding.max.ms", 180);
        this.unloadingDelayMinMs = getLong(p, "delay.unloading.min.ms", 50);
        this.unloadingDelayMaxMs = getLong(p, "delay.unloading.max.ms", 140);
        this.travelDelayMinMs = getLong(p, "delay.travel.min.ms", 250);
        this.travelDelayMaxMs = getLong(p, "delay.travel.max.ms", 550);
        this.returnDelayMinMs = getLong(p, "delay.return.min.ms", 300);
        this.returnDelayMaxMs = getLong(p, "delay.return.max.ms", 700);
        this.carPriority = getInt(p, "priority.car", 1);
        this.minibusPriority = getInt(p, "priority.minibus", 2);
        this.truckPriority = getInt(p, "priority.truck", 3);
        this.visualizationEnabled = getBoolean(p, "visualization.enabled", true);
        this.randomSeed = getLong(p, "random.seed", System.nanoTime());
        this.startingSideOverride = getSide(p.getProperty("ferry.start.side", "RANDOM").trim());
        validate();
    }

    public static SimulationConfig load(String path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = new FileInputStream(path)) {
            properties.load(input);
        } catch (IOException missingFile) {
            if (!Objects.equals(path, "simulation.properties")) {
                throw missingFile;
            }
            System.out.println("Configuration file not found, using built-in defaults.");
        }
        return new SimulationConfig(properties);
    }

 public int getPriority(VehicleType type) {
    switch (type) {
        case CAR:
            return carPriority;
        case MINIBUS:
            return minibusPriority;
        case TRUCK:
            return truckPriority;
        default:
            return 1;
    }
}

    public Side initialFerrySide(Random random) {
        return startingSideOverride != null ? startingSideOverride : (random.nextBoolean() ? Side.A : Side.B);
    }

    private void validate() {
        if (ferryCapacity < VehicleType.TRUCK.capacityUnits) {
            throw new IllegalArgumentException("Ferry capacity must be able to carry at least one truck.");
        }
        if (tollBoothsPerSide <= 0) {
            throw new IllegalArgumentException("Each side must have at least one toll booth.");
        }
        if (maxDockWaitMs <= 0 || fairnessWaitMs <= 0) {
            throw new IllegalArgumentException("Timing thresholds must be positive.");
        }
    }

    private static int getInt(Properties p, String key, int defaultValue) {
        return Integer.parseInt(p.getProperty(key, Integer.toString(defaultValue)).trim());
    }

    private static long getLong(Properties p, String key, long defaultValue) {
        return Long.parseLong(p.getProperty(key, Long.toString(defaultValue)).trim());
    }

    private static boolean getBoolean(Properties p, String key, boolean defaultValue) {
        return Boolean.parseBoolean(p.getProperty(key, Boolean.toString(defaultValue)).trim());
    }

    private static Side getSide(String value) {
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "A" -> Side.A;
            case "B" -> Side.B;
            case "RANDOM" -> null;
            default -> throw new IllegalArgumentException("Unsupported side value: " + value);
        };
    }
}
