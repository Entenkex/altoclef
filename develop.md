# Development Guide

## [Javadocs](https://gaucho-matrero.github.io/altoclef/)

## Requirements

- **JDK 21** to run Gradle and compile the mod (Gradle 8.8 does not run on newer JDKs such as 25).
  The jar for older Minecraft versions is still downgraded to Java 17 / 8 automatically.
- **JDK 8** installed as well. It is only used as Gradle toolchain to build the preprocessor
  plugin (see below); nothing of it ends up in the mod. Gradle finds it automatically
  (e.g. `/usr/lib/jvm` on Linux, `sudo apt install openjdk-8-jdk-headless`).
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
4) `./gradlew build` or `./gradlew :1.21.1:runClient`
   (if your default `java` is not 21, prefix the command with `JAVA_HOME=/path/to/jdk-21`)

The first run downloads Gradle, Minecraft, mappings and Fabric API for every version and sets them up.
This takes a long time (15+ minutes) and several GB in `~/.gradle`; later runs use the cache.

### Building only some versions (version filter)

By default all 12 versions are configured, even if you only build or test one. For local development pass
`-Paltoclef.versions=<versions>` (comma separated):

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

## Memory

The project `gradle.properties` allows the Gradle daemon 8 GB heap and parallel builds. On machines with
little RAM (or little swap) this can freeze the whole system while all versions are configured.
Override it for your machine in `~/.gradle/gradle.properties` (takes precedence over the project file), e.g.:

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


