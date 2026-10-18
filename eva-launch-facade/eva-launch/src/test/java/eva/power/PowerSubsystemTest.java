package eva.power;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerSubsystemTest {

    @TempDir
    Path dir;

    private PowerLogRepository repository;
    private PowerGridRouter router;

    @BeforeEach
    void setUp() {
        repository = new PowerLogRepository(dir);
        router = new PowerGridRouter(new CapacitorDischargeRule(), repository);
    }

    @AfterEach
    void tearDown() {
        repository.close();
    }

    @Test
    void ruleAcceptsSupplyWithSafetyMargin() {
        CapacitorDischargeRule rule = new CapacitorDischargeRule();
        assertTrue(rule.evaluate(10, 11).accepted());
        assertFalse(rule.evaluate(10, 10.5).accepted());
        assertTrue(rule.evaluate(10, 10.5).reason().contains("capacitor trip"));
    }

    @Test
    void ruleRefusesNonPositiveDemand() {
        assertThrows(IllegalArgumentException.class, () -> new CapacitorDischargeRule().evaluate(0, 10));
    }

    @Test
    void statusOnlyAllowsLegalTransitions() {
        assertTrue(PowerStatus.DISCONNECTED.canMoveTo(PowerStatus.CHARGED));
        assertFalse(PowerStatus.DISCONNECTED.canMoveTo(PowerStatus.DRAINED));
        assertTrue(PowerStatus.CHARGED.canMoveTo(PowerStatus.DRAINED));
        assertFalse(PowerStatus.DRAINED.canMoveTo(PowerStatus.CHARGED));
    }

    @Test
    void chargeStoresAChargedLine() {
        PowerLine line = router.charge("EVA-01", 10);
        PowerLine stored = repository.findById(line.id()).orElseThrow();
        assertEquals(PowerStatus.CHARGED, stored.status());
        assertEquals("EVA-01", stored.chassisId());
        assertEquals(10.0, stored.megawatts());
    }

    @Test
    void tripLeavesTheLineDisconnectedAndReportsItsId() {
        router.setBayAvailableMegawatts("EVA-01", 3);
        PowerFailureException failure = assertThrows(PowerFailureException.class, () -> router.charge("EVA-01", 10));
        PowerLine stored = router.find(failure.lineId()).orElseThrow();
        assertEquals(PowerStatus.DISCONNECTED, stored.status());
        assertTrue(stored.note().contains("capacitor trip"));
    }

    @Test
    void drainVentsAChargedLine() {
        PowerLine line = router.charge("EVA-01", 10);
        router.drain(line.id());
        assertEquals(PowerStatus.DRAINED, router.find(line.id()).orElseThrow().status());
    }

    @Test
    void drainingAnUnchargedLineIsIllegal() {
        router.setBayAvailableMegawatts("EVA-01", 3);
        PowerFailureException failure = assertThrows(PowerFailureException.class, () -> router.charge("EVA-01", 10));
        assertThrows(IllegalStateException.class, () -> router.drain(failure.lineId()));
        assertThrows(IllegalArgumentException.class, () -> router.drain(999));
    }

    @Test
    void baySupplyDefaultsAndCanBeOverridden() {
        assertEquals(PowerLogRepository.DEFAULT_BAY_SUPPLY, router.bayAvailableMegawatts("EVA-09"));
        router.setBayAvailableMegawatts("EVA-09", 5);
        router.setBayAvailableMegawatts("EVA-09", 6);
        assertEquals(6.0, router.bayAvailableMegawatts("EVA-09"));
    }

    @Test
    void stateSurvivesReopeningTheStore() {
        PowerLine line = router.charge("EVA-01", 10);
        router.setBayAvailableMegawatts("EVA-01", 4);
        repository.close();
        repository = new PowerLogRepository(dir);
        router = new PowerGridRouter(new CapacitorDischargeRule(), repository);
        assertEquals(PowerStatus.CHARGED, router.find(line.id()).orElseThrow().status());
        assertEquals(4.0, router.bayAvailableMegawatts("EVA-01"));
        assertEquals(1, repository.findAll().size());
    }
}
