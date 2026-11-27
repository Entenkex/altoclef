pluginManagement {
    // ReplayMod preprocessor, pinned as git submodule (commit c2041a3).
    // JitPack no longer serves that build, so it is built from source.
    // After cloning run: git submodule update --init
    includeBuild("gradle/preprocessor")

    repositories {
        maven("https://maven.fabricmc.net")
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
}


rootProject.name = "altoclef"
rootProject.buildFileName = "root.gradle.kts"

// Preprocessor chain, newest first. Keep in sync with the node list in root.gradle.kts.
val allVersions = listOf(
    "1.21.1",
    "1.21",
    "1.20.6",
    "1.20.5",
    "1.20.4",
    "1.20.2",
    "1.20.1",
    "1.19.4",
    "1.18.2",
    "1.18",
    "1.17.1",
    "1.16.5"
)

// Optional version filter for local development, e.g.
//   ./gradlew :1.21.1:test -Paltoclef.versions=1.21.1
//   ./gradlew :1.16.5:test -Paltoclef.versions=1.16.5
// Every version in the chain is configured by Loom (Minecraft setup, remapping), which costs
// a lot of RAM and time. With the filter only the requested versions plus the versions
// between them and the main project (versions/mainProject) are included, because the
// preprocessor derives each version's sources from its neighbour in the chain.
// Without the property all versions are included (CI, release builds).
val requestedVersions = providers.gradleProperty("altoclef.versions").orNull
    ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
val includedVersions = if (requestedVersions.isNullOrEmpty()) {
    allVersions
} else {
    val mainVersion = file("versions/mainProject").readText().trim()
    val unknown = (requestedVersions + mainVersion).filter { it !in allVersions }
    require(unknown.isEmpty()) { "Unknown version(s) in altoclef.versions / mainProject: $unknown. Known: $allVersions" }
    val indices = (requestedVersions + mainVersion).map { allVersions.indexOf(it) }
    allVersions.subList(indices.min(), indices.max() + 1)
}

includedVersions.forEach { version ->
    include(":$version")
    project(":$version").apply {
        projectDir = file("versions/$version")
        buildFileName = "../../build.gradle"
        name = version
    }
}