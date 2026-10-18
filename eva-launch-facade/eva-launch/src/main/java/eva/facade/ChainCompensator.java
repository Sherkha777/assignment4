package eva.facade;

import eva.pilot.EntryPlugLauncher;
import eva.pilot.PilotSession;
import eva.pilot.PilotSyncStatus;
import eva.power.PowerGridRouter;
import eva.power.PowerLine;
import eva.power.PowerStatus;

class ChainCompensator {

    private final EntryPlugLauncher launcher;
    private final PowerGridRouter grid;
    private final LaunchOrderRepository orders;

    ChainCompensator(EntryPlugLauncher launcher, PowerGridRouter grid, LaunchOrderRepository orders) {
        this.launcher = launcher;
        this.grid = grid;
        this.orders = orders;
    }

    void compensate(LaunchOrder order, String reason) {
        order.beginCompensation(reason);
        orders.update(order);

        boolean poweredUp = false;
        if (order.powerLineId() != null) {
            PowerLine line = grid.find(order.powerLineId()).orElse(null);
            if (line != null) {
                poweredUp = line.status() == PowerStatus.CHARGED || line.status() == PowerStatus.DRAINED;
                if (line.status() == PowerStatus.CHARGED) {
                    grid.drain(line.id());
                    order.addTrail("B:DRAINED");
                    orders.update(order);
                }
            }
        }

        if (order.pilotSessionId() != null) {
            PilotSession session = launcher.find(order.pilotSessionId()).orElse(null);
            if (session != null && session.status() == PilotSyncStatus.SYNC_LOCKED) {
                if (poweredUp) {
                    launcher.emergencyEject(session.id());
                    order.addTrail("A:EJECTED");
                } else {
                    launcher.unlatch(session.id());
                    order.addTrail("A:STANDBY");
                }
                orders.update(order);
            }
        }

        order.compensated();
        orders.update(order);
    }
}
