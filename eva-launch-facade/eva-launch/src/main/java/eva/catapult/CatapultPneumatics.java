package eva.catapult;

import java.util.Optional;

public class CatapultPneumatics {

    private final SiloPathClearanceRule rule;
    private final LaunchTelemetryRepository repository;

    public CatapultPneumatics(SiloPathClearanceRule rule, LaunchTelemetryRepository repository) {
        this.rule = rule;
        this.repository = repository;
    }

    public CatapultRun fire(String shaftId, String chassisId) {
        CatapultRun run = repository.insert(new CatapultRun(0, shaftId, chassisId, CatapultStatus.RESTING, "queued"));
        run.transitionTo(CatapultStatus.PRESSURIZED);
        run.annotate("pneumatics pressurized");
        repository.update(run);
        SiloPathClearanceRule.Clearance clearance = rule.evaluate(repository.shaftCondition(shaftId));
        if (!clearance.cleared()) {
            run.transitionTo(CatapultStatus.RESTING);
            run.annotate("aborted: " + clearance.reason());
            repository.update(run);
            throw new CatapultFailureException(clearance.reason(), run.id());
        }
        run.transitionTo(CatapultStatus.LAUNCHED);
        run.annotate("launched up " + shaftId);
        repository.update(run);
        return run;
    }

    public Optional<CatapultRun> find(long runId) {
        return repository.findById(runId);
    }

    public void setShaftCondition(String shaftId, boolean doorOpen, boolean debris) {
        repository.setShaftCondition(shaftId, new ShaftCondition(doorOpen, debris));
    }

    public ShaftCondition shaftCondition(String shaftId) {
        return repository.shaftCondition(shaftId);
    }
}
