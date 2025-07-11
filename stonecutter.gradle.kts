plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.7-fabric"

// each build task builds all of its loader and copies them to build/libs/
fun registerLoaderBuildTask(loader: String) {
    // not sure why Kotlin deprecated capitalize() ...
    tasks.register("build${loader.replaceFirstChar { it.uppercase() } }") {
        dependsOn(
            stonecutter.tasks.named("build") {
                metadata.project.endsWith(loader)
            }
        )

        // todo: copy shit doesn't work bc the copy block returns a WorkResult and not a Task -_-
        /*copy {
            val versions = (findProperty("targets") as String?)?.split(",") ?: emptyList()
            if(versions.isEmpty()) {
                println("no version targets..")
                return@copy
            }

            var libs = rootProject.layout.buildDirectory.file("libs/").get().asFile
            var _temp_ = libs.createNewFile() // ensure the directory exists
            println("created libs '${libs.path}' : $_temp_")

            from(versions.map {
                val vLibs = rootProject.file("versions/$it-$loader/build/libs/") // maps each version to its build/libs directory
                println("created vLibs '${vLibs.path}' : ${  vLibs.createNewFile()  }")

                vLibs
            })
            into(libs)
        }*/
    }
}

registerLoaderBuildTask("fabric")
registerLoaderBuildTask("neo")
//registerLoaderBuildTask("forge") // todo

tasks.register("buildAll") {
    dependsOn("buildFabric", "buildNeo"/*, "buildForge"*/)
}