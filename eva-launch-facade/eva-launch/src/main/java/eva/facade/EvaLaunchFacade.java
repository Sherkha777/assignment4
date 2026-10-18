package eva.facade;

import eva.catapult.CatapultFailureException;
import eva.catapult.CatapultPneumatics;
import eva.catapult.CatapultRun;
import eva.pilot.ChassisUnavailableException;
import eva.pilot.EntryPlugLauncher;
import eva.pilot.PilotRejectedException;
import eva.pilot.PilotSession;
import eva.pilot.PilotSyncStatus;
import eva.power.PowerFailureException;
import eva.power.PowerGridRouter;
import eva.power.PowerLine;
import eva.power.PowerStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EvaLaunchFacade {

    public static final double REQUIRED_MEGAWATTS = 10.0;

    private final EntryPlugLauncher launcher;
    private final PowerGridRouter grid;
    private final CatapultPneumatics pneumatics;
    private final LaunchOrderRepository orders;
    private final ChainCompensator compensator;
    private final OrderViewAssembler assembler;

    public EvaLaunchFacade(EntryPlugLauncher launcher, PowerGridRouter grid, CatapultPneumatics pneumatics,
                           LaunchOrderRepository orders) {
        this.launcher = launcher;
        this.grid = grid;
        this.pneumatics = pneumatics;
        this.orders = orders;
        this.compensator = new ChainCompensator(launcher, grid, orders);
        this.assembler = new OrderViewAssembler(launcher, grid, pneumatics);
    }

    public OrderView scramble(LaunchRequest request) {
        return scramble(request, CrashPoint.NONE);
    }

    public OrderView scramble(LaunchRequest request, CrashPoint crashPoint) {
        validate(request);
        LaunchOrder order = orders.create(request);

        PilotSession session;
        try {
            session = launcher.insertPlug(request.pilotName(), request.chassisId(), request.stress());
        } catch (PilotRejectedException | ChassisUnavailableException e) {
            order.reject(e.getMessage());
            orders.update(order);
            return assembler.assemble(order);
        }
        order.pilotLocked(session.id());
        orders.update(order);
        crashIf(crashPoint, CrashPoint.AFTER_A);

        drive(order, crashPoint);
        return assembler.assemble(order);
    }

    public List<OrderView> resumeInterrupted() {
        List<OrderView> resumed = new ArrayList<>();
        for (LaunchOrder order : orders.findNonTerminal()) {
            switch (order.status()) {
                case STARTED -> resumeStarted(order);
                case PENDING_B -> {
                    if (pilotStillLocked(order)) {
                        drive(order, CrashPoint.NONE);
                    } else {
                        compensator.compensate(order, "interrupted: pilot is no longer sync locked");
                    }
                }
                case PENDING_C -> {
                    if (powerStillCharged(order)) {
                        drive(order, CrashPoint.NONE);
                    } else {
                        compensator.compensate(order, "interrupted: umbilical power is not charged");
                    }
                }
                case COMPENSATING -> compensator.compensate(order, order.failureReason());
                default -> {
                }
            }
            resumed.add(assembler.assemble(order));
        }
        return resumed;
    }

    private void drive(LaunchOrder order, CrashPoint crashPoint) {
        if (order.status() == ChainStatus.PENDING_B) {
            try {
                PowerLine line = grid.charge(order.chassisId(), REQUIRED_MEGAWATTS);
                order.powerCharged(line.id());
                orders.update(order);
            } catch (PowerFailureException e) {
                order.linkPower(e.lineId());
                compensator.compensate(order, "B failed: " + e.getMessage());
                return;
            }
            crashIf(crashPoint, CrashPoint.AFTER_B);
        }
        if (order.status() == ChainStatus.PENDING_C) {
            try {
                CatapultRun run = pneumatics.fire(order.shaftId(), order.chassisId());
                order.launched(run.id());
                orders.update(order);
            } catch (CatapultFailureException e) {
                order.linkCatapult(e.runId());
                compensator.compensate(order, "C failed: " + e.getMessage());
            }
        }
    }

    private void resumeStarted(LaunchOrder order) {
        Optional<PilotSession> locked = launcher.lockedOn(order.chassisId());
        if (locked.isPresent() && locked.get().pilotName().equals(order.pilotName())) {
            order.linkPilot(locked.get().id());
            compensator.compensate(order, "interrupted before progress was recorded");
        } else {
            order.reject("interrupted before the entry plug was inserted");
            orders.update(order);
        }
    }

    private boolean pilotStillLocked(LaunchOrder order) {
        return order.pilotSessionId() != null && launcher.find(order.pilotSessionId())
                .map(s -> s.status() == PilotSyncStatus.SYNC_LOCKED).orElse(false);
    }

    private boolean powerStillCharged(LaunchOrder order) {
        return pilotStillLocked(order) && order.powerLineId() != null && grid.find(order.powerLineId())
                .map(l -> l.status() == PowerStatus.CHARGED).orElse(false);
    }

    private void crashIf(CrashPoint requested, CrashPoint here) {
        if (requested == here) {
            throw new SimulatedCrashException(here);
        }
    }

    private void validate(LaunchRequest request) {
        if (request == null) {
            throw new InvalidLaunchRequestException("launch request is missing");
        }
        if (isBlank(request.pilotName()) || isBlank(request.chassisId()) || isBlank(request.shaftId())) {
            throw new InvalidLaunchRequestException("pilot, chassis and shaft must all be given");
        }
        if (request.stress() < 0 || request.stress() > 100) {
            throw new InvalidLaunchRequestException("stress must be between 0 and 100");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
