package eva.pilot;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PilotSubsystemTest {

    @TempDir
    Path dir;

    private PilotStatusRepository repository;
    private EntryPlugLauncher launcher;

    @BeforeEach
    void setUp() {
        repository = new PilotStatusRepository(dir);
        launcher = new EntryPlugLauncher(new HarmonicsThresholdCalculator(), repository);
    }

    @AfterEach
    void tearDown() {
        repository.close();
    }

    @Test
    void calculatorTurnsStressIntoSyncRatio() {
        HarmonicsThresholdCalculator calculator = new HarmonicsThresholdCalculator();
        assertEquals(70.0, calculator.syncRatio(30));
        assertTrue(calculator.isAcceptable(60));
        assertFalse(calculator.isAcceptable(60.5));
    }

    @Test
    void calculatorRejectsStressOutsideScale() {
        HarmonicsThresholdCalculator calculator = new HarmonicsThresholdCalculator();
        assertThrows(IllegalArgumentException.class, () -> calculator.syncRatio(-1));
        assertThrows(IllegalArgumentException.class, () -> calculator.syncRatio(101));
    }

    @Test
    void statusOnlyAllowsLegalTransitions() {
        assertTrue(PilotSyncStatus.STANDBY.canMoveTo(PilotSyncStatus.SYNC_LOCKED));
        assertFalse(PilotSyncStatus.STANDBY.canMoveTo(PilotSyncStatus.EJECTED));
        assertTrue(PilotSyncStatus.SYNC_LOCKED.canMoveTo(PilotSyncStatus.STANDBY));
        assertTrue(PilotSyncStatus.SYNC_LOCKED.canMoveTo(PilotSyncStatus.EJECTED));
        assertFalse(PilotSyncStatus.EJECTED.canMoveTo(PilotSyncStatus.STANDBY));
    }

    @Test
    void sessionRefusesIllegalTransition() {
        PilotSession session = new PilotSession(1, "Shinji", "EVA-01", 10, 90, PilotSyncStatus.STANDBY);
        assertThrows(IllegalStateException.class, () -> session.transitionTo(PilotSyncStatus.EJECTED));
        session.transitionTo(PilotSyncStatus.SYNC_LOCKED);
        assertEquals(PilotSyncStatus.SYNC_LOCKED, session.status());
    }

    @Test
    void insertPlugLocksAndPersistsThePilot() {
        PilotSession session = launcher.insertPlug("Shinji", "EVA-01", 30);
        assertEquals(PilotSyncStatus.SYNC_LOCKED, session.status());
        assertEquals(70.0, session.syncRatio());
        PilotSession stored = repository.findById(session.id()).orElseThrow();
        assertEquals("Shinji", stored.pilotName());
        assertEquals("EVA-01", stored.chassisId());
        assertEquals(30.0, stored.stress());
        assertEquals(PilotSyncStatus.SYNC_LOCKED, stored.status());
    }

    @Test
    void stressedPilotIsRejectedBeforeAnythingIsStored() {
        assertThrows(PilotRejectedException.class, () -> launcher.insertPlug("Asuka", "EVA-02", 80));
        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void chassisCannotBeReservedTwice() {
        launcher.insertPlug("Shinji", "EVA-01", 30);
        assertThrows(ChassisUnavailableException.class, () -> launcher.insertPlug("Rei", "EVA-01", 10));
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void unlatchReturnsToStandbyAndFreesTheChassis() {
        PilotSession session = launcher.insertPlug("Shinji", "EVA-01", 30);
        launcher.unlatch(session.id());
        assertEquals(PilotSyncStatus.STANDBY, launcher.find(session.id()).orElseThrow().status());
        assertTrue(launcher.lockedOn("EVA-01").isEmpty());
        launcher.insertPlug("Rei", "EVA-01", 10);
    }

    @Test
    void emergencyEjectIsFinal() {
        PilotSession session = launcher.insertPlug("Shinji", "EVA-01", 30);
        launcher.emergencyEject(session.id());
        assertEquals(PilotSyncStatus.EJECTED, launcher.find(session.id()).orElseThrow().status());
        assertThrows(IllegalStateException.class, () -> launcher.unlatch(session.id()));
    }

    @Test
    void movingAnUnknownSessionFails() {
        assertThrows(IllegalArgumentException.class, () -> launcher.emergencyEject(999));
    }

    @Test
    void stateSurvivesReopeningTheStore() {
        PilotSession session = launcher.insertPlug("Shinji", "EVA-01", 30);
        repository.close();
        repository = new PilotStatusRepository(dir);
        launcher = new EntryPlugLauncher(new HarmonicsThresholdCalculator(), repository);
        assertEquals(PilotSyncStatus.SYNC_LOCKED, launcher.find(session.id()).orElseThrow().status());
        assertTrue(launcher.lockedOn("EVA-01").isPresent());
    }
}
