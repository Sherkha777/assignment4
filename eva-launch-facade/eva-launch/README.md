# Assignment 4 - Facade over a three-stage chain: EVA launch sequence

Amangeldin Sherkhan, group SE-2536
Domain: Neon Genesis Evangelion / Gundam launch sequence, scrambling an EVA unit to intercept an Angel (own proposed domain, "Option 2").

## 1. Run it

You need JDK 17+ and Maven. Everything below is one command.

| What | Command |
| --- | --- |
| Build, run tests, make coverage report | `mvn verify` |
| Run the demo console | `mvn -q compile exec:java` (or `./run.sh`, `run.bat` on Windows) |
| Reset the persisted store | `./reset.sh` (or `reset.bat`), it just deletes the `data/` folder |
| Coverage report | `target/site/jacoco/index.html` after `mvn verify` |

The demo keeps its state in `data/` (H2 embedded database files, one per subsystem plus one for the chain). Close the app, start it again, and everything is still there. Run `./reset.sh` before a fresh demo run.

Every subsystem also has its own `main` so it can be shown alone, without the facade:
`eva.pilot.PilotStandalone`, `eva.power.PowerStandalone`, `eva.catapult.CatapultStandalone`.

## 2. The domain

Scrambling an EVA is one logical operation for the client ("launch Shinji in EVA-01 through SHAFT-1"), but behind it there are three unrelated subsystems:

| | Subsystem | Classes | Status values |
| --- | --- | --- | --- |
| A | Pilot Sync and Cage Lock (`eva.pilot`) | `EntryPlugLauncher`, `HarmonicsThresholdCalculator` (business logic), `PilotStatusRepository` (persistence gateway), `PilotSession` (entity) | `STANDBY -> SYNC_LOCKED -> EJECTED` (plus `SYNC_LOCKED -> STANDBY`) |
| B | Umbilical Power and Battery Matrix (`eva.power`) | `PowerGridRouter`, `CapacitorDischargeRule` (business logic), `PowerLogRepository` (persistence gateway), `PowerLine` (entity) | `DISCONNECTED -> CHARGED -> DRAINED` |
| C | GeoFront Linear Catapult (`eva.catapult`) | `CatapultPneumatics`, `SiloPathClearanceRule` (business logic), `LaunchTelemetryRepository` (persistence gateway), `CatapultRun` (entity), `ShaftCondition` | `RESTING -> PRESSURIZED -> LAUNCHED` (plus `PRESSURIZED -> RESTING`) |

12 domain classes in total. Each entity checks its own legal transitions through its status enum (`canMoveTo`), so a subsystem refuses illegal moves even if the facade is wrong. No subsystem imports another one. The only shared thing is `eva.store.Db`, a tiny helper that opens an H2 file, which is infrastructure and not a subsystem.

What each step does:

- **A** inserts the entry plug: computes the sync ratio from the pilot stress, rejects the pilot below 40%, refuses a chassis that is already reserved, then persists the session as `SYNC_LOCKED`.
- **B** charges the umbilical line: the capacitor rule compares the bay supply with the demand plus a 10% safety margin. If the supply is not enough the capacitor trips, the line stays `DISCONNECTED` and a `PowerFailureException` is thrown.
- **C** fires the catapult: pressurizes, then checks the shaft (blast door and debris). If blocked, the catapult vents back to `RESTING` and throws `CatapultFailureException`, otherwise it becomes `LAUNCHED`.

The failures are real persisted facts, not flags in the facade: the bay supply (MW) is a row in the power store and the shaft condition (door, debris) is a row in the catapult store. The operator changes them through the admin facade and the next launch really fails.

## 3. The facades

- `EvaLaunchFacade` (client workflow): `scramble(request)` runs A -> B -> C, writes the chain status to disk after each step, and compensates when a step fails. `resumeInterrupted()` looks at what is on disk after a restart and finishes or undoes unfinished orders.
- `HangarControlFacade` (admin / inspection): `listOrders`, `inspect`, `manualCompensate`, plus the controls for breaking things on purpose (`setBayPower`, `setShaftCondition`, `shaftReport`).
- `ChainCompensator` is the shared undo logic used by both facades, and `OrderViewAssembler` builds the read-only `OrderView` so clients never see subsystem entities.

The sequencing logic is in `EvaLaunchFacade.scramble`/`drive`/`resumeInterrupted`, it is not one-line delegation.

## 4. Durable chain progress and recovery

Each launch has a `LaunchOrder` row with a `ChainStatus`: `STARTED`, `PENDING_B`, `PENDING_C`, `COMPLETED`, `COMPENSATING`, `COMPENSATED`, `REJECTED`. It is updated after every step.

After a restart, `resumeInterrupted()` does not trust the order row alone. It reads the actual state from each subsystem's store:

| Order on disk | What the facade checks | What it does |
| --- | --- | --- |
| `STARTED` | is a pilot for this chassis locked? | if yes undo A, if no mark `REJECTED` |
| `PENDING_B` | pilot really `SYNC_LOCKED`? | yes: continue with B then C, no: compensate |
| `PENDING_C` | pilot `SYNC_LOCKED` and power line `CHARGED`? | yes: continue with C, no: compensate |
| `COMPENSATING` | what is still charged / locked | finish the compensation |

Compensation is also based on persisted state, so it is safe to run twice:

1. If the power line is `CHARGED`, drain it (`B:DRAINED`).
2. If the pilot is still `SYNC_LOCKED`: when power had been up, emergency eject (`A:EJECTED`), otherwise just unlatch back to `STANDBY` (`A:STANDBY`).

The undo steps are recorded in the `trail` column of the order, for example `B:DRAINED > A:EJECTED`.

### The two mandatory compensation depths

- **B fails after A committed:** bay supply too low, capacitor trips. The facade compensates A only. Result: pilot `STANDBY`, power line `DISCONNECTED`, catapult untouched, trail `A:STANDBY`.
- **C fails after A and B committed:** blast door sealed or debris in the shaft. The facade compensates B and then A. Result: power `DRAINED`, pilot `EJECTED`, catapult `RESTING`, trail `B:DRAINED > A:EJECTED`.

### Other failures (3.6)

- Pre-chain: pilot stress too high (sync ratio under 40%) or chassis already reserved. Nothing is committed in A, B or C, order ends as `REJECTED` with the reason.
- Invalid input (blank names, stress outside 0-100): `InvalidLaunchRequestException`, nothing is stored.
- Own exceptions: `PilotRejectedException`, `ChassisUnavailableException`, `PowerFailureException`, `CatapultFailureException`, `InvalidLaunchRequestException`, `OrderNotFoundException`, `OrderNotCompensableException`, `StoreException`.

## 5. Demo script (what to do live)

Start with `./reset.sh`, then `mvn -q compile exec:java`.

1. Option 1: Shinji / EVA-01 / stress 30 / SHAFT-1. The chain ends `COMPLETED`.
2. **Compensation depth 1:** option 7, set EVA-02 power to 4 MW. Option 1: Rei / EVA-02 / 20 / SHAFT-2. Result `COMPENSATED`, pilot `STANDBY`, trail `A:STANDBY`.
3. **Compensation depth 2:** option 8, SHAFT-3, door open `n` (or debris `y`). Option 1: Asuka / EVA-03 / 10 / SHAFT-3. Result `COMPENSATED`, trail `B:DRAINED > A:EJECTED`.
4. **Pre-chain failure:** option 1 with stress 80. Result `REJECTED`, nothing touched.
5. **Restart persistence:** option 3 (crash after B), then exit with 0, start the app again, option 5 shows `PENDING_C`, option 4 resumes and finishes it. Option 2 does the same for a crash after A.
6. Option 9 compensates a half-finished order by hand, option 6 inspects any order.

## 6. Principle of Least Knowledge (before / after)

Without the facade the client has to know every subsystem, the entities they return, and the order and the undo rules:

```java
PilotStatusRepository pilotRepo = new PilotStatusRepository(dir);
EntryPlugLauncher launcher = new EntryPlugLauncher(new HarmonicsThresholdCalculator(), pilotRepo);
PowerGridRouter grid = new PowerGridRouter(new CapacitorDischargeRule(), new PowerLogRepository(dir));
CatapultPneumatics catapult = new CatapultPneumatics(new SiloPathClearanceRule(), new LaunchTelemetryRepository(dir));

PilotSession session = launcher.insertPlug("Shinji", "EVA-01", 30);
try {
    PowerLine line = grid.charge("EVA-01", 10.0);
    try {
        CatapultRun run = catapult.fire("SHAFT-1", "EVA-01");
    } catch (CatapultFailureException e) {
        grid.drain(line.id());
        launcher.emergencyEject(session.id());
    }
} catch (PowerFailureException e) {
    launcher.unlatch(session.id());
}
```

The client talks to about ten classes, calls through returned objects (`line.id()`, `session.id()`), and has to remember the undo rules itself. Nothing is written to disk about progress either.

With the facade the client has one friend:

```java
EvaLaunchFacade facade = hangar.launchFacade();
OrderView result = facade.scramble(new LaunchRequest("Shinji", "EVA-01", 30, "SHAFT-1"));
```

`ConsoleApp` only knows the two facades and `OrderView`, it never sees `PilotSession`, `PowerLine` or `CatapultRun`. The facade itself only talks to its own fields (the three subsystem entry classes and the order repository) and to objects it creates, it does not reach through them.

## 7. Facade vs Adapter (reflection)

An Adapter exists to make one existing interface look like a different one that the client expects. It is about compatibility: one thing in, one thing out, the same job. Here nothing is incompatible. `EntryPlugLauncher`, `PowerGridRouter` and `CatapultPneumatics` all have clean interfaces already, and the client does not expect a different shape from any of them.

The real problem is that one client intent, "scramble this EVA", is spread over three independent subsystems that must be called in the right order, where the second and third step can fail after the first ones have already been committed to disk. That is a coordination and simplification problem. The facade gives one simpler entry point, owns the ordering, and owns the knowledge of which committed parts must be undone (A alone, or B then A). An adapter would translate calls but would never decide that a blocked silo shaft means "drain the capacitors, then eject the plug". Also, an adapter wraps one subsystem, while the facade has to hold all three at the same time.

Intent decides it: Adapter = make this fit, Facade = hide the complexity of a group of things behind one operation.

## 8. Tests and coverage

57 JUnit 5 tests (`mvn test`), each one runs in its own `@TempDir`, so every test starts from a clean persisted state and no test depends on another or on the order (Fast, Independent, Repeatable). All assertions are programmatic (Self-validating).

- `PilotSubsystemTest`, `PowerSubsystemTest`, `CatapultSubsystemTest`: each subsystem alone, including its own transition rules and a close/reopen persistence test.
- `LaunchFacadeTest`: happy path, B-fails-after-A, C-fails-after-A-and-B (debris and sealed door), pre-chain rejection, reserved chassis, invalid input.
- `RecoveryTest`: the persistence-reload tests. The store is closed and reopened mid-chain (after A, after B, half-finished compensation, inconsistent disk state) and the facade has to pick up from what is persisted.
- `HangarControlFacadeTest`: the admin facade, including manual compensation at both depths.
- `ConsoleAppTest`: drives the real menu with scripted input.

Domain-specific testing language: `HangarScenario` lets tests read like sentences:

```java
hangar.shaftIsBlockedByDebris("SHAFT-1");
hangar.pilot("Asuka").inChassis("EVA-02").withStress(20).scramblesThrough("SHAFT-1")
        .endsAs(ChainStatus.COMPENSATED)
        .powerEnds(PowerStatus.DRAINED)
        .pilotEnds(PilotSyncStatus.EJECTED)
        .compensationTrailIs("B:DRAINED > A:EJECTED");
```

The outcome checks read the persisted state straight from the subsystem stores, not from the facade's return value.

Coverage is measured with JaCoCo (`mvn verify` fails below 80% line coverage). Over all three subsystems and both facades plus the console it is 88.7% of lines when everything is counted, including the standalone `main` classes that tests never run. With `Main` and the `*Standalone` classes excluded (as configured in the pom) it is about 92%.

## 9. UML

`docs/class-diagram.png` (also `docs/class-diagram.svg`, source in `docs/uml.dot`). It shows the client, both facades, all three subsystems and their associations, and there are no arrows between the subsystems.

## 10. Git

Commit subsystem by subsystem, each with its tests in the same commit (A + `PilotSubsystemTest`, then B, then C, then the facades + `LaunchFacadeTest`, `RecoveryTest`, then the console), so the history shows tests next to the code they cover.
