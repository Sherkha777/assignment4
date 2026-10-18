package eva.power;

import java.util.Optional;

public class PowerGridRouter {

    private final CapacitorDischargeRule rule;
    private final PowerLogRepository repository;

    public PowerGridRouter(CapacitorDischargeRule rule, PowerLogRepository repository) {
        this.rule = rule;
        this.repository = repository;
    }

    public PowerLine charge(String chassisId, double requiredMegawatts) {
        double available = repository.bayAvailableMegawatts(chassisId);
        CapacitorDischargeRule.Verdict verdict = rule.evaluate(requiredMegawatts, available);
        PowerLine line = repository.insert(
                new PowerLine(0, chassisId, requiredMegawatts, PowerStatus.DISCONNECTED, verdict.reason()));
        if (!verdict.accepted()) {
            throw new PowerFailureException(verdict.reason(), line.id());
        }
        line.transitionTo(PowerStatus.CHARGED);
        repository.update(line);
        return line;
    }

    public PowerLine drain(long lineId) {
        PowerLine line = repository.findById(lineId)
                .orElseThrow(() -> new IllegalArgumentException("no power line " + lineId));
        line.transitionTo(PowerStatus.DRAINED);
        line.annotate("capacitors vented, umbilical disconnected");
        repository.update(line);
        return line;
    }

    public Optional<PowerLine> find(long lineId) {
        return repository.findById(lineId);
    }

    public void setBayAvailableMegawatts(String chassisId, double megawatts) {
        repository.setBayAvailableMegawatts(chassisId, megawatts);
    }

    public double bayAvailableMegawatts(String chassisId) {
        return repository.bayAvailableMegawatts(chassisId);
    }
}
