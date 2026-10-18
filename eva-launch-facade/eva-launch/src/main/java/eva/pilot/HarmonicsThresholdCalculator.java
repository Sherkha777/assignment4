package eva.pilot;

public class HarmonicsThresholdCalculator {

    public static final double MIN_SYNC_RATIO = 40.0;

    public double syncRatio(double stress) {
        if (stress < 0 || stress > 100) {
            throw new IllegalArgumentException("stress must be between 0 and 100");
        }
        return 100.0 - stress;
    }

    public boolean isAcceptable(double stress) {
        return syncRatio(stress) >= MIN_SYNC_RATIO;
    }
}
