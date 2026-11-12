# Gamble Hub (Fabric, Minecraft 26.3)

Client-side mod. Press **Right Shift** (rebind in Options > Controls > Gamble Hub) to open the hub.

## Build
Needs JDK 25+.

    gradle wrapper --gradle-version 9.7.1     # once, if you have no wrapper yet
    ./gradlew build

The jar is in `build/libs/gamble-hub-1.0.0.jar`. Put it in `.minecraft/mods` with Fabric Loader 0.19.3+ and Fabric API for 26.3.

If Gradle can't find the Fabric API version, check the exact string for 26.3 at https://modrinth.com/mod/fabric-api/versions and edit `fabric_api_version` in gradle.properties.

## Where to fix things if the game updates
- `Draw.java`  - every call into Minecraft's drawing API.
- `Gui.java`   - opening screens.
- `Chat.java`  - the chat patterns used to detect payments and balance.
