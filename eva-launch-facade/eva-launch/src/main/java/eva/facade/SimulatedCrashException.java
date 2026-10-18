package eva.facade;

public class SimulatedCrashException extends RuntimeException {

    public SimulatedCrashException(CrashPoint point) {
        super("simulated process crash " + point);
    }
}
