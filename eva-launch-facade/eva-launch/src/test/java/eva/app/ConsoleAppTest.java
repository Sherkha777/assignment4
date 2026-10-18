package eva.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleAppTest {

    @TempDir
    Path dir;

    private String run(String... lines) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (HangarSystem hangar = new HangarSystem(dir)) {
            BufferedReader in = new BufferedReader(new StringReader(String.join("\n", lines) + "\n"));
            new ConsoleApp(hangar, in, new PrintStream(bytes)).run();
        }
        return bytes.toString();
    }

    @Test
    void fullChainBothCompensationsAndRecoveryCanBeDrivenFromTheMenu() throws IOException {
        String output = run(
                "1", "Shinji", "EVA-01", "30", "SHAFT-1",
                "7", "EVA-02", "4",
                "1", "Rei", "EVA-02", "20", "SHAFT-2",
                "8", "SHAFT-3", "n", "y",
                "1", "Asuka", "EVA-03", "10", "SHAFT-3",
                "3", "Misato", "EVA-04", "15", "SHAFT-4",
                "2", "Kaji", "EVA-05", "15", "SHAFT-5",
                "4", "5", "6", "1", "9", "5", "0");
        assertTrue(output.contains("chain=COMPLETED"));
        assertTrue(output.contains("trail=[A:STANDBY]"));
        assertTrue(output.contains("trail=[B:DRAINED > A:EJECTED]"));
        assertTrue(output.contains("simulated process crash AFTER_B"));
        assertTrue(output.contains("simulated process crash AFTER_A"));
        assertTrue(output.contains("EVA-02 bay supply is now 4.0 MW"));
        assertTrue(output.contains("SHAFT-3: door sealed, debris present"));
        assertTrue(output.contains("hangar closed"));
    }

    @Test
    void badInputIsReportedWithoutStoppingTheConsole() throws IOException {
        String output = run(
                "1", "", "EVA-01", "30", "SHAFT-1",
                "1", "Shinji", "EVA-01", "lots", 
                "6", "77",
                "9", "77",
                "6", "x",
                "42",
                "5",
                "4");
        assertTrue(output.contains("refused: pilot, chassis and shaft must all be given"));
        assertTrue(output.contains("that was not a number"));
        assertTrue(output.contains("refused: no launch order 77"));
        assertTrue(output.contains("unknown option"));
        assertTrue(output.contains("no orders yet"));
        assertTrue(output.contains("nothing to resume"));
    }

    @Test
    void completedOrdersCannotBeCompensatedFromTheMenu() throws IOException {
        String output = run("1", "Shinji", "EVA-01", "30", "SHAFT-1", "9", "1", "0");
        assertTrue(output.contains("cannot be compensated"));
    }
}
