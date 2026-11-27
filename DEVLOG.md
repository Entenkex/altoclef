# Dev log: what went wrong

History of problems we ran into while developing, how we found them and how they were fixed.
Newest entries at the top. Template:

```markdown
### YYYY-MM-DD: Short title
**Found:** what happened / how it showed up
**Cause:** why
**Fix:** what we changed (files, branch/commit)
```

Open findings that are known but not fixed yet are listed at the end.

---

## 2026-11-27

### Settings warnings repeated after every change
**Found:** In game, every `@setting ...` change printed the same profile warnings again
(e.g. a clamped value from the file).
**Cause:** Every change writes the profile and reloads it; the reload reports all warnings again.
**Fix:** Reloads triggered by commands only show warnings that are new since the last load; already shown
ones go to the log only. Startup, `@reload_settings` and profile switches still show everything
(`SettingsService.load(boolean)`, branch `feature/settings`).

### Broken YAML gave a multi-line parser error in chat
**Found:** A broken profile file (`settings: [`) printed the full multi-line SnakeYAML message into chat.
**Fix:** `ProfileStore.shortError` reduces it to one line with position, e.g.
`expected the node content, but found '<stream end>' (line 1, column 12)`. Covered by `ProfileStoreTest`.

### NullPointerException while loading settings at startup
**Found:** In-game test with a hand-edited profile: only the first warning appeared, `settings_reference.yml`
was not regenerated, and the log showed `Caught error loading resourcepacks` with a
`NullPointerException` in `Debug.logWarning`.
**Cause:** Warnings during the very first load are printed before `AltoClef` has stored its settings, and
`Debug.logWarning` / `getLogPrefix` called `getModSettings()` without a null check. The exception aborted the rest
of loading (more warnings, reload listeners) and Minecraft swallowed it as a resource pack error.
The first test run had no warnings, so it didn't show up there.
**Fix:** `Debug` is null-safe; `SettingsManager` catches failures of the warning output and of reload listeners, so
loading always completes. Regression test `failingWarningOutputDoesNotAbortLoading`.

### Unreadable error texts for invalid setting values
**Found:** `@setting beatMinecraft.targetEyes viele` showed Jackson internals
("Cannot deserialize value of type `java.lang.Integer` ..."); for a rejected value it said "using default",
although nothing was saved.
**Fix:** Short reasons per type ("expected a whole number", "expected true or false",
"expected one of ..."), rejected input ends with "- not saved".

### Stack traces in chat for simple typos
**Found:** Every wrong input to the new commands printed a full stack trace into chat.
**Cause:** Commands used `Debug.logError`, which always appends the stack trace.
**Fix:** `Debug.logUserError` (red line, no stack trace) for user input; new setting `core.showStackTracesInChat`
(default off) decides whether real errors show their stack trace in chat. The log always contains it.

### Settings listings flooded the chat
**Found:** `@setting core` sent one chat message per setting (45 messages).
**Fix:** Listings are sent as one formatted, colored message; long values are cut off (`Debug.logLines`).

### Class name clash `AltoClefSettings`
**Found:** While migrating, `BotBehaviour` already used `baritone.altoclef.AltoClefSettings`.
**Fix:** The new settings entry point is called `SettingsService` instead.

### Gradle fails with only "25.0.5.1" as error
**Found:** `./gradlew ...` without `JAVA_HOME` failed with `What went wrong: 25.0.5.1`.
**Cause:** The system default Java is 25; Gradle 8.8 can't run on it.
**Fix:** Pin Gradle to JDK 21 per machine with `org.gradle.java.home` in the user `gradle.properties`
(documented in `develop.md`).

### System freeze while building, hard reset needed
**Found:** Running the tests for 1.16.5 right after 1.21.1 froze the whole laptop (13 GiB RAM, 512 MB swap);
only the power button helped. The journal ended without an OOM kill (memory thrashing).
**Cause:** Project `gradle.properties` allows 8 GB heap and parallel builds; Gradle configures all 12 Loom
projects (Minecraft setup, remapping) even when only one version is built, plus Kotlin daemon and test JVM.
**Fix:** Local limits in the user `gradle.properties` (4 GB heap, no parallel builds, 2 workers),
test JVM capped at 512 MB, version filter `-Paltoclef.versions=...` so only the needed part of the
chain is configured, `./gradlew --stop` after builds. A RAM guard script was used for long runs.

### Minecraft download failed during first setup
**Found:** First build stopped with `Failed to setup Minecraft ... Failed download after 3 attempts`.
**Cause:** Network hiccup while Loom downloaded the versions.
**Fix:** Ran it again; already downloaded parts come from the cache.

### Preprocessor plugin needs a Java 8 toolchain
**Found:** After building the preprocessor from source: `Cannot find a Java installation ... languageVersion=8`.
**Cause:** The plugin's own build file requires a Java 8 toolchain.
**Fix:** Install JDK 8 (only used to build the plugin); CI installs JDK 8 and passes it via
`-Porg.gradle.java.installations.fromEnv=JAVA_HOME_8_X64`.

### Build broken for everyone: preprocessor no longer on JitPack
**Found:** The very first build failed: plugin `com.replaymod.preprocess` version `c2041a3` could not be resolved.
JitPack reports the build as ok but serves no files (404). The upstream fork (MiranCZ) uses the same version.
**Fix:** The preprocessor is a git submodule in `gradle/preprocessor` pinned to `c2041a3` and is built from source
via `includeBuild` in `settings.gradle.kts`.

### Submodule pinned to the wrong commit before committing
**Found:** `git submodule add` recorded the plugin's current `master` (`dc13fe6`); checking out `c2041a3`
afterwards was not staged yet.
**Fix:** `git add gradle/preprocessor` before committing; checked with `git diff --cached --submodule=short`.

### CI could not upload artifacts
**Found:** While updating the workflow: `actions/upload-artifact@v2` is disabled by GitHub,
`checkout`/`setup-java` v2 run on deprecated Node versions.
**Fix:** All actions on v4, JDK distribution `temurin` instead of `adopt`.

---

## Known, not fixed yet

- **2026-11-27:** `CraftingRecipeTracker` fills recipe slots linearly and ignores the recipe width, so narrow shaped
  recipes (1x2, 2x2 in a 3x3 grid) end up in the wrong slots; it also keeps only the first item per ingredient.
  To be solved with the unified recipe format (TODO step 2). The new model places shapes correctly
  (`RecipeShape.Shaped.toGrid`); the old tracker stays in use until step 2.3.
- **2026-11-27:** `@recipes compare` (1.21.1): 295 of 306 hand-written `TaskCatalogue` recipes match the game.
  Wrong ones: `bamboo_button`/`bamboo_planks` (catalogue name `bamboo_block` does not exist → empty slots, can
  never be crafted), `brick_slab`/`brick_stairs`/`brick_wall` (use `brick` instead of `bricks`; wall also wrong
  shape and count 4 instead of 6), `carrot_on_a_stick`/`warped_fungus_on_a_stick` (side by side instead of
  diagonal), `lead` (gives 2, not 1), `quartz_pillar` (gives 2, not 4), `red_nether_bricks` (needs nether brick
  + nether wart, not 4 nether wart). `suspicious_stew` is a special recipe (not imported, expected).
  Fixed by taking catalogue recipes from the registry (TODO 2.4).
- **2026-11-27:** `ItemPriorityCalculator` checks `count > max`, `DistancePriorityCalculator` checks `count >= max`
  (pinned by `PriorityCalculatorTest`). To be cleaned up with the new priority system (TODO step 3).
