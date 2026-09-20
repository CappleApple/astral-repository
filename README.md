# Astral Repository — Minecraft 26.2 NeoForge

A magical storage and automation network: place crystals around a workshop, teach a bookshelf the products you want, and let the network find storage, route resources, and coordinate ordinary workstations.

This branch builds Astral Repository **1.11.8** for **Minecraft 26.2**, **NeoForge 26.2.0.88 or newer within Minecraft 26.2**, and **Java 25**. Install the matching JAR on both the client and server. No additional mod is required.

## Building

Use Java 25 to run the included Gradle wrapper:

```powershell
.\gradlew.bat test build
```

On Linux or macOS, use `./gradlew test build`.

The installable JAR is `build/libs/astral_repository-26.2-neoforge-1.11.8.jar`.

## Source and validation

The Java implementation and tests are in `src/main/java` and `src/test/java`. Resources are in `src/main/resources`.

The project contains 149 unit tests. Development runtime checks are available as `runServer -PsmokeTest` and `runClientSmoke -PclientSmoke`; the client fixture is compiled only when requested. Test fixtures are excluded from the release JAR. Server runs require accepting Minecraft's EULA in `run-server/eula.txt`.

See the [port validation record on main](https://github.com/CappleApple/astral-repository/blob/main/ports/VALIDATION.md) for the completed server, loot, recipe, and client-rendering checks and their limits. The [main branch](https://github.com/CappleApple/astral-repository) retains the original Minecraft 1.21.1 NeoForge implementation and usage documentation.

## Optional integrations

Curios, JEI, Jade, and the other integration hooks remain optional. Third-party API compilation is separate from runtime compatibility; complete optional-mod packs were not tested. No public Patchouli or EMI release for this Minecraft version was available during the port, so those hooks remain inactive without a matching release. The Field Guide requires Patchouli.

## License

[CC BY-NC-SA 4.0 with additional permission for Minecraft modpacks and servers](LICENSE). The additional permission allows use in commercial or monetized modpacks and servers; standalone paid distribution is excluded. See `LICENSE` for the complete terms supplied with the project.
