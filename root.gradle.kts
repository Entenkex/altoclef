plugins {
    id("fabric-loom") version "1.7-SNAPSHOT" apply false
    id("com.replaymod.preprocess")
}

subprojects {
    repositories {
        //mavenLocal()
        mavenCentral()
        maven("https://libraries.minecraft.net/")
        maven("https://repo.spongepowered.org/repository/maven-public/")
        maven("https://github.com/jitsi/jitsi-maven-repository/raw/master/releases/")
        maven("https://maven.fabricmc.net/")
        maven("https://jitpack.io")
    }
}

// Preprocessor chain, newest first. Keep in sync with allVersions in settings.gradle.kts.
// Third value: extra mappings for the link to the next (older) version.
val versionChain = listOf(
    Triple("1.21.1", 12101, null),
    Triple("1.21", 12100, null),
    Triple("1.20.6", 12006, null),
    Triple("1.20.5", 12005, null),
    Triple("1.20.4", 12004, null),
    Triple("1.20.2", 12002, null),
    Triple("1.20.1", 12001, null),
    Triple("1.19.4", 11904, "versions/mapping-1.19.4-1.18.2.txt"),
    Triple("1.18.2", 11802, null),
    Triple("1.18", 11800, "versions/mapping-1.18.2-1.17.1.txt"),
    Triple("1.17.1", 11701, "versions/mapping-1.17.1-1.16.5.txt"),
    Triple<String, Int, String?>("1.16.5", 11605, null),
)

preprocess {
    // Only versions included by settings.gradle.kts (see -Paltoclef.versions) become nodes.
    // The included versions are always a contiguous part of the chain, so neighbours stay linked.
    val included = versionChain.filter { (name, _, _) -> findProject(":$name") != null }
    val nodes = included.map { (name, mcVersion, _) -> createNode(name, mcVersion, "yarn") }

    included.zip(nodes).zipWithNext { (newer, newerNode), (_, olderNode) ->
        val extraMappings = newer.third
        if (extraMappings != null) {
            newerNode.link(olderNode, file(extraMappings))
        } else {
            newerNode.link(olderNode)
        }
    }
}