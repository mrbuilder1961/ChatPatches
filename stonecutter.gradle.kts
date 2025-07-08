plugins {
    id("dev.kikugie.stonecutter")
    //id("me.modmuss50.mod-publish-plugin")
    //id("dev.isxander.modstitch.base") apply false//prepub ???
}

//val vcs: String by settings//todo?
stonecutter active "1.20.2-fabric"

// each build task builds all of its loader and copies them to build/libs/
fun registerLoaderBuildTask(loader: String) {
    // not sure why Kotlin deprecated capitalize() ...
    tasks.register("build${loader.replaceFirstChar { it.uppercase() } }") {
        // todo: copy to rootProject.layout.buildDirectory.file("libs")
        dependsOn(
            stonecutter.tasks.named("build") {
                metadata.project.endsWith(loader)
            }
        )
    }
}

registerLoaderBuildTask("fabric")
registerLoaderBuildTask("neo")
//registerLoaderBuildTask("forge") // todo w forge

tasks.register("buildAll") {
    dependsOn("buildFabric", "buildNeo"/*, "buildForge"*/)
}