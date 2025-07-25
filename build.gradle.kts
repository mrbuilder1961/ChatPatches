import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.modmuss50.mpp.ReleaseType

plugins { // versions in gradle.properties + settings.gradle.kts
    id("dev.isxander.modstitch.base")
    id("me.modmuss50.mod-publish-plugin")
    kotlin("jvm")
}


val id = m("id")
val v: String = m("version")
val minecraft = stonecutter.current.version //name.substringBefore("-")
val loader: String = name.substringAfter("-").replace("neoforge", "neo") // prepub: does this cause any issues...
val currentIsActive = minecraft == stonecutter.active?.version
val java = if(stonecutter.eval(minecraft, ">1.20.4")) 21 else 17

var allowPublish = false
var changes = "No changelog specified."

/**
 * Returns the property with the given name. If it doesn't exist then returns the
 * fallback, but if that's null then throws an error.
 */
fun p(name: String, fallback: String? = null): String {
    val p = findProperty(name) as String?
    return when {
        p != null -> p
        fallback != null -> fallback
        else -> error("Property '$name' not found with no fallback provided")
    }
}
fun prop(name: String, consumer: (prop: String) -> Unit) {
    val p = p(name, "")
    if(p.isNotEmpty()) p.let(consumer)
}

fun d(name: String, fallback: String? = null): String = p("dep.$name", fallback)
fun dep(name: String, consumer: (prop: String) -> Unit) = prop("dep.$name", consumer)

fun m(name: String, fallback: String? = null): String = p("mod.$name", fallback)
//fun mod(name: String, consumer: (prop: String) -> Unit) = prop("mod.$name", consumer)

/**
 * Returns the property belonging to the current loader. For example, `l("api")`
 * will return the value of `fabric.api`, `neo.api`, or `forge.api` depending on
 * the current loader.
 */
/*fun l(name: String, fallback: String? = null): String = p("$loader.$name", fallback)*/


kotlin {
    jvmToolchain(java)
}

dependencies {
    // fabric only
    modstitch.loom {
        modstitchModImplementation("net.fabricmc.fabric-api:fabric-api:${p("fabric.api")}+$minecraft")
    }

    if(minecraft == "1.20.2")
        modstitchModImplementation("dev.isxander.yacl:yet-another-config-lib-fabric:${d("yacl")}")
    else
        modstitchModImplementation("dev.isxander:yet-another-config-lib:${d("yacl")}-fabric")

    //modstitchModImplementation("eu.pb4:placeholder-api:${d("placeholder")}")
    modstitchModImplementation("com.terraformersmc:modmenu:${d("modmenu")}")

    implementation(kotlin("stdlib-jdk8"))
}

repositories {
    mavenCentral()
    maven("https://maven.isxander.dev/releases")
    maven("https://maven.terraformersmc.com/releases/")
    maven("https://maven.nucleoid.xyz/") // Placeholder API, for us and Mod Menu
}

modstitch {
    minecraftVersion = minecraft

    parchment {
        dep("parchment") { mappingsVersion = it }
    }

    // applies to any files inside the templates folder
    metadata {
        fun <K, V> MapProperty<K, V>.populate(block: MapProperty<K, V>.() -> Unit) { block() }

        modId = id
        modVersion = v
        modName = m("name")
        modGroup = m("group")
        modDescription = m("desc")
        modAuthor = m("author")
        modCredits = m("credits").split(",").toString() // transforms the invalid json into a valid list
        modLicense = m("license")
        //todo forge: uses mods.toml instead of neoforge.mods.toml
        // also todo with FMJ: remove fabric api and use arch api or sm

        replacementProperties.populate {
            put("java", java.toString())
            put("mod_source", m("source"))
            put("mod_modrinth", m("modrinth"))
            put("minecraft_range", m("range", "")
                .takeIf { it.contains(",") } // if there are multiple versions...
                ?.split(",") // parse them into a list
                ?.map { "\"$it\"" } // add quotes to ensure valid JSON syntax
                ?.toString()
                ?: "\"$minecraft\"" // else only one version
                //if(!isLoom) [list.getFirst(),list.getLast()] // version ranges should all be consecutive
            )
        }
    }

    // Fabric
    loom {
        fabricLoaderVersion = p("fabric.loader")


        // Configure loom like normal in this block.
        configureLoom {
            //todo ?? blank unless i need to edit something like AW (aka disable validation for versioning)
            // also note that AW/ATs are now automatically translated to the other by modstitch >:) see stonecutter website for using them together :D
        }
    }

    // NeoForge, Forge
    moddevgradle {
        // Configures client runs for MDG, it is not done by default
        //defaultRuns(true, false) { "$loader $it" }

        // This block configures the `neoforge` extension that MDG exposes by default,
        // you can configure MDG like normal from here
        /*configureNeoforge {
            runs.all {
                disableIdeRun()
            }
            //todo https://projects.neoforged.net/neoforged/moddevgradle # Runs
        }*/
    }

    mixin {
        addMixinsToModManifest = true // auto-gen mixins in FMJ and mods.toml

        configs.register(id)

        // loader specific mixin configs:
        //if(is(Loom|ModDevGradleRegular|ModDevGradleLegacy))
            //configs.register("$id-{}")
    }
}
tasks {
    modstitch.finalJarTask {
        archiveBaseName.set(id)
        archiveVersion.set("$v+$minecraft")
        archiveClassifier.set(loader)
    }

    processResources {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        outputs.upToDateWhen { false } // from Bawnorton/Trimica: works around modstitch mixin cache issue

        val changelogFile: File = rootDir.toPath().resolve("changelog.md").toFile()
        if(changelogFile.exists()) {
            var fileText = changelogFile.readText()
            // replace issue numbers with links
            fileText = fileText.replace(Regex("##(\\d+)"), "[#$1](https://www.github.com/mrbuilder1961/ChatPatches/issues/$1)")
            changelogFile.writeText(fileText) // update the file

            // hack-ily gets the first changelog entry
            val newEntryTitle = "## Chat Patches `$v`"
            val newIndex = fileText.indexOf(newEntryTitle)
            val prevEntryIndex = fileText.replaceFirst(newEntryTitle, "").indexOf("## Chat Patches `") + newEntryTitle.length - 2

            changes = fileText.substring(if(newIndex >= 0) newIndex else 0, prevEntryIndex)

            // considered "malformed" if it doesn't end with any word characters, whitespace, or newlines
            if( !changes.matches(Regex("(?s).*(\\s+|(\r?\n)+|\\w+)$")) || newIndex == -1 ) {
                println("Warning: Changelog appears malformed, this is probably caused by an invalid version ($v).")
                if(allowPublish) {
                    allowPublish = false
                }
            }
        }
    }

    clean {
        delete(rootProject.layout.buildDirectory)
        delete(project.file("build"))
    }

    publishMods {
        dependencies.get().dependsOn("processResources")
    }
}

stonecutter { // https://stonecutter.kikugie.dev/wiki/config/params
    constants { match(loader, "fabric", "neo", "forge") }
    dependencies { put("java", java.toString()) }

    swaps {
        //prepub: make this data-driven from gradle.properties
        val v1216 = eval(minecraft, ">=1.21.6")
        put("pushStack", if(v1216) "graphics.pose().pushMatrix();" else "graphics.pose().pushPose();")
        put("popStack", if(v1216) "graphics.pose().popMatrix();" else "graphics.pose().popPose();")
    }

    replacements {
        string {
            direction = java < 21
            replace(".getFirst()", ".get(0)")
        }
    }
}


publishMods {
    val secrets = rootDir.toPath().resolve("secrets.json").toFile()

    fun propList(name: String): List<String> = p(name).split(",").filter { it.isNotBlank() }
    fun token(name: String): String {
        return when {
            !secrets.exists() -> {
                dryRun = true
                "-"
            }
            else -> (
                Json.parseToJsonElement( secrets.readText(Charsets.UTF_8) )
                    .jsonObject[name]
                    ?.toString()
                    ?.replace("\"", "") // kotlin's json is weird
                ?:
                    "?"
            )
        }
    }

    val targets = m("range", minecraft).split(",")
    val required = propList("required")
    val optionals = propList("optionals")
    val incompatibles = propList("incompatibles")
    val embedded = propList("embedded")
    //prepub prob need to store these in options... sigh

    version = "$v+$name" // mod_version+minecraft-loader
    displayName = "$v for $minecraft ${loader.replaceFirstChar { it.uppercase() }}"
    file = modstitch.finalJarTask.flatMap { it.archiveFile } // https://modmuss50.github.io/mod-publish-plugin/getting_started/#input-file
    changelog = changes
    type = when {
        "alpha" in v -> ReleaseType.ALPHA
        "beta" in v -> ReleaseType.BETA
        else -> ReleaseType.STABLE
    }
    modLoaders = propList("loaders") // todo: vers-specific for forge/neo version cutoffs
    dryRun = !allowPublish

    curseforge {
        accessToken = token("curseforge")
        projectId = m("curseforge")
        projectSlug = m("id")
        minecraftVersions.addAll(targets)

        required.forEach(::requires)
        optionals.forEach(::optional)
        incompatibles.forEach(::incompatible)
        embedded.forEach(::embeds)
    }

    modrinth {
        accessToken = token("modrinth")
        projectId = m("modrinth")
        minecraftVersions.addAll(targets)

        // specify id OR slug NOT both, +OPTIONAL specific version
        required.forEach(::requires)
        optionals.forEach(::optional)
        incompatibles.forEach(::incompatible)
        embedded.forEach(::embeds)
    }

    // temp disabled bc idk how to make stonecutter accumulate the versions (its not compiling)
    /*github {
        accessToken = token("github")

        if(currentIsActive) {
            repository = m("source")
            commitish = "omnivers"
            tagName = version

            allowEmptyFiles = true // active version (parent) task only
        } else {
             //parent(stonecutter.tasks.named("publishMods").filter { it.version == minecraft })
        }
    }*/

    // only announce the version once (if it's the active version)
    if(currentIsActive) {
        discord {
            webhookUrl = token("discord") // official
            dryRunWebhookUrl = token("discord_debug") // testing
            username = "Publisher Bot"
            avatarUrl = "https://cdn.modrinth.com/data/MOqt4Z5n/56c954dea290ef4dd1b0d6ea92a811acac62ca85.png"
        }
    }
}