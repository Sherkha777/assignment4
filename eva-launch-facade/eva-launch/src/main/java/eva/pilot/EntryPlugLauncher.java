package eva.pilot;

import java.util.Optional;

public class EntryPlugLauncher {

    private final HarmonicsThresholdCalculator calculator;
    private final PilotStatusRepository repository;

    public EntryPlugLauncher(HarmonicsThresholdCalculator calculator, PilotStatusRepository repository) {
        this.calculator = calculator;
        this.repository = repository;
    }

    public PilotSession insertPlug(String pilotName, String chassisId, double stress) {
        double ratio = calculator.syncRatio(stress);
        if (!calculator.isAcceptable(stress)) {
            throw new PilotRejectedException("sync ratio " + ratio + "% is below "
                    + HarmonicsThresholdCalculator.MIN_SYNC_RATIO + "% for " + pilotName);
        }
        if (repository.findLockedOnChassis(chassisId).isPresent()) {
            throw new ChassisUnavailableException(chassisId + " is already reserved by another pilot");
        }
        PilotSession session = repository.insert(
                new PilotSession(0, pilotName, chassisId, stress, ratio, PilotSyncStatus.STANDBY));
        session.transitionTo(PilotSyncStatus.SYNC_LOCKED);
        repository.updateStatus(session);
        return session;
    }

    public PilotSession unlatch(long sessionId) {
        return move(sessionId, PilotSyncStatus.STANDBY);
    }

    public PilotSession emergencyEject(long sessionId) {
        return move(sessionId, PilotSyncStatus.EJECTED);
    }

    public Optional<PilotSession> find(long sessionId) {
        return repository.findById(sessionId);
    }

    public Optional<PilotSession> lockedOn(String chassisId) {
        return repository.findLockedOnChassis(chassisId);
    }

    private PilotSession move(long sessionId, PilotSyncStatus target) {
        PilotSession session = repository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("no pilot session " + sessionId));
        session.transitionTo(target);
        repository.updateStatus(session);
        return session;
    }
}
