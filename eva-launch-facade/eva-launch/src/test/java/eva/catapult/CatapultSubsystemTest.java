package eva.catapult;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatapultSubsystemTest {

    @TempDir
    Path dir;

    private LaunchTelemetryRepository repository;
    private CatapultPneumatics pneumatics;

    @BeforeEach
    void setUp() {
        repository = new LaunchTelemetryRepository(dir);
        pneumatics = new CatapultPneumatics(new SiloPathClearanceRule(), repository);
    }

    @AfterEach
    void tearDown() {
        repository.close();
    }

    @Test
    void ruleExplainsEachKindOfBlockage() {
        SiloPathClearanceRule rule = new SiloPathClearanceRule();
        assertTrue(rule.evaluate(ShaftCondition.clear()).cleared());
        assertFalse(rule.evaluate(new ShaftCondition(false, false)).cleared());
        assertTrue(rule.evaluate(new ShaftCondition(false, false)).reason().contains("blast door"));
        assertTrue(rule.evaluate(new ShaftCondition(true, true)).reason().contains("debris"));
    }

    @Test
    void statusOnlyAllowsLegalTransitions() {
        assertTrue(CatapultStatus.RESTING.canMoveTo(CatapultStatus.PRESSURIZED));
        assertFalse(CatapultStatus.RESTING.canMoveTo(CatapultStatus.LAUNCHED));
        assertTrue(CatapultStatus.PRESSURIZED.canMoveTo(CatapultStatus.RESTING));
        assertTrue(CatapultStatus.PRESSURIZED.canMoveTo(CatapultStatus.LAUNCHED));
        assertFalse(CatapultStatus.LAUNCHED.canMoveTo(CatapultStatus.RESTING));
    }

    @Test
    void runRefusesIllegalTransition() {
        CatapultRun run = new CatapultRun(1, "SHAFT-1", "EVA-01", CatapultStatus.RESTING, "");
        assertThrows(IllegalStateException.class, () -> run.transitionTo(CatapultStatus.LAUNCHED));
    }

    @Test
    void fireLaunchesThroughAClearShaft() {
        CatapultRun run = pneumatics.fire("SHAFT-1", "EVA-01");
        CatapultRun stored = repository.findById(run.id()).orElseThrow();
        assertEquals(CatapultStatus.LAUNCHED, stored.status());
        assertEquals("SHAFT-1", stored.shaftId());
        assertEquals("EVA-01", stored.chassisId());
    }

    @Test
    void blockedShaftVentsBackToResting() {
        pneumatics.setShaftCondition("SHAFT-1", true, true);
        CatapultFailureException failure = assertThrows(CatapultFailureException.class,
                () -> pneumatics.fire("SHAFT-1", "EVA-01"));
        CatapultRun stored = pneumatics.find(failure.runId()).orElseThrow();
        assertEquals(CatapultStatus.RESTING, stored.status());
        assertTrue(stored.note().contains("debris"));
    }

    @Test
    void shaftConditionDefaultsToClearAndIsStored() {
        assertEquals(ShaftCondition.clear(), pneumatics.shaftCondition("SHAFT-7"));
        pneumatics.setShaftCondition("SHAFT-7", false, false);
        pneumatics.setShaftCondition("SHAFT-7", true, false);
        assertEquals(new ShaftCondition(true, false), pneumatics.shaftCondition("SHAFT-7"));
    }

    @Test
    void stateSurvivesReopeningTheStore() {
        CatapultRun run = pneumatics.fire("SHAFT-1", "EVA-01");
        pneumatics.setShaftCondition("SHAFT-2", false, true);
        repository.close();
        repository = new LaunchTelemetryRepository(dir);
        pneumatics = new CatapultPneumatics(new SiloPathClearanceRule(), repository);
        assertEquals(CatapultStatus.LAUNCHED, pneumatics.find(run.id()).orElseThrow().status());
        assertEquals(new ShaftCondition(false, true), pneumatics.shaftCondition("SHAFT-2"));
        assertEquals(1, repository.findAll().size());
    }
}
