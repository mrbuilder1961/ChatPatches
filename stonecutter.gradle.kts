plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.2-fabric"


stonecutter tasks {
    order("publishModrinth")
    order("publishCurseforge")
}

// each build task builds all of its loader and copies them to build/libs/
fun registerLoaderBuildTask(loader: String) {
    // registers as a Copy task to allow easy copying
    tasks.register<Copy>("collect${loader.replaceFirstChar { it.uppercase() } }") { // not sure why Kotlin deprecated capitalize() ...
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
            val libs = rootProject.layout.buildDirectory.dir("libs").get().asFile
            // ensure the directory exists
            if(!libs.exists()) {
                libs.parentFile.mkdirs() // avoid IOExceptions
                libs.mkdir()
            }

            from(versions.map {
                val vLibs = rootProject.layout.projectDirectory.dir("versions/$it-$loader/build/libs/").asFile // maps each version to its build/libs directory
                if(!vLibs.exists()) {
                    vLibs.parentFile.mkdirs()
                    vLibs.mkdir()
                }
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

tasks.register("collectAll") {
    group = "build"
    dependsOn("collectFabric", "collectNeo", "collectForge")
}