package eva.app;

import eva.facade.CrashPoint;
import eva.facade.InvalidLaunchRequestException;
import eva.facade.LaunchRequest;
import eva.facade.OrderNotCompensableException;
import eva.facade.OrderNotFoundException;
import eva.facade.OrderView;
import eva.facade.SimulatedCrashException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;

public class ConsoleApp {

    private final HangarSystem hangar;
    private final BufferedReader in;
    private final PrintStream out;

    public ConsoleApp(HangarSystem hangar, BufferedReader in, PrintStream out) {
        this.hangar = hangar;
        this.in = in;
        this.out = out;
    }

    public void run() throws IOException {
        boolean running = true;
        while (running) {
            printMenu();
            String choice = prompt("> ");
            if (choice == null) {
                break;
            }
            try {
                running = handle(choice.trim());
            } catch (InvalidLaunchRequestException | OrderNotFoundException | OrderNotCompensableException e) {
                out.println("refused: " + e.getMessage());
            } catch (NumberFormatException e) {
                out.println("that was not a number");
            }
        }
        out.println("hangar closed");
    }

    private boolean handle(String choice) throws IOException {
        switch (choice) {
            case "1" -> scramble(CrashPoint.NONE);
            case "2" -> scramble(CrashPoint.AFTER_A);
            case "3" -> scramble(CrashPoint.AFTER_B);
            case "4" -> resume();
            case "5" -> listOrders();
            case "6" -> inspect();
            case "7" -> setBayPower();
            case "8" -> setShaft();
            case "9" -> compensate();
            case "0" -> {
                return false;
            }
            default -> out.println("unknown option");
        }
        return true;
    }

    private void printMenu() {
        out.println();
        out.println("=== NERV GeoFront launch console ===");
        out.println("1  scramble an EVA (full chain A->B->C)");
        out.println("2  scramble, process crashes after A (pilot locked)");
        out.println("3  scramble, process crashes after B (power charged)");
        out.println("4  resume interrupted launches from disk");
        out.println("5  list all launch orders");
        out.println("6  inspect one order");
        out.println("7  set umbilical bay power (MW) - break step B");
        out.println("8  set shaft condition - break step C");
        out.println("9  manually compensate an order");
        out.println("0  exit");
    }

    private void scramble(CrashPoint crashPoint) throws IOException {
        String pilot = prompt("pilot name: ");
        String chassis = prompt("chassis id (e.g. EVA-01): ");
        double stress = Double.parseDouble(prompt("pilot stress 0-100: ").trim());
        String shaft = prompt("shaft id (e.g. SHAFT-1): ");
        try {
            OrderView view = hangar.launchFacade().scramble(new LaunchRequest(pilot, chassis, stress, shaft), crashPoint);
            out.println(view.summary());
        } catch (SimulatedCrashException e) {
            out.println(e.getMessage() + " - state is on disk, use option 4 or restart the app");
        }
    }

    private void resume() {
        var resumed = hangar.launchFacade().resumeInterrupted();
        if (resumed.isEmpty()) {
            out.println("nothing to resume");
        }
        resumed.forEach(view -> out.println(view.summary()));
    }

    private void listOrders() {
        var views = hangar.controlFacade().listOrders();
        if (views.isEmpty()) {
            out.println("no orders yet");
        }
        views.forEach(view -> out.println(view.summary()));
    }

    private void inspect() throws IOException {
        long id = Long.parseLong(prompt("order id: ").trim());
        out.println(hangar.controlFacade().inspect(id).summary());
    }

    private void setBayPower() throws IOException {
        String chassis = prompt("chassis id: ");
        double mw = Double.parseDouble(prompt("available MW (launch needs 11.0): ").trim());
        hangar.controlFacade().setBayPower(chassis, mw);
        out.println(chassis + " bay supply is now " + hangar.controlFacade().bayPower(chassis) + " MW");
    }

    private void setShaft() throws IOException {
        String shaft = prompt("shaft id: ");
        boolean doorOpen = prompt("blast door open? (y/n): ").trim().equalsIgnoreCase("y");
        boolean debris = prompt("debris in shaft? (y/n): ").trim().equalsIgnoreCase("y");
        hangar.controlFacade().setShaftCondition(shaft, doorOpen, debris);
        out.println(hangar.controlFacade().shaftReport(shaft));
    }

    private void compensate() throws IOException {
        long id = Long.parseLong(prompt("order id: ").trim());
        out.println(hangar.controlFacade().manualCompensate(id).summary());
    }

    private String prompt(String text) throws IOException {
        out.print(text);
        out.flush();
        return in.readLine();
    }
}
