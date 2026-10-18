package eva.catapult;

import java.nio.file.Path;

public class CatapultStandalone {

    public static void main(String[] args) {
        try (LaunchTelemetryRepository repository = new LaunchTelemetryRepository(Path.of("data", "standalone"))) {
            CatapultPneumatics pneumatics = new CatapultPneumatics(new SiloPathClearanceRule(), repository);
            CatapultRun run = pneumatics.fire("SHAFT-STANDALONE", "EVA-00");
            System.out.println("run " + run.id() + " " + run.status());
            System.out.println("runs on disk: " + repository.findAll().size());
        }
    }
}
