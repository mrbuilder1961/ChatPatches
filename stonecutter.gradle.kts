plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.7-fabric"

// each build task builds all of its loader and copies them to build/libs/
fun registerLoaderBuildTask(loader: String) {
    // registers as a Copy task to allow easy copying
    tasks.register<Copy>("build${loader.replaceFirstChar { it.uppercase() } }") { // not sure why Kotlin deprecated capitalize() ...
        group = "build"

        // builds the loader's jars (lazily)
        dependsOn(
            stonecutter.tasks.named("build") {
                metadata.project.endsWith(loader)
            }
        )

        // copies them to build/libs/
        val versions = stonecutter.versions.filter { it.project.endsWith(loader) }.map { it.version } // gets this loader's versions
        if(versions.isNotEmpty()) {
            var libs = rootProject.layout.buildDirectory.file("libs/").get().asFile
            libs.createNewFile() // ensure the directory exists

            from(versions.map {
                val vLibs = rootProject.file("versions/$it-$loader/build/libs/") // maps each version to its build/libs directory
                vLibs.createNewFile()
                vLibs
            })
            into(libs)
        } else {
            println("Warning: No versions specified for $loader; ensure `targets` is correctly populated")
        }
    }
}

registerLoaderBuildTask("fabric")
registerLoaderBuildTask("neo")
registerLoaderBuildTask("forge")

tasks.register("buildAll") {
    dependsOn("buildFabric", "buildNeo", "buildForge")
}