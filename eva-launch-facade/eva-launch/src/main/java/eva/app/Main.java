package eva.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;

public class Main {

    public static void main(String[] args) throws IOException {
        Path dataDirectory = Path.of(args.length > 0 ? args[0] : "data");
        try (HangarSystem hangar = new HangarSystem(dataDirectory)) {
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            new ConsoleApp(hangar, in, System.out).run();
        }
    }
}
