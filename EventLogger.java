public final class EventLogger {
    private final Simulation simulation;
    private long eventCount;

    public EventLogger(Simulation simulation) {
        this.simulation = simulation;
    }

    public synchronized void log(String message) {
        long timeMs = simulation.elapsedMillis();
        eventCount++;
        String line = String.format("[Time %6d ms] %s", timeMs, message);
        System.out.println(line);
        simulation.pushLogToVisualization(line);
    }

    public synchronized long eventCount() {
        return eventCount;
    }
}
