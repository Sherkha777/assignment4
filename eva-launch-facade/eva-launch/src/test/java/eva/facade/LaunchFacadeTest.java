package eva.facade;

import eva.catapult.CatapultStatus;
import eva.pilot.PilotSyncStatus;
import eva.power.PowerStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LaunchFacadeTest {

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
    void happyPathLaunchesTheEva() {
        hangar.pilot("Shinji").inChassis("EVA-01").withStress(30).scramblesThrough("SHAFT-1")
                .endsAs(ChainStatus.COMPLETED)
                .pilotEnds(PilotSyncStatus.SYNC_LOCKED)
                .powerEnds(PowerStatus.CHARGED)
                .catapultEnds(CatapultStatus.LAUNCHED)
                .compensationTrailIs("");
    }

    @Test
    void whenPowerFailsAfterPilotLockedOnlyThePilotIsCompensated() {
        hangar.umbilicalSupplyDropsTo("EVA-01", 4);

        hangar.pilot("Shinji").inChassis("EVA-01").withStress(30).scramblesThrough("SHAFT-1")
                .endsAs(ChainStatus.COMPENSATED)
                .pilotEnds(PilotSyncStatus.STANDBY)
                .powerEnds(PowerStatus.DISCONNECTED)
                .neverTouchedCatapult()
                .compensationTrailIs("A:STANDBY")
                .failureMentions("B failed");
    }

    @Test
    void whenShaftIsBlockedPowerIsDrainedThenPilotIsEjected() {
        hangar.shaftIsBlockedByDebris("SHAFT-1");

        hangar.pilot("Asuka").inChassis("EVA-02").withStress(20).scramblesThrough("SHAFT-1")
                .endsAs(ChainStatus.COMPENSATED)
                .catapultEnds(CatapultStatus.RESTING)
                .powerEnds(PowerStatus.DRAINED)
                .pilotEnds(PilotSyncStatus.EJECTED)
                .compensationTrailIs("B:DRAINED > A:EJECTED")
                .failureMentions("debris");
    }

    @Test
    void whenBlastDoorStaysSealedTheSameTwoStepRollbackHappens() {
        hangar.blastDoorStaysSealed("SHAFT-3");

        hangar.pilot("Rei").inChassis("EVA-00").withStress(15).scramblesThrough("SHAFT-3")
                .endsAs(ChainStatus.COMPENSATED)
                .powerEnds(PowerStatus.DRAINED)
                .pilotEnds(PilotSyncStatus.EJECTED)
                .compensationTrailIs("B:DRAINED > A:EJECTED")
                .failureMentions("blast door");
    }

    @Test
    void stressedPilotIsRejectedBeforeAnySubsystemCommits() {
        hangar.pilot("Asuka").inChassis("EVA-02").withStress(75).scramblesThrough("SHAFT-1")
                .endsAs(ChainStatus.REJECTED)
                .neverTouchedPilot()
                .neverTouchedPower()
                .neverTouchedCatapult()
                .failureMentions("sync ratio");
    }

    @Test
    void reservedChassisRejectsASecondPilot() {
        hangar.pilot("Shinji").inChassis("EVA-01").withStress(30).scramblesThrough("SHAFT-1");

        hangar.pilot("Rei").inChassis("EVA-01").withStress(10).scramblesThrough("SHAFT-2")
                .endsAs(ChainStatus.REJECTED)
                .neverTouchedPower()
                .failureMentions("already reserved");
    }

    @Test
    void chassisIsFreeAgainAfterACompensatedLaunch() {
        hangar.umbilicalSupplyDropsTo("EVA-01", 4);
        hangar.pilot("Shinji").inChassis("EVA-01").withStress(30).scramblesThrough("SHAFT-1")
                .endsAs(ChainStatus.COMPENSATED);

        hangar.umbilicalSupplyDropsTo("EVA-01", 12);
        hangar.pilot("Shinji").inChassis("EVA-01").withStress(30).scramblesThrough("SHAFT-1")
                .endsAs(ChainStatus.COMPLETED);
    }

    @Test
    void invalidInputIsRefusedAndNothingIsStored() {
        EvaLaunchFacade facade = hangar.hangar().launchFacade();
        assertThrows(InvalidLaunchRequestException.class, () -> facade.scramble(null));
        assertThrows(InvalidLaunchRequestException.class,
                () -> facade.scramble(new LaunchRequest(" ", "EVA-01", 10, "SHAFT-1")));
        assertThrows(InvalidLaunchRequestException.class,
                () -> facade.scramble(new LaunchRequest("Shinji", null, 10, "SHAFT-1")));
        assertThrows(InvalidLaunchRequestException.class,
                () -> facade.scramble(new LaunchRequest("Shinji", "EVA-01", 10, "")));
        assertThrows(InvalidLaunchRequestException.class,
                () -> facade.scramble(new LaunchRequest("Shinji", "EVA-01", -5, "SHAFT-1")));
        assertThrows(InvalidLaunchRequestException.class,
                () -> facade.scramble(new LaunchRequest("Shinji", "EVA-01", 101, "SHAFT-1")));
        assertEquals(0, hangar.hangar().orders().findAll().size());
    }

    @Test
    void orderViewSummaryDescribesTheChain() {
        hangar.shaftIsBlockedByDebris("SHAFT-1");
        OrderView view = hangar.hangar().launchFacade()
                .scramble(new LaunchRequest("Asuka", "EVA-02", 20, "SHAFT-1"));
        assertEquals(ChainStatus.COMPENSATED, view.chain());
        assertEquals("EJECTED", view.pilot());
        assertEquals("DRAINED", view.power());
        assertEquals("RESTING", view.catapult());
        assertEquals(true, view.summary().contains("trail=[B:DRAINED > A:EJECTED]"));
    }
}
