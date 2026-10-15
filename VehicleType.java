public enum VehicleType {
    CAR("Car", "C", 1),
    MINIBUS("Minibus", "M", 2),
    TRUCK("Truck", "T", 3);

    public final String displayName;
    public final String shortName;
    public final int capacityUnits;

    VehicleType(String displayName, String shortName, int capacityUnits) {
        this.displayName = displayName;
        this.shortName = shortName;
        this.capacityUnits = capacityUnits;
    }
}
