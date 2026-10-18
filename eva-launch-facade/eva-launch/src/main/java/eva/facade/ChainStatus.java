package eva.facade;

public enum ChainStatus {
    STARTED,
    PENDING_B,
    PENDING_C,
    COMPLETED,
    COMPENSATING,
    COMPENSATED,
    REJECTED;

    public boolean isTerminal() {
        return this == COMPLETED || this == COMPENSATED || this == REJECTED;
    }
}
