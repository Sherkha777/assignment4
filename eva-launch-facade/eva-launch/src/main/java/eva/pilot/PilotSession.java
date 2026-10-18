package eva.pilot;

public class PilotSession {

    private long id;
    private final String pilotName;
    private final String chassisId;
    private final double stress;
    private final double syncRatio;
    private PilotSyncStatus status;

    public PilotSession(long id, String pilotName, String chassisId, double stress, double syncRatio,
                        PilotSyncStatus status) {
        this.id = id;
        this.pilotName = pilotName;
        this.chassisId = chassisId;
        this.stress = stress;
        this.syncRatio = syncRatio;
        this.status = status;
    }

    public void transitionTo(PilotSyncStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("pilot session " + id + " cannot go " + status + " -> " + next);
        }
        status = next;
    }

    public long id() {
        return id;
    }

    void assignId(long id) {
        this.id = id;
    }

    public String pilotName() {
        return pilotName;
    }

    public String chassisId() {
        return chassisId;
    }

    public double stress() {
        return stress;
    }

    public double syncRatio() {
        return syncRatio;
    }

    public PilotSyncStatus status() {
        return status;
    }
}
