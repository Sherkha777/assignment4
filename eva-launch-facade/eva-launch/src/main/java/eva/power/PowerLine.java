package eva.power;

public class PowerLine {

    private long id;
    private final String chassisId;
    private final double megawatts;
    private PowerStatus status;
    private String note;

    public PowerLine(long id, String chassisId, double megawatts, PowerStatus status, String note) {
        this.id = id;
        this.chassisId = chassisId;
        this.megawatts = megawatts;
        this.status = status;
        this.note = note;
    }

    public void transitionTo(PowerStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("power line " + id + " cannot go " + status + " -> " + next);
        }
        status = next;
    }

    public void annotate(String note) {
        this.note = note;
    }

    public long id() {
        return id;
    }

    void assignId(long id) {
        this.id = id;
    }

    public String chassisId() {
        return chassisId;
    }

    public double megawatts() {
        return megawatts;
    }

    public PowerStatus status() {
        return status;
    }

    public String note() {
        return note;
    }
}
