package eva.facade;

import eva.app.HangarSystem;
import eva.catapult.CatapultRun;
import eva.catapult.CatapultStatus;
import eva.pilot.PilotSession;
import eva.pilot.PilotSyncStatus;
import eva.power.PowerLine;
import eva.power.PowerStatus;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HangarScenario implements AutoCloseable {

    private final Path directory;
    private HangarSystem hangar;

    private HangarScenario(Path directory) {
        this.directory = directory;
        this.hangar = new HangarSystem(directory);
    }

    public static HangarScenario inAFreshHangar(Path directory) {
        return new HangarScenario(directory);
    }

    public HangarSystem hangar() {
        return hangar;
    }

    public HangarScenario umbilicalSupplyDropsTo(String chassis, double megawatts) {
        hangar.controlFacade().setBayPower(chassis, megawatts);
        return this;
    }

    public HangarScenario shaftIsBlockedByDebris(String shaft) {
        hangar.controlFacade().setShaftCondition(shaft, true, true);
        return this;
    }

    public HangarScenario blastDoorStaysSealed(String shaft) {
        hangar.controlFacade().setShaftCondition(shaft, false, false);
        return this;
    }

    public HangarScenario hangarRestarts() {
        hangar.close();
        hangar = new HangarSystem(directory);
        return this;
    }

    public PilotStep pilot(String name) {
        return new PilotStep(name);
    }

    public Outcome outcomeOf(long orderId) {
        return new Outcome(orderId);
    }

    @Override
    public void close() {
        hangar.close();
    }

    public class PilotStep {

        private final String name;
        private String chassis = "EVA-01";
        private double stress = 20;

        private PilotStep(String name) {
            this.name = name;
        }

        public PilotStep inChassis(String chassis) {
            this.chassis = chassis;
            return this;
        }

        public PilotStep withStress(double stress) {
            this.stress = stress;
            return this;
        }

        public Outcome scramblesThrough(String shaft) {
            OrderView view = hangar.launchFacade().scramble(new LaunchRequest(name, chassis, stress, shaft));
            return new Outcome(view.orderId());
        }

        public Outcome scramblesThroughUntil(String shaft, CrashPoint crash) {
            try {
                hangar.launchFacade().scramble(new LaunchRequest(name, chassis, stress, shaft), crash);
            } catch (SimulatedCrashException e) {
                return new Outcome(hangar.orders().findAll().stream().mapToLong(LaunchOrder::id).max().orElseThrow());
            }
            throw new AssertionError("expected a simulated crash");
        }
    }

    public class Outcome {

        private final long orderId;

        private Outcome(long orderId) {
            this.orderId = orderId;
        }

        public long orderId() {
            return orderId;
        }

        private LaunchOrder order() {
            return hangar.orders().find(orderId).orElseThrow();
        }

        public Outcome endsAs(ChainStatus status) {
            assertEquals(status, order().status());
            return this;
        }

        public Outcome pilotEnds(PilotSyncStatus status) {
            PilotSession session = hangar.launcher().find(order().pilotSessionId()).orElseThrow();
            assertEquals(status, session.status());
            return this;
        }

        public Outcome powerEnds(PowerStatus status) {
            PowerLine line = hangar.grid().find(order().powerLineId()).orElseThrow();
            assertEquals(status, line.status());
            return this;
        }

        public Outcome catapultEnds(CatapultStatus status) {
            CatapultRun run = hangar.pneumatics().find(order().catapultRunId()).orElseThrow();
            assertEquals(status, run.status());
            return this;
        }

        public Outcome neverTouchedPilot() {
            assertNull(order().pilotSessionId());
            assertTrue(hangar.launcher().lockedOn(order().chassisId()).isEmpty());
            return this;
        }

        public Outcome neverTouchedPower() {
            assertNull(order().powerLineId());
            return this;
        }

        public Outcome neverTouchedCatapult() {
            assertNull(order().catapultRunId());
            return this;
        }

        public Outcome compensationTrailIs(String trail) {
            assertEquals(trail, order().trail());
            return this;
        }

        public Outcome failureMentions(String text) {
            assertNotNull(order().failureReason());
            assertTrue(order().failureReason().contains(text),
                    "reason was: " + order().failureReason());
            return this;
        }
    }
}
