package eva.pilot;

import java.nio.file.Path;

public class PilotStandalone {

    public static void main(String[] args) {
        try (PilotStatusRepository repository = new PilotStatusRepository(Path.of("data", "standalone"))) {
            EntryPlugLauncher launcher = new EntryPlugLauncher(new HarmonicsThresholdCalculator(), repository);
            PilotSession session = launcher.insertPlug("Rei", "EVA-00-" + System.nanoTime(), 25);
            System.out.println("locked: " + session.id() + " " + session.status());
            launcher.emergencyEject(session.id());
            System.out.println("sessions on disk: " + repository.findAll().size());
        }
    }
}
