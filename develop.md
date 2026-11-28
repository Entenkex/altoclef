# Development Guide

## [Javadocs](https://gaucho-matrero.github.io/altoclef/)

## Requirements

- **JDK 21** to run Gradle and compile the mod (Gradle 8.8 does not run on newer JDKs such as 25).
  The jar for older Minecraft versions is still downgraded to Java 17 / 8 automatically.
  If your default `java` is newer, the build fails right away with just the Java version as error
  (e.g. `What went wrong: 25.0.5.1`). Fix it once per machine by pinning Gradle to JDK 21 in your user
  `gradle.properties` (see [Memory](#memory) for where that file is):
  ```properties
  # Linux
  org.gradle.java.home=/usr/lib/jvm/java-21-openjdk-amd64
  # Windows (forward slashes or escaped backslashes)
  org.gradle.java.home=C:/Program Files/Eclipse Adoptium/jdk-21.0.x-hotspot
  ```
  Alternatively set `JAVA_HOME` for a single command (see [Command line](#get-it-running-command-line)).
  In IntelliJ, set the Gradle JVM to 21 instead (see below).
- **JDK 8** installed as well. It is only used as Gradle toolchain to build the preprocessor
  plugin (see below); nothing of it ends up in the mod. Gradle finds it automatically in the usual
  install locations:
  - Linux: `sudo apt install openjdk-8-jdk-headless` (lands in `/usr/lib/jvm`)
  - Windows: install e.g. [Eclipse Temurin 8](https://adoptium.net/temurin/releases/?version=8) (JDK, `.msi`)
  - If Gradle still doesn't find it ("Cannot find a Java installation ... languageVersion=8"), add its path in
    your user `gradle.properties` (see [Memory](#memory) for where that file is):
    `org.gradle.java.installations.paths=C:/Program Files/Eclipse Adoptium/jdk-8.0.xxx-hotspot`
- **Git submodules**: clone with `git clone --recurse-submodules ...`, or run
  `git submodule update --init` in an existing clone.
- **RAM**: configuring all 12 Minecraft versions is heavy. See [Memory](#memory) below.

### Preprocessor plugin (git submodule)

The [ReplayMod preprocessor](https://github.com/ReplayMod/preprocessor) generates the sources for every
Minecraft version from the main project (`versions/mainProject`, currently 1.21.1): it switches
`//#if MC >= ...` blocks, remaps names between Yarn mappings (plus `versions/mapping-*.txt`) and applies
`@Pattern` helpers from `adris.altoclef.multiversion`.

JitPack no longer serves the pinned build (commit `c2041a3`), so the plugin lives as git submodule in
`gradle/preprocessor` and is built from source via `includeBuild` in `settings.gradle.kts`.
To change the plugin version, check out another commit in the submodule and commit the submodule pointer.

## Get it Running (IDE)

### James Green's setup guide!

[![James Green's Intellij Setup video on YouTube](https://img.youtube.com/vi/zZ1upxZ43Sg/0.jpg)](https://www.youtube.com/watch?v=zZ1upxZ43Sg)

### Text tutorial

Clone project (with submodules, see [Requirements](#requirements)) and import. I'd suggest using JetBrain's IntelliJ
to import the project. Make sure you're using JDK 21 in your Project & Gradle Settings!
(The screenshots below are older and still show JDK 16.)

1) Open Intellij
2)

Click `File > New > Project from Version Control...` ![image](https://user-images.githubusercontent.com/13367955/146222866-42fa307b-016e-40a6-98bc-6e2428cde2dc.png)

3) Copy + Paste the altoclef clone URL and Clone (find
   here: )![image](https://user-images.githubusercontent.com/13367955/146223264-0cc436c0-4c08-4adc-b948-0ca3da4fbd6f.png)
4) Go to `File > Settings`, search `Gradle` and make sure your Gradle JVM is set to a JDK that's version 21 (IntelliJ
   lets you download open source JDKs, any of those should be
   fine) ![image](https://user-images.githubusercontent.com/13367955/146223463-2cfe8671-5504-430f-93d4-bb5312b2b540.png)
5) Go to `File > Project Structure`, then under `Project Settings/Project` make sure "Project SDK" is set to version
   21 ![image](https://user-images.githubusercontent.com/13367955/146223634-dc4d9eb3-293a-4e70-b5fa-29f44145e02c.png)
7) On the right side of the screen open the gradle tab and navigate to `Tasks/fabric/runClient`.
   Click `runClient` ![image](https://user-images.githubusercontent.com/13367955/146223786-243c63e9-790f-48d7-b627-4e9191a84f22.png)

If the gradle tab doesn't exist, try `View > Tool Windows > Gradle`

## Get it Running (Command line)

1) Git Clone project with submodules: `git clone --recurse-submodules <url>`
2) `cd` into cloned local repo
3) `chmod +x gradlew` (skip this step if you are on windows)
4) Build or start the game, e.g. for 1.21.1 (see the version filter below for what `-P...` does):

Linux / macOS:

```bash
./gradlew :1.21.1:runClient -Paltoclef.versions=1.21.1
# if your default java is not 21:
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :1.21.1:runClient -Paltoclef.versions=1.21.1
```

Windows (PowerShell, in the repo folder):

```powershell
.\gradlew.bat :1.21.1:runClient "-Paltoclef.versions=1.21.1"
# if your default java is not 21 (only for this window):
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.x-hotspot"
.\gradlew.bat :1.21.1:runClient "-Paltoclef.versions=1.21.1"
```

Windows (cmd):

```bat
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.x-hotspot
gradlew.bat :1.21.1:runClient -Paltoclef.versions=1.21.1
```

In PowerShell, keep the quotes around `-Paltoclef.versions=...`: without them PowerShell may split the
argument at the dot. All other commands in this guide work the same on Windows with `.\gradlew.bat`
instead of `./gradlew`.

The game started by `runClient` uses `versions/<version>/run/` as its `.minecraft` folder, so the AltoClef
settings end up in `versions/<version>/run/altoclef/`.

The first run downloads Gradle, Minecraft, mappings and Fabric API for every version and sets them up.
This takes a long time (15+ minutes) and several GB in the Gradle user folder (`~/.gradle`, on Windows
`%USERPROFILE%\.gradle`); later runs use the cache.

### Building only some versions (version filter)

By default all 12 versions are configured, even if you only build or test one: Gradle sets up Minecraft,
mappings and remapped Fabric API for each of them, which costs a lot of time and RAM. For local development pass
`-Paltoclef.versions=<versions>` (comma separated). `-P` sets a Gradle project property; `altoclef.versions`
is read in `settings.gradle.kts` and decides which version projects are included. It is optional: without it
the command does the same, just slower and with much more memory.

```bash
./gradlew :1.21.1:test -Paltoclef.versions=1.21.1
./gradlew :1.21.1:runClient -Paltoclef.versions=1.21.1
./gradlew :1.20.4:build -Paltoclef.versions=1.20.4
```

Included are the requested versions plus every version between them and the main project, because the
preprocessor derives each version from its neighbour in the chain. So the filter saves the most for versions
close to 1.21.1; for 1.16.5 the whole chain is still needed. Without the property everything is built (CI, releases).
When adding a version, update both lists: `allVersions` in `settings.gradle.kts` and `versionChain` in `root.gradle.kts`.

## Tests

Unit tests live in `src/test/java` and use JUnit 5. They are preprocessed like the main sources, so they run
for every version. Keep them to pure logic that works without a running Minecraft client.

```bash
./gradlew :1.21.1:test -Paltoclef.versions=1.21.1   # quick (seconds once cached)
./gradlew :1.16.5:test -Paltoclef.versions=1.16.5   # oldest version, needs the whole chain
```

Reports: `versions/<version>/build/reports/tests/test/index.html`.

## Decision log and flight recorder

`adris.altoclef.diagnostics` writes `altoclef/logs/<session>.jsonl` (in `versions/<version>/run/` for `runClient`)
and dumps on death / thrashing / `@dump`. The task system reports to it through `tasksystem.TaskSystemListener`
(chain priorities per tick, task start/stop/interrupt), so the task code doesn't depend on the log.
The pure parts (`DecisionLog`, `FlightRecorder`, `ThrashDetector`, `DumpLimiter`, `RunSummary`, `LogDirectory`)
are unit tested. Read a log or dump with:

```bash
python3 scripts/decision_timeline.py versions/1.21.1/run/altoclef/logs/<session>.jsonl --no-tasks
```

Design: [docs/task-architecture.md](docs/task-architecture.md) section 2.6.

## Memory

The project `gradle.properties` allows the Gradle daemon 8 GB heap and parallel builds. On machines with
little RAM (or little swap) this can freeze the whole system while all versions are configured.
Override it for your machine in the user `gradle.properties` (takes precedence over the project file):
`~/.gradle/gradle.properties` on Linux/macOS, `%USERPROFILE%\.gradle\gradle.properties` on Windows. For example:

```properties
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g -XX:+UseG1GC
org.gradle.parallel=false
org.gradle.workers.max=2
kotlin.daemon.jvmargs=-Xmx1g
```

Also use the version filter above, and stop the daemon when you're done: `./gradlew --stop`.
The forked test JVM is capped at 512 MB in `build.gradle`.

## Modifying Baritone (dev mode)

Alto Clef uses a custom fork of baritone that gives you more control over how baritone works.
If you wish to make edits to that fork you can do so locally if you follow these steps:

1) Clone [The baritone fork](https://github.com/gaucho-matrero/baritone) into the same directory containing `altoclef`.
   For example, if you cloned `altoclef` into your desktop, `baritone` should be in your desktop as well.
2) Run `gradle build` within the fork you just cloned. You may open the folder in an IDE and run the `build` task.
3) There should now be various `.jar` files starting with `baritone` in the following folder: `baritone/build/libs`
4) Now within `altoclef`, pass `-Paltoclef.development` as a parameter when running `gradle build`
   (In IntelliJ, go to the build dropdown -> `Edit Configurations`, then duplicate the `altoclef [build]` configuration.
   In this duplicate, paste `-Paltoclef.development` into the Arguments text field.)
5) When you build and pass `-Paltoclef.development`, Alto Clef should now use the jar file inside
   of your custom `baritone` fork instead of pulling from online. This lets you rapidly test local changes to baritone.

## Task Development Guides

### Task Programming Tutorial Stream

[![More Recent AltoClef Task Programming VOD](https://img.youtube.com/vi/uROEqwyzn3o/0.jpg)](https://www.youtube.com/watch?v=uROEqwyzn3o)

### Old (Post Stream) Tutorial VOD

[![Rough AltoClef Tutorial VOD](https://img.youtube.com/vi/giBjHDZ7HvY/0.jpg)](https://www.youtube.com/watch?v=giBjHDZ7HvY)


