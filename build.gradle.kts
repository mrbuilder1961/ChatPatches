import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.modmuss50.mpp.ReleaseType

plugins { // versions in gradle.properties + settings.gradle.kts
    id("dev.isxander.modstitch.base")
    id("me.modmuss50.mod-publish-plugin")
    kotlin("jvm")
}


val id = m("id") ?: error("No mod id specified")
val minecraft = stonecutter.current.version //name.substringBefore("-")
val loader: String = name.substringAfter("-").replace("neoforge", "neo") // prepub: does this cause any issues...
val v: String = m("version") ?: error("No version specified")

var allowPublish = false
var changes = "No changelog specified."

fun p(name: String): String? = findProperty(name) as String?
fun prop(name: String, consumer: (prop: String) -> Unit) = p(name)?.let(consumer)

fun d(name: String): String? = findProperty("deps.$name") as String?
fun dep(name: String, consumer: (prop: String) -> Unit) = d(name)?.let(consumer)

fun m(name: String): String? = findProperty("mod.$name") as String?

/**
 * Returns the property belonging to the current loader. For example, `l("api")`
 * will return the value of `fabric.api`, `neo.api`, or `forge.api` depending on
 * the current loader.
 */
fun l(name: String): String? = findProperty("$loader.$name") as String?


kotlin {
    jvmToolchain(21)
}

dependencies {
    // fabric only
    modstitch.loom {
        modstitchModImplementation("net.fabricmc.fabric-api:fabric-api:${l("api")}+$minecraft")
    }

    //modstitchModImplementation("eu.pb4:placeholder-api:${d("placeholder")}")
    if(minecraft == "1.20.2")
        modstitchModImplementation("dev.isxander.yacl:yet-another-config-lib-fabric:${d("yacl")}")
    else
        modstitchModImplementation("dev.isxander:yet-another-config-lib:${d("yacl")}-fabric")

    modstitchModImplementation("com.terraformersmc:modmenu:${d("modmenu")}")

    implementation(kotlin("stdlib-jdk8"))
}

repositories {
    mavenCentral()
    //todo NEW MAVEN FOR ARCH... later
    maven("https://maven.isxander.dev/releases")
    maven("https://maven.terraformersmc.com/releases/")
    maven("https://maven.nucleoid.xyz/") // Placeholder API, for us and Mod Menu
}

modstitch {
    minecraftVersion = minecraft

    // Alternatively use stonecutter.eval if you have a lot of versions to target.
    // https://stonecutter.kikugie.dev/stonecutter/guide/setup#checking-versions
    javaTarget = 21

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
        modCredits = m("credits")?.split(",")?.toString() // transforms the invalid json into a valid list
        modLicense = m("license")
        //todo forge: uses mods.toml instead of neoforge.mods.toml
        // also todo with FMJ: remove fabric api and use arch api or sm

        replacementProperties.populate {
            // URGENT: idk how range is supposed to work between fabric's nice system and neo's dumb maven shit
            put("minecraft_range", m("range") ?: minecraft) // if range is not specified, use the current minecraft version
            put("mod_source", m("source") ?: error("No source repo specified"))
            put("mod_modrinth", m("modrinth") ?: error("No Modrinth ID specified"))
        }
    }

    // Fabric
    loom {
        fabricLoaderVersion = if(isLoom) l("loader") else error("Trying to specify Fabric loader on '$loader'") //p("fabric.loader")


        // Configure loom like normal in this block.
        configureLoom {
            //todo ?? blank unless i need to edit something like AW (aka disable validation for versioning)
        }
    }

    // NeoForge, Forge
    moddevgradle {
        enable {
            prop("forge.loader") { forgeVersion = it }
            prop("neo.loader") { neoForgeVersion = it }
        }

        // Configures client runs for MDG, it is not done by default
        defaultRuns(true, false) { "$loader $it" }

        // This block configures the `neoforge` extension that MDG exposes by default,
        // you can configure MDG like normal from here
        configureNeoforge {
            runs.all {
                disableIdeRun()
            }
            //todo https://projects.neoforged.net/neoforged/moddevgradle # Runs
        }
    }

    mixin {
        addMixinsToModManifest = true // auto-gen mixins in FMJ and mods.toml

        configs.register("chatpatches")

        // If you need loader specific mixins, simply make the mixin file and add it like so for the respective loader:
        // if(is(Loom|ModDevGradleRegular|ModDevGradleLegacy))
            // configs.register("chatpatches-{}")
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
                println("/!\\ Warning: /!\\ Changelog appears malformed, this is probably caused by an invalid version ($v).")
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
    // https://stonecutter.kikugie.dev/blog/changes/0.7.html#_0-7-alpha-10
    constants {
        match(loader, "fabric", "neo", "forge")
    }

    //prepub: make this data-driven from gradle.properties
    replacements {
        // needed bc only one replacement per block -_-
        fun strRepl(dir: Boolean, from: String, to: String) {
            string {
                direction = dir
                replace(from, to)
            }
        }

        strRepl(eval(minecraft, ">=1.20.3"), "SystemToast.SystemToastIds.", "SystemToast.SystemToastId.") // '.' prevents adding an extra 's'

        // todo: update and merge the toast method so i can just put the versioned code straight in the method
        strRepl(eval(minecraft, ">=1.21.2"), "getToasts()", "getToastManager()") // on Minecraft

        val v1216 = eval(minecraft, ">=1.21.6")
        //fixme: all of these replacements need stonecutter comments so i dont forget they're being replaced
        strRepl(v1216, "graphics.pose().pushPose()", "graphics.pose().pushMatrix()") // on GuiGraphics
        strRepl(v1216, "graphics.pose().popPose()", "graphics.pose().popMatrix()") // on GuiGraphics
    }
}


publishMods {
    val secrets = rootDir.toPath().resolve("secrets.json").toFile()

    fun propList(name: String): List<String> {
        val p: String = p(name) ?: return emptyList()
        return p.split(",").filter { it.isNotBlank() }
    }
    fun token(name: String): String {
        return when {
            !allowPublish -> "-"
            !secrets.exists() -> {
                dryRun = true
                "x"
            }
            else -> (Json.parseToJsonElement( secrets.readText(Charsets.UTF_8) ).jsonObject[name]?.toString() ?: "?")
        }
    }

    val loaders = propList("loaders")
    val targets  = propList("targets")

    val required = propList("required")
    val optionals = propList("optionals")
    val incompatibles = propList("incompatibles")
    val embedded = propList("embedded")
    //prepub prob need to store these in options... sigh

    //version = v // automatically set by mpp
    displayName = "${modstitch.metadata.modName} $v for \$minecraft_range on $loader" //prepub keep?
    file = modstitch.finalJarTask.flatMap { it.archiveFile } // https://modmuss50.github.io/mod-publish-plugin/getting_started/#input-file
    changelog = changes
    type = when {
        v.contains("alpha", true) -> ReleaseType.ALPHA
        v.contains("beta", true) -> ReleaseType.BETA
        else -> ReleaseType.STABLE
    }
    modLoaders = loaders
    println("dryRun = ${dryRun.getOrElse(false)}, but will be set to ${!allowPublish}")
    dryRun = !allowPublish

    curseforge {
        if(allowPublish) {
            println("Publishing v$v to $loaders on $targets!")
        }

        accessToken = token("curseforge")
        projectId = m("curseforge") ?: error("No CurseForge project id specified")
        projectSlug = m("id") ?: error("No mod id specified")
        minecraftVersions.addAll(targets)

        required.forEach(::requires)
        optionals.forEach(::optional)
        incompatibles.forEach(::incompatible)
        embedded.forEach(::embeds)
    }

    modrinth {
        accessToken = token("modrinth")
        projectId = m("modrinth") ?: error("No Modrinth project id specified")
        minecraftVersions.addAll(targets)

        // specify id OR slug NOT both, +OPTIONAL specific version
        required.forEach(::requires)
        optionals.forEach(::optional)
        incompatibles.forEach(::incompatible)
        embedded.forEach(::embeds)
    }

    github {
        accessToken = token("github")
        repository = m("source")!!
        commitish = "omnivers"
        tagName = "$v-$name" //prepub

        if(modstitch.isLoom) {
            additionalFiles.from(
                tasks.remapSourcesJar.flatMap { it.archiveFile }, // warning: broken bc loom-specific?
                modstitch.namedJarTask.flatMap { it.archiveFile } // should work for both
            )
        }
    }

    discord {
        webhookUrl = token("discord") // official
        dryRunWebhookUrl = token("discord_debug") // testing
        username = "Publisher Bot"
        avatarUrl = "https://cdn.modrinth.com/data/MOqt4Z5n/56c954dea290ef4dd1b0d6ea92a811acac62ca85.png"
    }
}