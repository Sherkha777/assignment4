package eva.catapult;

public class CatapultFailureException extends RuntimeException {

    private final long runId;

    public CatapultFailureException(String message, long runId) {
        super(message);
        this.runId = runId;
    }

    public long runId() {
        return runId;
    }
}
