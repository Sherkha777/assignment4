package eva.facade;

import eva.pilot.PilotSyncStatus;
import eva.power.PowerStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HangarControlFacadeTest {

    @TempDir
    Path dir;

    private HangarScenario hangar;
    private HangarControlFacade control;

    @BeforeEach
    void setUp() {
        hangar = HangarScenario.inAFreshHangar(dir);
        control = hangar.hangar().controlFacade();
    }

    @AfterEach
    void tearDown() {
        hangar.close();
    }

    @Test
    void listsEveryOrderWithItsChainState() {
        hangar.pilot("Shinji").inChassis("EVA-01").withStress(30).scramblesThrough("SHAFT-1");
        hangar.pilot("Asuka").inChassis("EVA-02").withStress(90).scramblesThrough("SHAFT-1");

        assertEquals(2, control.listOrders().size());
        assertEquals(ChainStatus.COMPLETED, control.listOrders().get(0).chain());
        assertEquals(ChainStatus.REJECTED, control.listOrders().get(1).chain());
    }

    @Test
    void inspectShowsStatusFromEverySubsystem() {
        HangarScenario.Outcome order = hangar.pilot("Shinji").inChassis("EVA-01").withStress(30)
                .scramblesThrough("SHAFT-1");

        OrderView view = control.inspect(order.orderId());

        assertEquals("SYNC_LOCKED", view.pilot());
        assertEquals("CHARGED", view.power());
        assertEquals("LAUNCHED", view.catapult());
    }

    @Test
    void inspectOfUnknownOrderFails() {
        assertThrows(OrderNotFoundException.class, () -> control.inspect(404));
        assertThrows(OrderNotFoundException.class, () -> control.manualCompensate(404));
    }

    @Test
    void manualCompensationOfAHalfFinishedOrderRollsBackTwoSteps() {
        HangarScenario.Outcome order = hangar.pilot("Asuka").inChassis("EVA-02").withStress(20)
                .scramblesThroughUntil("SHAFT-2", CrashPoint.AFTER_B);

        OrderView view = control.manualCompensate(order.orderId());

        assertEquals(ChainStatus.COMPENSATED, view.chain());
        order.powerEnds(PowerStatus.DRAINED)
                .pilotEnds(PilotSyncStatus.EJECTED)
                .compensationTrailIs("B:DRAINED > A:EJECTED")
                .failureMentions("manual");
    }

    @Test
    void manualCompensationBeforePowerOnlyUnlatchesThePilot() {
        HangarScenario.Outcome order = hangar.pilot("Shinji").inChassis("EVA-01").withStress(30)
                .scramblesThroughUntil("SHAFT-1", CrashPoint.AFTER_A);

        control.manualCompensate(order.orderId());

        order.pilotEnds(PilotSyncStatus.STANDBY).compensationTrailIs("A:STANDBY");
    }

    @Test
    void finishedOrdersCannotBeCompensated() {
        HangarScenario.Outcome order = hangar.pilot("Shinji").inChassis("EVA-01").withStress(30)
                .scramblesThrough("SHAFT-1");

        assertThrows(OrderNotCompensableException.class, () -> control.manualCompensate(order.orderId()));
    }

    @Test
    void operatorCanChangeBayPowerAndShaftConditions() {
        control.setBayPower("EVA-05", 3.5);
        control.setShaftCondition("SHAFT-5", false, true);

        assertEquals(3.5, control.bayPower("EVA-05"));
        assertTrue(control.shaftReport("SHAFT-5").contains("sealed"));
        assertTrue(control.shaftReport("SHAFT-5").contains("debris present"));
        assertTrue(control.shaftReport("SHAFT-6").contains("door open"));
    }
}
