package eva.facade;

public record OrderView(long orderId, String pilotName, String chassisId, String shaftId, ChainStatus chain,
                        String pilot, String power, String catapult, String failureReason, String trail) {

    public String summary() {
        return "#" + orderId + " " + pilotName + "/" + chassisId + "/" + shaftId + " chain=" + chain
                + " pilot=" + pilot + " power=" + power + " catapult=" + catapult
                + (failureReason == null || failureReason.isEmpty() ? "" : " reason=\"" + failureReason + "\"")
                + (trail == null || trail.isEmpty() ? "" : " trail=[" + trail + "]");
    }
}
