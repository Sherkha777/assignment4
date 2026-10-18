package eva.facade;

import eva.catapult.CatapultStatus;
import eva.pilot.PilotSyncStatus;
import eva.power.PowerStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryTest {

    @TempDir
    Path dir;

    private HangarScenario hangar;

    @BeforeEach
    void setUp() {
        hangar = HangarScenario.inAFreshHangar(dir);
    }

    @AfterEach
    void tearDown() {
        hangar.close();
    }

    @Test
    void crashAfterPilotLockThenRestartContinuesTheChain() {
        HangarScenario.Outcome order = hangar.pilot("Shinji").inChassis("EVA-01").withStress(30)
                .scramblesThroughUntil("SHAFT-1", CrashPoint.AFTER_A);
        order.endsAs(ChainStatus.PENDING_B);

        hangar.hangarRestarts();
        List<OrderView> resumed = hangar.hangar().launchFacade().resumeInterrupted();

        assertEquals(1, resumed.size());
        order.endsAs(ChainStatus.COMPLETED)
                .pilotEnds(PilotSyncStatus.SYNC_LOCKED)
                .powerEnds(PowerStatus.CHARGED)
                .catapultEnds(CatapultStatus.LAUNCHED);
    }

    @Test
    void crashAfterPilotLockThenPowerLossCompensatesOnlyThePilot() {
        HangarScenario.Outcome order = hangar.pilot("Shinji").inChassis("EVA-01").withStress(30)
                .scramblesThroughUntil("SHAFT-1", CrashPoint.AFTER_A);

        hangar.hangarRestarts().umbilicalSupplyDropsTo("EVA-01", 2);
        hangar.hangar().launchFacade().resumeInterrupted();

        order.endsAs(ChainStatus.COMPENSATED)
                .pilotEnds(PilotSyncStatus.STANDBY)
                .powerEnds(PowerStatus.DISCONNECTED)
                .compensationTrailIs("A:STANDBY");
    }

    @Test
    void crashAfterPowerChargeThenRestartFinishesTheLaunch() {
        HangarScenario.Outcome order = hangar.pilot("Asuka").inChassis("EVA-02").withStress(20)
                .scramblesThroughUntil("SHAFT-2", CrashPoint.AFTER_B);
        order.endsAs(ChainStatus.PENDING_C).powerEnds(PowerStatus.CHARGED);

        hangar.hangarRestarts();
        hangar.hangar().launchFacade().resumeInterrupted();

        order.endsAs(ChainStatus.COMPLETED).catapultEnds(CatapultStatus.LAUNCHED);
    }

    @Test
    void crashAfterPowerChargeThenBlockedShaftRollsBackTwoSteps() {
        HangarScenario.Outcome order = hangar.pilot("Asuka").inChassis("EVA-02").withStress(20)
                .scramblesThroughUntil("SHAFT-2", CrashPoint.AFTER_B);

        hangar.hangarRestarts().shaftIsBlockedByDebris("SHAFT-2");
        hangar.hangar().launchFacade().resumeInterrupted();

        order.endsAs(ChainStatus.COMPENSATED)
                .catapultEnds(CatapultStatus.RESTING)
                .powerEnds(PowerStatus.DRAINED)
                .pilotEnds(PilotSyncStatus.EJECTED)
                .compensationTrailIs("B:DRAINED > A:EJECTED");
    }

    @Test
    void interruptedCompensationIsFinishedFromWhatIsOnDisk() {
        HangarScenario.Outcome order = hangar.pilot("Asuka").inChassis("EVA-02").withStress(20)
                .scramblesThroughUntil("SHAFT-2", CrashPoint.AFTER_B);
        LaunchOrder stored = hangar.hangar().orders().find(order.orderId()).orElseThrow();
        stored.beginCompensation("C failed: shaft blocked by debris");
        hangar.hangar().orders().update(stored);

        hangar.hangarRestarts();
        hangar.hangar().launchFacade().resumeInterrupted();

        order.endsAs(ChainStatus.COMPENSATED)
                .powerEnds(PowerStatus.DRAINED)
                .pilotEnds(PilotSyncStatus.EJECTED)
                .compensationTrailIs("B:DRAINED > A:EJECTED")
                .failureMentions("debris");
    }

    @Test
    void pendingBOrderWhosePilotIsGoneIsCompensatedNotContinued() {
        HangarScenario.Outcome order = hangar.pilot("Shinji").inChassis("EVA-01").withStress(30)
                .scramblesThroughUntil("SHAFT-1", CrashPoint.AFTER_A);
        long sessionId = hangar.hangar().orders().find(order.orderId()).orElseThrow().pilotSessionId();
        hangar.hangar().launcher().emergencyEject(sessionId);

        hangar.hangarRestarts();
        hangar.hangar().launchFacade().resumeInterrupted();

        order.endsAs(ChainStatus.COMPENSATED).pilotEnds(PilotSyncStatus.EJECTED).neverTouchedPower();
    }

    @Test
    void pendingCOrderWhosePowerIsGoneIsCompensatedNotContinued() {
        HangarScenario.Outcome order = hangar.pilot("Asuka").inChassis("EVA-02").withStress(20)
                .scramblesThroughUntil("SHAFT-2", CrashPoint.AFTER_B);
        long lineId = hangar.hangar().orders().find(order.orderId()).orElseThrow().powerLineId();
        hangar.hangar().grid().drain(lineId);

        hangar.hangarRestarts();
        hangar.hangar().launchFacade().resumeInterrupted();

        order.endsAs(ChainStatus.COMPENSATED)
                .powerEnds(PowerStatus.DRAINED)
                .pilotEnds(PilotSyncStatus.EJECTED)
                .compensationTrailIs("A:EJECTED")
                .neverTouchedCatapult();
    }

    @Test
    void startedOrderWithALockedPilotButNoRecordedProgressIsUndone() {
        hangar.hangar().launcher().insertPlug("Shinji", "EVA-01", 30);
        LaunchOrder order = hangar.hangar().orders().create(new LaunchRequest("Shinji", "EVA-01", 30, "SHAFT-1"));

        hangar.hangarRestarts();
        hangar.hangar().launchFacade().resumeInterrupted();

        hangar.outcomeOf(order.id()).endsAs(ChainStatus.COMPENSATED).pilotEnds(PilotSyncStatus.STANDBY);
    }

    @Test
    void startedOrderThatNeverReachedThePilotIsRejected() {
        LaunchOrder order = hangar.hangar().orders().create(new LaunchRequest("Rei", "EVA-00", 10, "SHAFT-1"));

        hangar.hangarRestarts();
        hangar.hangar().launchFacade().resumeInterrupted();

        hangar.outcomeOf(order.id()).endsAs(ChainStatus.REJECTED).neverTouchedPilot()
                .failureMentions("interrupted");
    }

    @Test
    void finishedOrdersAreLeftAloneByRecovery() {
        hangar.pilot("Shinji").inChassis("EVA-01").withStress(30).scramblesThrough("SHAFT-1");

        hangar.hangarRestarts();

        assertTrue(hangar.hangar().launchFacade().resumeInterrupted().isEmpty());
    }

    @Test
    void completedStateIsReadBackCorrectlyAfterARestart() {
        HangarScenario.Outcome order = hangar.pilot("Shinji").inChassis("EVA-01").withStress(30)
                .scramblesThrough("SHAFT-1");

        hangar.hangarRestarts();

        order.endsAs(ChainStatus.COMPLETED).catapultEnds(CatapultStatus.LAUNCHED);
        assertEquals("LAUNCHED", hangar.hangar().controlFacade().inspect(order.orderId()).catapult());
    }
}
