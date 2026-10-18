package eva.power;

public class PowerFailureException extends RuntimeException {

    private final long lineId;

    public PowerFailureException(String message, long lineId) {
        super(message);
        this.lineId = lineId;
    }

    public long lineId() {
        return lineId;
    }
}
