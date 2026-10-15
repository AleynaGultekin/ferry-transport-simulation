import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

public final class SimulationStats {
    private final AtomicLong totalVehicleWaitTimeMs = new AtomicLong(0);
    private final AtomicLong maxVehicleWaitTimeMs = new AtomicLong(0);
    private final AtomicLong recordedVehicleCount = new AtomicLong(0);
    private final AtomicLong totalTrips = new AtomicLong(0);
    private final AtomicLong totalLoadAcrossTrips = new AtomicLong(0);

    public void recordCompletedVehicleWait(long totalWaitMs) {
        totalVehicleWaitTimeMs.addAndGet(totalWaitMs);
        maxVehicleWaitTimeMs.accumulateAndGet(totalWaitMs, Math::max);
        recordedVehicleCount.incrementAndGet();
    }

    public void recordTrip(int departureLoad) {
        totalTrips.incrementAndGet();
        totalLoadAcrossTrips.addAndGet(departureLoad);
    }

    public long totalTrips() {
        return totalTrips.get();
    }

    public void printSummary(long totalSimulationTimeMs, int totalVehicleCount, int completedVehicles, int ferryCapacity) {
        long divisor = recordedVehicleCount.get() == 0 ? totalVehicleCount : recordedVehicleCount.get();
        double averageWaitMs = divisor == 0 ? 0.0 : totalVehicleWaitTimeMs.get() / (double) divisor;
        double utilization = totalTrips.get() == 0
            ? 0.0
            : totalLoadAcrossTrips.get() / (double) (ferryCapacity * totalTrips.get());

        System.out.println();
        System.out.println("========== FINAL STATISTICS ==========");
        System.out.printf(Locale.US, "Total simulation time        : %d ms%n", totalSimulationTimeMs);
        System.out.printf(Locale.US, "Average waiting time/vehicle : %.2f ms%n", averageWaitMs);
        System.out.printf(Locale.US, "Maximum waiting time         : %d ms%n", maxVehicleWaitTimeMs.get());
        System.out.printf(Locale.US, "Number of ferry trips        : %d%n", totalTrips.get());
        System.out.printf(Locale.US, "Ferry utilization ratio      : %.2f%%%n", utilization * 100.0);
        System.out.printf(Locale.US, "Completed vehicles           : %d/%d%n", completedVehicles, totalVehicleCount);
        System.out.println("======================================");
    }
}
