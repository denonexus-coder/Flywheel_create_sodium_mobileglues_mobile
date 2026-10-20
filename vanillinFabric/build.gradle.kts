plugins {
    idea
    java
    `maven-publish`
    id("dev.architectury.loom")
    id("flywheel.subproject")
    id("flywheel.platform")
}

val common = ":common"
val platform = ":fabric"

subproject.init("vanillin-fabric", "vanillin_group", "vanillin_version")

val main = sourceSets.getByName("main")

platform {
    setupLoomRuns()
}

transitiveSourceSets {
    sourceSet(main) {
        compileClasspath(project(platform), "api", "lib", "main")

        bundleFrom(project(common), "vanillin")
    }
}

var vanillinVersion = "${property("vanillin_version")}+${property("minecraft_version")}"

if (subproject.buildNumber != null) {
    vanillinVersion += ".build.${subproject.buildNumber}"
}

val replaceProperties = listOf(
    "mod_license",
    "mod_sources",
    "mod_issues",
    "mod_homepage",
    "flywheel_id",
    "vanillin_id",
    "vanillin_name",
    "vanillin_description",
    "minecraft_semver_version_range",
    "flywheel_semver_version_range",
    "fabric_api_version_range",
).associateWith { property(it) as String }
    .plus("vanillin_version" to vanillinVersion)

tasks.withType<ProcessResources>().configureEach {
    inputs.properties(replaceProperties)

    filesMatching(listOf("fabric.mod.json")) {
        expand(replaceProperties)
    }
}

jarSets {
    mainSet.publishWithRemappedSources {
        artifactId = "vanillin-fabric-${property("artifact_minecraft_version")}"
    }
}

defaultPackageInfos {
    sources(main)
}

loom {
    mixin {
        useLegacyMixinAp = true
        add(main, "vanillin.refmap.json")
    }
}

dependencies {
    modImplementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
    modApi("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")

    modCompileOnly("maven.modrinth:sodium:${property("sodium_version")}")

    compileOnly(project(path = common, configuration = "vanillinClasses"))
    compileOnly(project(path = common, configuration = "vanillinResources"))
    // The visuals read MobileOptions.hideOffscreenInstances, which lives in the backend source
    // set. Compile-only: at runtime these classes come from the Flywheel jar this one is
    // bundled with, so nothing extra is shipped.
    compileOnly(project(path = common, configuration = "backendClasses"))

    // JiJ flywheel proper
    include(project(path = platform, configuration = "flywheelRemap"))
    runtimeOnly(project(path = platform, configuration = "flywheelDev"))
}
