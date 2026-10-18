package eva.catapult;

public record ShaftCondition(boolean doorOpen, boolean debris) {

    public static ShaftCondition clear() {
        return new ShaftCondition(true, false);
    }
}
