package eva.power;

import java.nio.file.Path;

public class PowerStandalone {

    public static void main(String[] args) {
        try (PowerLogRepository repository = new PowerLogRepository(Path.of("data", "standalone"))) {
            PowerGridRouter router = new PowerGridRouter(new CapacitorDischargeRule(), repository);
            PowerLine line = router.charge("EVA-00", 8.0);
            System.out.println("line " + line.id() + " " + line.status());
            router.drain(line.id());
            System.out.println("lines on disk: " + repository.findAll().size());
        }
    }
}
