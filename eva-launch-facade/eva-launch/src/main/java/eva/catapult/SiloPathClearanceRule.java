package eva.catapult;

public class SiloPathClearanceRule {

    public record Clearance(boolean cleared, String reason) {
    }

    public Clearance evaluate(ShaftCondition condition) {
        if (!condition.doorOpen()) {
            return new Clearance(false, "surface blast door failed to open");
        }
        if (condition.debris()) {
            return new Clearance(false, "shaft blocked by debris");
        }
        return new Clearance(true, "shaft clear");
    }
}
