package eva.pilot;

public enum PilotSyncStatus {
    STANDBY,
    SYNC_LOCKED,
    EJECTED;

    public boolean canMoveTo(PilotSyncStatus next) {
        switch (this) {
            case STANDBY:
                return next == SYNC_LOCKED;
            case SYNC_LOCKED:
                return next == STANDBY || next == EJECTED;
            default:
                return false;
        }
    }
}
