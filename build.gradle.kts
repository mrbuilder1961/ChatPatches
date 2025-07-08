import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.modmuss50.mpp.ReleaseType

plugins { // versions in gradle.properties + settings.gradle.kts
    //id("dev.kikugie.stonecutter") //prepub ?
    id("dev.isxander.modstitch.base")
    id("me.modmuss50.mod-publish-plugin")
    //id("fabric-loom") // prepub ??
    kotlin("jvm")
}


println("name: $name, SVC: ${stonecutter.current.version}")// debug: !
val id = m("id") ?: error("No mod id specified")
val minecraft = name.substringBefore("-") // uses the stonecutter project's minecraft version
val loader: String = name.substringAfter("-") //name.substring(name.lastIndexOf('-') + 1)
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
fun l(name: String): String? = findProperty("${if(loader == "neoforge") "neo" else loader}.$name") as String?


// All dependencies should be specified through the modstitch proxy configuration.
// Wondering where the "repositories" block is? Go to "stonecutter.gradle.kts" // prepub lie
dependencies {
    // fabric only
    modstitch.loom {
        modstitchModImplementation("net.fabricmc.fabric-api:fabric-api:${l("api")}+$minecraft")
    }

    //modstitchModApi "dev.architectury:architectury-fabric:${d("arch")}"
    //modstitchModImplementation("eu.pb4:placeholder-api:${d("placeholder")}")
    if(minecraft == "1.20.2")
        modstitchModImplementation("dev.isxander.yacl:yet-another-config-lib-fabric:${d("yacl")}")
    else
        modstitchModImplementation("dev.isxander:yet-another-config-lib:${d("yacl")}-fabric")

    modstitchModImplementation("com.terraformersmc:modmenu:${d("modmenu")}")

    implementation(kotlin("stdlib-jdk8"))
}

repositories { // ??????
    mavenCentral()
    //maven("https://maven.kikugie.dev/releases")
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
        modVersion = v//"$v-$minecraft" // prepub?!
        modName = m("name")
        modGroup = m("group")
        modDescription = m("desc")
        modAuthor = m("author")
        modCredits = m("credits")?.replace("\"", "\\\"") //prepub todo make this a list
        modLicense = m("license")
        //todo forge: uses mods.toml instead of neoforge.mods.toml
        // also todo with FMJ: remove fabric api and use arch api or sm

        replacementProperties.populate {
            // prepub absolute logo path?
            //put("mod_logo", /*not rootDir..*/rootDir.toPath().resolve("assets/$id/logo.png").toString())
            put("minecraft_range", minecraft) // fixme: use a version-specific property that defaults to its mc vers?!
            put("mod_source", m("source") ?: error("No source repo specified"))
            put("mod_modrinth", m("modrinth") ?: error("No Modrinth ID specified"))
            // prepub delete this guy
            put("pack_format", // https://minecraft.wiki/w/Pack_format#List_of_data_pack_formats
                when(minecraft) {
                    "1.20", "1.20.1" -> 15
                    "1.20.2" -> 18
                    "1.20.3", "1.20.4" -> 26
                    "1.20.5", "1.20.6" -> 41
                    "1.21", "1.21.1" -> 48
                    "1.21.2", "1.21.3" -> 57
                    "1.21.4" -> 61
                    "1.21.5" -> 71
                    "1.21.6" -> 77
                    "1.21.7" -> 81
                    else -> error("No `pack_format` exists for $minecraft.")
                }.toString()
            )
        }
    }

    // Fabric
    loom {
        fabricLoaderVersion = p("fabric.loader")

        // Configure loom like normal in this block.
        configureLoom {
            //todo ?? blank i think
        }
    }

    // NeoForge, Forge
    moddevgradle {
        enable {
            prop("forge.loader") { forgeVersion = it }
            prop("neo.loader") { neoForgeVersion = it }
            //dep("mcp") { mcpVersion = it }
        }

        // Configures client runs for MDG, it is not done by default
        defaultRuns(true, false, { "$loader $it" })

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

stonecutter { // https://stonecutter.kikugie.dev/wiki/config/params
    // https://stonecutter.kikugie.dev/blog/changes/0.7.html#_0-7-alpha-10
    constants {
        match(if(loader == "neoforge") "neo" else loader, "fabric", "neo", "forge") // prepub does this neo shortener work
    }

    replacements {
        string {
            direction = eval(minecraft, ">1.20.2")
            phase = "first"//fixme????
            // a SINGLE LETTER CHANGE is SO DIABOLICAL it inspired me to add stonecutter.
            replace("SystemToast.SystemToastIds", "SystemToast.SystemToastId")
            //replace("ExtraCodecs.COMPONENT", "ComponentSerialization.CODEC")
        }
    }
}

kotlin {
    jvmToolchain(21)
}

tasks {
    modstitch.finalJarTask { // todo: vet plz idk if this is what i want or too much
        archiveBaseName.set(id) //?
        archiveVersion.set("$v+$minecraft")
        archiveClassifier.set(loader) //?
    }

    processResources {
        duplicatesStrategy = DuplicatesStrategy.WARN //?

        val changelogFile: File = rootDir.toPath().resolve("changelog.md").toFile()
        if(changelogFile.exists()) {
            var fileText = changelogFile.readText()
            // replace issue numbers with links
            fileText = fileText.replace(Regex("##(\\d+)"), "[#\$1](https://www.github.com/mrbuilder1961/ChatPatches/issues/\$1)")
            changelogFile.writeText(fileText) // update the file

            // hackily gets the first changelog entry
            val newEntryTitle = "## Chat Patches `$v`"
            val newIndex = fileText.indexOf(newEntryTitle)
            val prevEntryIndex = fileText.replaceFirst(newEntryTitle, "").indexOf("## Chat Patches `") + newEntryTitle.length - 2

            changes = fileText.substring(if(newIndex >= 0) newIndex else 0, prevEntryIndex)

            // considered "malformed" if it doesn't end with any word characters, whitespace, or newlines
            if( !changes.matches(Regex("(?s).*(\\s+|(\r?\n)+|\\w+)\$")) || newIndex == -1 ) {
                println("/!\\ Warning: /!\\ Changelog appears malformed, this is probably caused by an invalid version ($v).")
                if(allowPublish) {
                    allowPublish = false
                }
            }
        }
    }

    publishMods {
        dependencies.get().dependsOn("processResources")
    }
}

publishMods {
    val secrets = rootDir.toPath().resolve("secrets.json").toFile()

    fun propList(name: String): List<String> {
        val p: String = p(name) ?: return emptyList()
        return p.split(",").filter { it.isNotBlank() }
    }
    fun token(name: String): String {
        if(!allowPublish || !secrets.exists()) return "-"
        return (Json.parseToJsonElement( secrets.readText(Charsets.UTF_8) ).jsonObject[name]?.toString() ?: "?")
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

    /*if(allowPublish) {
        println("Publishing v$v to $loaders on $targets!")
    } else {
        println("Not publishing v$v because `allowPublish` was false! Maybe the changelog is malformed?")
        println("Using:")
        println("\tphase: ${type.get()}")
        println("\tbranch: omnivers")
        println("\tloaders: $loaders")
        println("\ttargets: $targets")
        println("\trequired: $required")
        println("\toptionals: $optionals")
        println("\tincompatibles: $incompatibles")
        println("\tembedded: $embedded")
        println("\tchangelog: |$changes|")
        //return@publishMods
    }*/

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
        repository = "mrbuilder1961/ChatPatches"
        commitish = "omnivers"
        tagName = "$v-$name" //prepub
        //additionalFiles.from(remapSourcesJar.archiveFile, jar.archiveFile)//fixme
    }

    discord {
        webhookUrl = token("discord") // official
        dryRunWebhookUrl = token("discord_debug") // testing
        username = "Publisher Bot"
        avatarUrl = "https://cdn.modrinth.com/data/MOqt4Z5n/56c954dea290ef4dd1b0d6ea92a811acac62ca85.png"
    }
}