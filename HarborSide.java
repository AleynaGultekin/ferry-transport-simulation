import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Semaphore;

public final class HarborSide {
    public final Side side;
    public final Semaphore tolls;
    public final Deque<Vehicle> queue;

    public HarborSide(Side side, int booths) {
        this.side = side;
        this.tolls = new Semaphore(booths, true);
        this.queue = new ArrayDeque<>();
    }
}
