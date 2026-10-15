public final class FerryAction {
    public enum Type {
        CROSS,
        TERMINATE
    }

    public final Type type;
    public final Side destination;
    public final int departureLoad;

    private FerryAction(Type type, Side destination, int departureLoad) {
        this.type = type;
        this.destination = destination;
        this.departureLoad = departureLoad;
    }

    public static FerryAction terminate() {
        return new FerryAction(Type.TERMINATE, null, 0);
    }

    public static FerryAction cross(Side destination, int departureLoad) {
        return new FerryAction(Type.CROSS, destination, departureLoad);
    }
}
