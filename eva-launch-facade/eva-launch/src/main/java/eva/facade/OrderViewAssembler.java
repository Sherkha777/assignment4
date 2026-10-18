package eva.facade;

import eva.catapult.CatapultPneumatics;
import eva.catapult.CatapultRun;
import eva.pilot.EntryPlugLauncher;
import eva.pilot.PilotSession;
import eva.power.PowerGridRouter;
import eva.power.PowerLine;

class OrderViewAssembler {

    private final EntryPlugLauncher launcher;
    private final PowerGridRouter grid;
    private final CatapultPneumatics pneumatics;

    OrderViewAssembler(EntryPlugLauncher launcher, PowerGridRouter grid, CatapultPneumatics pneumatics) {
        this.launcher = launcher;
        this.grid = grid;
        this.pneumatics = pneumatics;
    }

    OrderView assemble(LaunchOrder order) {
        String pilot = order.pilotSessionId() == null ? "-"
                : launcher.find(order.pilotSessionId()).map(PilotSession::status).map(Enum::name).orElse("?");
        String power = order.powerLineId() == null ? "-"
                : grid.find(order.powerLineId()).map(PowerLine::status).map(Enum::name).orElse("?");
        String catapult = order.catapultRunId() == null ? "-"
                : pneumatics.find(order.catapultRunId()).map(CatapultRun::status).map(Enum::name).orElse("?");
        return new OrderView(order.id(), order.pilotName(), order.chassisId(), order.shaftId(), order.status(),
                pilot, power, catapult, order.failureReason(), order.trail());
    }
}
