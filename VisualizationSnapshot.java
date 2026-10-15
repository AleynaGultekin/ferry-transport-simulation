import java.util.List;

public record VisualizationSnapshot(
    long timeMs,
    Side ferrySide,
    int currentLoad,
    int ferryCapacity,
    int onboardVehicleCount,
    int demandOnCurrentSide,
    int sideAQueueSize,
    int sideBQueueSize,
    int sideATotalDemand,
    int sideBTotalDemand,
    List<String> sideAWaitingForToll,
    List<String> sideAInToll,
    List<String> sideAQueuePreview,
    List<String> sideBWaitingForToll,
    List<String> sideBInToll,
    List<String> sideBQueuePreview,
    List<String> onboardVehicles,
    int completedVehicles,
    int totalVehicles,
    long totalTrips,
    int activeVehicles,
    long eventCount
) {
}
