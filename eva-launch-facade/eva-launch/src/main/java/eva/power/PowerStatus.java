package eva.power;

public enum PowerStatus {
    DISCONNECTED,
    CHARGED,
    DRAINED;

    public boolean canMoveTo(PowerStatus next) {
        switch (this) {
            case DISCONNECTED:
                return next == CHARGED;
            case CHARGED:
                return next == DRAINED;
            default:
                return false;
        }
    }
}
