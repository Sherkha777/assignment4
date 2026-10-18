package eva.app;

import eva.catapult.CatapultPneumatics;
import eva.catapult.LaunchTelemetryRepository;
import eva.catapult.SiloPathClearanceRule;
import eva.facade.EvaLaunchFacade;
import eva.facade.HangarControlFacade;
import eva.facade.LaunchOrderRepository;
import eva.pilot.EntryPlugLauncher;
import eva.pilot.HarmonicsThresholdCalculator;
import eva.pilot.PilotStatusRepository;
import eva.power.CapacitorDischargeRule;
import eva.power.PowerGridRouter;
import eva.power.PowerLogRepository;

import java.nio.file.Path;

public class HangarSystem implements AutoCloseable {

    private final PilotStatusRepository pilotRepository;
    private final PowerLogRepository powerRepository;
    private final LaunchTelemetryRepository telemetryRepository;
    private final LaunchOrderRepository orderRepository;
    private final EntryPlugLauncher launcher;
    private final PowerGridRouter grid;
    private final CatapultPneumatics pneumatics;
    private final EvaLaunchFacade launchFacade;
    private final HangarControlFacade controlFacade;

    public HangarSystem(Path dataDirectory) {
        this.pilotRepository = new PilotStatusRepository(dataDirectory);
        this.powerRepository = new PowerLogRepository(dataDirectory);
        this.telemetryRepository = new LaunchTelemetryRepository(dataDirectory);
        this.orderRepository = new LaunchOrderRepository(dataDirectory);
        this.launcher = new EntryPlugLauncher(new HarmonicsThresholdCalculator(), pilotRepository);
        this.grid = new PowerGridRouter(new CapacitorDischargeRule(), powerRepository);
        this.pneumatics = new CatapultPneumatics(new SiloPathClearanceRule(), telemetryRepository);
        this.launchFacade = new EvaLaunchFacade(launcher, grid, pneumatics, orderRepository);
        this.controlFacade = new HangarControlFacade(launcher, grid, pneumatics, orderRepository);
    }

    public EvaLaunchFacade launchFacade() {
        return launchFacade;
    }

    public HangarControlFacade controlFacade() {
        return controlFacade;
    }

    public EntryPlugLauncher launcher() {
        return launcher;
    }

    public PowerGridRouter grid() {
        return grid;
    }

    public CatapultPneumatics pneumatics() {
        return pneumatics;
    }

    public LaunchOrderRepository orders() {
        return orderRepository;
    }

    @Override
    public void close() {
        pilotRepository.close();
        powerRepository.close();
        telemetryRepository.close();
        orderRepository.close();
    }
}
