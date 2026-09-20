# Optional runtime validation

Optional integrations stay out of the default runtime classpath. To run the installed-mod gate, place the required JARs in the ignored `test-libs/` directory, then run:

```powershell
.\gradlew.bat runGameTestServer -PcompatTest --no-daemon
```

The flag adds `test-libs/*.jar` to the development runtime, selects `run-gametest-compat/`, and requires Create and Stacks Not Slots to be loaded. It does not add their classes to Astral Repository's compiled API or bundle their JARs in the release artifact. The run is a headless GameTest server.

Without this flag, optional tests return immediately when their mod is absent. A successful default run therefore does not validate optional APIs.

## Validated artifacts

The September 14, 2026 gate used Minecraft 1.21.1, NeoForge 21.1.244, and Java 21. The final run completed all 34 registered GameTests successfully with these optional mods loaded:

| JAR | Version | SHA-256 |
| --- | --- | --- |
| `create-1.21.1-6.0.10.jar` | Create 6.0.10 | `ef87fe5709f1ba1f5b8bb20a2925b5afb4669e178fd6d8bf10c167759eefe37a` |
| `stacksnotslots-1.0.jar` | Stacks Not Slots 1.0 | `ffc4b17989aa5a74f27acd24a7828154992cea37f48f696fb619227a903e676c` |

Create's JAR supplied its embedded dependencies: Registrate `MC1.21-1.3.0+67`, Flywheel `1.0.6`, and Ponder `1.0.82+mc1.21.1`. No separate dependency JARs were needed for this gate.

## What the optional tests exercise

- **Create pressing:** the crafting service inserts an iron ingot into a real depot below a Mechanical Press. Create performs its normal pressing recipe, and the service retrieves `create:iron_sheet` into the chest.
- **Create milling:** the crafting service supplies a real Millstone with cobblestone and retrieves the gravel produced by Create's milling recipe.
- **Create stress:** a real Creative Motor initializes its own kinetic network. The adapter observes 262,144 spare stress units, shares acquired capacity between provider instances, and returns it on release. Simulation leaves available capacity unchanged.
- **Stacks Not Slots:** the test invokes the installed public `exactCapacityCost` API, compares the adapter's exact fraction, and inserts three items with a maximum stack size of three into 64 units of Astral storage. A fourth item is rejected.

The press and millstone fixtures supply 256 RPM through Create's public `setSpeed` method. Create itself performs the processing; the fixtures do not synthesize recipe outputs. These tests validate machine inventory boundaries, scheduling, extraction, and reservation release. They do not test a player's complete mechanical drive layout or client rendering.

The stress test uses an actual Creative Motor without injecting stress-network values. Its measured capacity is specific to the tested Create version and default configuration.

The final captured gate output is `build/revision-final-optional.log`. Inspect `run-gametest-compat/logs/latest.log` for the loaded-mod list, `ASTRAL_COMPAT` markers, GameTest results, recipe-load errors, and adapter warnings. The final gate had no recipe parsing errors or unsupported Create API warnings. Deliberate transfer-failure fixtures emitted expected quarantine errors and passed their recovery assertions.

