package eva.catapult;

public enum CatapultStatus {
    RESTING,
    PRESSURIZED,
    LAUNCHED;

    public boolean canMoveTo(CatapultStatus next) {
        switch (this) {
            case RESTING:
                return next == PRESSURIZED;
            case PRESSURIZED:
                return next == LAUNCHED || next == RESTING;
            default:
                return false;
        }
    }
}
