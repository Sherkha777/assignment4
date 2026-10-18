package eva.facade;

public class LaunchOrder {

    private long id;
    private final String pilotName;
    private final String chassisId;
    private final double stress;
    private final String shaftId;
    private ChainStatus status;
    private Long pilotSessionId;
    private Long powerLineId;
    private Long catapultRunId;
    private String failureReason;
    private String trail;

    public LaunchOrder(long id, String pilotName, String chassisId, double stress, String shaftId,
                       ChainStatus status, Long pilotSessionId, Long powerLineId, Long catapultRunId,
                       String failureReason, String trail) {
        this.id = id;
        this.pilotName = pilotName;
        this.chassisId = chassisId;
        this.stress = stress;
        this.shaftId = shaftId;
        this.status = status;
        this.pilotSessionId = pilotSessionId;
        this.powerLineId = powerLineId;
        this.catapultRunId = catapultRunId;
        this.failureReason = failureReason == null ? "" : failureReason;
        this.trail = trail == null ? "" : trail;
    }

    public void pilotLocked(long sessionId) {
        this.pilotSessionId = sessionId;
        this.status = ChainStatus.PENDING_B;
    }

    public void linkPilot(long sessionId) {
        this.pilotSessionId = sessionId;
    }

    public void powerCharged(long lineId) {
        this.powerLineId = lineId;
        this.status = ChainStatus.PENDING_C;
    }

    public void linkPower(long lineId) {
        this.powerLineId = lineId;
    }

    public void launched(long runId) {
        this.catapultRunId = runId;
        this.status = ChainStatus.COMPLETED;
    }

    public void linkCatapult(long runId) {
        this.catapultRunId = runId;
    }

    public void reject(String reason) {
        this.failureReason = reason;
        this.status = ChainStatus.REJECTED;
    }

    public void beginCompensation(String reason) {
        if (failureReason.isEmpty() && reason != null) {
            failureReason = reason;
        }
        status = ChainStatus.COMPENSATING;
    }

    public void compensated() {
        status = ChainStatus.COMPENSATED;
    }

    public void addTrail(String step) {
        trail = trail.isEmpty() ? step : trail + " > " + step;
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

    public String shaftId() {
        return shaftId;
    }

    public ChainStatus status() {
        return status;
    }

    public Long pilotSessionId() {
        return pilotSessionId;
    }

    public Long powerLineId() {
        return powerLineId;
    }

    public Long catapultRunId() {
        return catapultRunId;
    }

    public String failureReason() {
        return failureReason;
    }

    public String trail() {
        return trail;
    }
}
