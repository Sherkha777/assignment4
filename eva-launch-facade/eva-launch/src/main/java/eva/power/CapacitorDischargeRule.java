package eva.power;

public class CapacitorDischargeRule {

    public static final double SAFETY_MARGIN = 1.1;

    public record Verdict(boolean accepted, String reason) {
    }

    public Verdict evaluate(double requiredMegawatts, double availableMegawatts) {
        if (requiredMegawatts <= 0) {
            throw new IllegalArgumentException("required power must be positive");
        }
        double needed = requiredMegawatts * SAFETY_MARGIN;
        if (availableMegawatts < needed) {
            return new Verdict(false, "capacitor trip: " + availableMegawatts + " MW available, "
                    + needed + " MW needed");
        }
        return new Verdict(true, "stable at " + availableMegawatts + " MW");
    }
}
