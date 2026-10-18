package eva.catapult;

public class CatapultRun {

    private long id;
    private final String shaftId;
    private final String chassisId;
    private CatapultStatus status;
    private String note;

    public CatapultRun(long id, String shaftId, String chassisId, CatapultStatus status, String note) {
        this.id = id;
        this.shaftId = shaftId;
        this.chassisId = chassisId;
        this.status = status;
        this.note = note;
    }

    public void transitionTo(CatapultStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("catapult run " + id + " cannot go " + status + " -> " + next);
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

    public String shaftId() {
        return shaftId;
    }

    public String chassisId() {
        return chassisId;
    }

    public CatapultStatus status() {
        return status;
    }

    public String note() {
        return note;
    }
}
