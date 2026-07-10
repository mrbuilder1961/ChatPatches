import dev.kikugie.stonecutter.build.config.ReplacementContainer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.modmuss50.mpp.ReleaseType
import me.modmuss50.mpp.platforms.curseforge.CurseforgeVersionRangeOptions
import me.modmuss50.mpp.platforms.modrinth.ModrinthVersionRangeOptions
import org.gradle.crypto.checksum.Checksum

plugins { // versions in gradle.properties + settings.gradle.kts
    kotlin("jvm")
    id("com.google.devtools.ksp") // kotlin and this r for fletching-table
    id("dev.kikugie.fletching-table.fabric")
    id("org.gradle.crypto.checksum") //todo temp comment out this stuff..?
    signing
    id("dev.isxander.mtk.manifests")
    id("me.modmuss50.mod-publish-plugin")
    id("dev.isxander.modstitch.base")
    id("net.fabricmc.fabric-loom") apply false
}

fun String.capitalize(): String = replaceFirstChar(Char::uppercaseChar)

val id = m("id")
val v = m("version")
val minecraft = stonecutter.current.version
val loader = when {
    modstitch.isLoom -> "fabric"
    modstitch.isModDevGradle -> "neoforge"
    modstitch.isModDevGradleLegacy -> "forge"
    else -> error("Invalid Modstitch loader ${modstitch.platform}")
}
val currentIsActive = minecraft == stonecutter.active?.version
val nonReleaseComponent = findProperty("mod.nonReleaseComponent")?.toString()
val signFinalJarTask = "sign" + modstitch.finalJarTask.name.capitalize()

var publish = providers.gradleProperty("publish").getOrElse("false").toBoolean() // prepub: abolish bc this is annoying bc the default is
// that it will publish bc the property is not set but u need that for regular publishMods to work without ugly command line parameters, but it would be best
// if we just had a `testPublishMods` task
var changes = "No changelog specified."

fun fullMc(): String {
    var ver: String = minecraft
    if(nonReleaseComponent?.isNotEmpty() ?: false) {
        ver += "-${nonReleaseComponent.removePrefix("!")}"
    }
    return ver
}
fun java(): Int = modstitch.javaVersion.orNull ?: error("No Java version available (per Modstitch)")
fun javaStr(): String = java().toString()

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

/**
 * See the `publishMods` and `modstitch.metadata` blocks
 */
fun propList(name: String): List<String> = p(name).split(",").filter { it.isNotBlank() }

fun d(name: String, fallback: String? = null): String = p("dep.$name", fallback)
fun dep(name: String, consumer: (prop: String) -> Unit) = prop("dep.$name", consumer)

fun m(name: String, fallback: String? = null): String = p("mod.$name", fallback)
fun mod(name: String, consumer: (prop: String) -> Unit) = prop("mod.$name", consumer)

/**
 * Returns the property belonging to the current loader. For example, `l("api")`
 * will return the value of `fabric.api`, `neoforge.api`, or `forge.api` depending on
 * the current loader.
 */
fun l(name: String, fallback: String? = null): String = p("$loader.$name", fallback)

fun token(name: String): String {
    val secrets: File? = rootDir.toPath().resolve("secrets.json").toFile()
    if(secrets?.exists() == true) {
        // kotlin's json is weird
        return Json.parseToJsonElement(secrets.readText(Charsets.UTF_8)).jsonObject[name]?.toString()?.replace("\"", "") ?: "?"
    } else {
        publishMods.dryRun = true
        return "(no secrets.json specified)"
    }
}


/*kotlin {
    jvmToolchain(25) // can't use java() bc it's not available here, may cause issues?
}*/

dependencies {
    // snapshots are now handled via nonReleaseComponent
    val fapi = (if(modstitch.isModDevGradle) "" else l("api")) + "+" + minecraft

    // warning: this is ugly and not great, maybe remove?
    mod("nonReleaseComponent") {
        if(modstitch.isLoom && !it.startsWith("!")) {
            constraints {
                // enforces the specified fapi version when a non-release component is specified, avoiding
                // the common issue of old fapi dependencies preventing the buildscript from compiling!
                modstitchModImplementation("net.fabricmc.fabric-api:fabric-api:$fapi")
            }
        } /*else if(modstitch.isModDevGradle) {}*/
    }

    // fabric only
    modstitch.loom {
        if(minecraft > "1.20.1") {
            // loads the correct versions: https://github.com/FabricMC/fabric-api/pull/3487#issue-2058907234
            modstitchModImplementation(platform("net.fabricmc.fabric-api:fabric-api-bom:$fapi"))

            modstitchModImplementation("net.fabricmc.fabric-api:fabric-lifecycle-events-v1")
            modstitchModImplementation("net.fabricmc.fabric-api:fabric-networking-api-v1")
            modstitchModImplementation("net.fabricmc.fabric-api:fabric-screen-api-v1")
        } else {
            modstitchModImplementation("net.fabricmc.fabric-api:fabric-api:$fapi")
        }
    }

    modstitchModImplementation("dev.isxander:yet-another-config-lib:${d("yacl")}-fabric")

    modstitchModImplementation("com.terraformersmc:modmenu:${d("modmenu")}")

    implementation(kotlin("stdlib-jdk8"))
}

repositories {
    mavenCentral()
    maven("https://maven.terraformersmc.com/releases/")
    maven("https://maven.isxander.dev/releases")
}

modstitch {
    minecraftVersion = fullMc()
    if(nonReleaseComponent != null) {
        val note = if(nonReleaseComponent.startsWith("!")) " (dependency override disabled)" else ""
        println("Non-release version component: ${nonReleaseComponent.removePrefix("!")}$note")
    }

    // warning: inconsistent processed.ct file generation
    classTweaker = sc.process(rootProject.file("src/main/resources/access.ct"), ".gradle/processed.ct")
    //validateClassTweaker = providers.gradleProperty("forceValidation").getOrElse("").toBoolean()

    parchment {
        dep("parchment") { mappingsVersion = it }
    }

    // applies to *all* files inside the templates folder
    metadata {
        modId = id
        modVersion = v
        modName = m("name")
        modGroup = m("group")
        modDescription = m("desc")
        modAuthor = m("author")
        modCredits = m("credits").split(",").map { "\"$it\"" }.toString() // transforms the invalid json into a valid list
        modLicense = m("license")

        fun dep2StringList(list: String): String = propList(list).joinToString(separator = ",\n\t", transform = { "\"$it\": \"*\"" })

        replacementProperties.putAll(mapOf(
            "java" to javaStr(),
            "mod_source" to m("source"),
            "mod_modrinth" to m("modrinth"),

            // uses the override if available, otherwise translates the
            // maven range into a fabric one, escapes it, and gets the only
            // element from the list (which may contain multiple ranges)
            "minecraft_range" to m(
                "metadata",
                manifests.mavenRange( m("range", "[${fullMc()}]") )
                    .toFabric()[0]
            ).let { "\"$it\"" },
            // extracts the major version of the loader so lower minor versions don't throw a bogus error
            "fabric_loader_major" to (l("loader").substringBeforeLast('.', "!") + ".0"),
            "optional_list" to dep2StringList("optionals"),
            "incompatible_list" to dep2StringList("incompatibles"),
            //"embed_list" to dep2StringList("embedded"), // currently empty
        ))
    }

    // Fabric
    loom {
        fabricLoaderVersion = l("loader")

        // Configure loom like normal here
        /*configureLoom {}*/
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
            // + https://discord.com/channels/780023008668287017/780485575194312704/1402249054179688468
        }*/
    }

    mixin {
        addMixinsToModManifest = true

        configs.register(id)
    }
}

fletchingTable {
    fabric {
        // i lowkey don't know why i'm even bothering with these entrypoints
        // this feature feels completely useless to me but i'm just gonna leave em bc why not
        entrypointMappings.put("modmenu", "com.terraformersmc.modmenu.api.ModMenuApi")
    }

        //fixme: Name should match an existing source set
    mixins.create("main") {
        // matches the default value in the annotation
        mixin("default", "$id.mixins.json")
    }
}

signing {
    isRequired = publish

    useGpgCmd() // credentials specified in GRADLE_HOME
    sign(modstitch.finalJarTask.get()) // creates `signJar` on 26.1+ else `signRemapJar`
}

val checksumTask = tasks.register<Checksum>("generateChecksum") {
    description = "Generates a SHA256 hash for the registered final jar task."
    group = "signing"

    dependsOn(signFinalJarTask) // requires the signed jars to exist (which in turn requires `build`)

    inputFiles.from(modstitch.finalJarTask)
    outputDirectory = layout.buildDirectory.dir("libs")
    appendFileNameToChecksum = true
}

tasks {
    modstitch.finalJarTask {
        archiveBaseName.set(id)
        archiveVersion.set("$v+${fullMc()}")
        archiveClassifier.set(loader)
    }

    // https://docs.gradle.org/current/userguide/build_lifecycle.html#the_phases_in_build_scripts
    processResources {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        val changelogFile: File = rootDir.toPath().resolve("changelog.md").toFile()
        if(changelogFile.exists()) {
            var fileText = changelogFile.readText()

            // replace issue numbers with links
            fileText = fileText.replace(Regex("##(\\d+)"), "[#$1](https://www.github.com/mrbuilder1961/ChatPatches/issues/$1)")
            changelogFile.writeText(fileText)

            // hack-ily gets the first changelog entry
            val newEntryTitle = "## Chat Patches `$v`"
            val newIndex = fileText.indexOf(newEntryTitle)
            val prevEntryIndex = fileText.replaceFirst(newEntryTitle, "").indexOf("## Chat Patches `") + newEntryTitle.length - 2

            changes = (if(newIndex > prevEntryIndex) "" else fileText.substring(if(newIndex >= 0) newIndex else 0, prevEntryIndex))

            // considered "malformed" if it doesn't end with any word characters, whitespace, or newlines - or changes were emptied bc the indices were bad
            if(newIndex == -1 || !changes.matches(Regex("(?s).*(\\s+|(\r?\n)+|\\w+)$"))) {
                publish = false
                println("Warning: Changelog appears malformed; this is typically caused by an outdated version ($v)")
            }
        }
    }

    clean {
        delete(layout.buildDirectory.file("out"))
    }

    signFinalJarTask {
        dependsOn( if(sc.current.parsed >= "26.1") "jar" else "remapJar" )
    }

    //prepub: do we need to declare a dependency for signFinalJarTask?
    // it doesn't seem like it... but idk if gradle will crash out later
    /*publishMods {
        dependencies.get().dependsOn(tasks[signFinalJarTask], checksumTask)
    }*/
}

stonecutter { // https://stonecutter.kikugie.dev/wiki/config/params
    constants {
        match(loader, "fabric", "neo", "forge")
    }

    dependencies {
        put("java", javaStr())
        put("config", when {
            current.parsed >= "1.19" -> "yacl"
            else -> "cloth"
        })
    }

    swaps {
        val v1215 = current.parsed >= "1.21.5"
        // all of these require 'new' before them, regardless of version
        put("open_url", if(v1215) "ClickEvent.OpenUrl(URI.create($1))" else "ClickEvent(ClickEvent.Action.OPEN_URL, $1)") // java.net.URI is always available
        put("suggest_command", if(v1215) "ClickEvent.SuggestCommand($1)" else "ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, $1)")
        put("show_text", if(v1215) "HoverEvent.ShowText($1)" else "HoverEvent(HoverEvent.Action.SHOW_TEXT, $1)")

        val v1219 = current.parsed >= "1.21.9"
        put("key_args", if(v1219) "key" else "keyCode, scanCode, modifiers")
        put("mouse_args", if(v1219) "mouse, bl" else "mX, mY, button")
    }

    // https://stonecutter.kikugie.dev/wiki/config/params#replacement-overlapping:~:text=Excluded%20matches !!!
    replacements {
        fun str(dir: Boolean, from: String, to: String, nameId: String? = null, defaultEnabled: Boolean = true) {
            val action: ReplacementContainer.StringReplacementSpec.() -> Unit = { replace(from, to) }
            val id = (if(defaultEnabled) "!" else "") + nameId

            if(nameId == null) {
                if(!defaultEnabled) error("Replacement cannot be nameless and disabled by default: '$from' -> '$to'")
                string(dir, action)
            } else {
                string(dir, id, action)
            }

            //println("REGISTERED STRING REPLACEMENT $id: '$from' ${if(dir) "->" else "<-"} '$to' (defaultEnabled = $defaultEnabled)")
        }

        val j21 = java() >= 21
        str(j21, ".get(0)", ".getFirst()", "j21_get_first")
        str(j21, ".remove(0)", ".removeFirst()", "j21_remove_first")

        val v1219 = current.parsed >= "1.21.9"
        str(v1219, "int keyCode, int scanCode, int modifiers", "KeyEvent key") // key_event
        //str(v1219, "keyCode, scanCode, modifiers", "key") // key_args
        str(v1219, "double mX, double mY, int button", "MouseButtonEvent mouse, boolean bl") // mouse_event
        //str(v1219, "mX, mY, button", "mouse, bl") // mouse_args

        val v1216 = current.parsed >= "1.21.6"
        str(v1216, "graphics.pose().pushPose();", "graphics.pose().pushMatrix();") // push_stack
        str(v1216, "graphics.pose().popPose();", "graphics.pose().popMatrix();") // pop_stack

        val v12111 = current.parsed >= "1.21.11"
        str(v12111, "net.minecraft.Util", "net.minecraft.util.Util")
        str(v12111, "ResourceLocation", "Identifier", "yarnification", false) // selectively enabled
        // \- todo try out the specific disabling thing outlined in the docs so maybe i can auto enable this and selectively DISable it (perhaps
        //     automatically too!)

        val v261 = current.parsed >= "26.1"
        str(v261, "net.minecraft.client.GuiMessage", "net.minecraft.client.multiplayer.chat.GuiMessage") // also conveniently covers GuiMessageTag!
        str(v261, "GuiGraphics", "GuiGraphicsExtractor")
        str(v261, "render(", "extractRenderState(", "extract_render_state", true) // targets plain render(..) calls
        str(v261, "\"render\"", "\"extractRenderState\"", "extract_render_state_target", true) // targets plain render(..) injectors
        str(v261, "render", "extract", "render_extraction", false) // targets render<component>(..) calls

        val v262 = current.parsed >= "26.2"
        str(v262, "gui.getChat()", "gui.hud.getChat()") // how clear and verbose Mojang!
        str(v262, ".setScreen(", ".gui.setScreen(")
    }
}

fun trimChanges(max: Int = 2000): String {
    val cutoff = "... (trimmed)"
    return if(changes.length > max) {
        changes.substring(0, max - cutoff.length) + cutoff
    } else {
        changes
    }
}

publishMods {
    val required = propList("required")
    val optionals = propList("optionals")
    val incompatibles = propList("incompatibles")
    val embedded = propList("embedded")

    version = "$v+$name" // mod_version+minecraft-loader
    displayName = "$v for $minecraft ${loader.capitalize()}"
    file = modstitch.finalJarTask.flatMap { it.archiveFile }
    changelog = changes
    type = when {
        "alpha" in v -> ReleaseType.ALPHA
        "beta" in v -> ReleaseType.BETA
        else -> ReleaseType.STABLE
    }
    modLoaders = propList("loaders") // note: vers-specific for forge/neo version cutoffs
    dryRun = !publish /*findProperty("dryRun")?.toString()?.toBoolean() ?: false*/// todo: can i just specify dryRun as false thru the command line?

    /**
     * Takes the `minecraftVersions` ListProperty, the `minecraftVersionList()` method, and
     * a boolean representing Modrinth or CurseForge, and configures the minecraft versions
     * appropriately. Note that if the conditions are satisfied for `minecraftVersionRange`,
     * it will return a Curseforge/Modrinth -VersionRangeOptions object that can be used via
     * `let` or a similar method to set the actual publishing configuration.
     */
    fun specifyVersions(mcV: ListProperty<String>, mVL: (csv: String) -> Unit, modrinth: Boolean): Any? {
        val range = findProperty("mod.range") as String?
        val rangeList = range
            ?.let {
                it.substring(1, it.length - 1) // cuts off the enclosing brackets/parenthesis
            }
            ?.split(",")

        when {
            // if nothing is specified, use the only version we have
            rangeList == null -> mcV.add(minecraft)

            // if there are no brackets, we have a list
            !range.startsWith("[") && !range.startsWith("(") -> mVL(range)

            // otherwise, we have a range of versions!
            // todo this might throw an error for strings like `[26.1,)` or `(,1.21.11]`
            rangeList.size > 1 -> {
                val start = rangeList[0]
                /*startInclusive = range.startsWith('[')*/
                val end = if(currentIsActive) "latestRelease" else rangeList[1]
                /*endInclusive = range.endsWith(']')*/

                if(modrinth) {
                    val opts = getObjects().newInstance(ModrinthVersionRangeOptions::class.java)
                    opts.start = start
                    /*opts.startInclusive = startInclusive*/
                    opts.end = end
                    /*opts.endInclusive = endInclusive*/
                    opts.includeSnapshots = nonReleaseComponent != null
                    return opts
                } else {
                    val opts = getObjects().newInstance(CurseforgeVersionRangeOptions::class.java)
                    opts.start = start
                    /*opts.startInclusive = startInclusive*/
                    opts.end = end
                    /*opts.endInclusive = endInclusive*/
                    return opts
                }
            }

            else -> error("Invalid `mod.range` value specified: '$range'")
        }
        return null
    }

    curseforge {
        if(nonReleaseComponent != null) {
            println("Warning: Attempting to publish to CurseForge with a snapshot build!")
            return@curseforge
        }

        accessToken = token("curseforge")
        projectId = m("curseforge")
        projectSlug = m("id")
        client = true

        specifyVersions(minecraftVersions, ::minecraftVersionList, false)?.let {
            minecraftVersionRange {
                val src = it as CurseforgeVersionRangeOptions
                start = src.start
                /*startInclusive = src.startInclusive*/
                end = src.end
                /*endInclusive = src.endInclusive*/
            }
        }

        required.forEach(::requires)
        optionals.forEach(::optional)
        incompatibles.forEach(::incompatible)
        embedded.forEach(::embeds)
    }

    modrinth {
        accessToken = token("modrinth")
        projectId = m("modrinth")
        environment = CLIENT_ONLY

        specifyVersions(minecraftVersions, ::minecraftVersionList, true)?.let {
            minecraftVersionRange {
                val src = it as ModrinthVersionRangeOptions
                start = src.start
                /*startInclusive = src.startInclusive*/
                end = src.end
                /*endInclusive = src.endInclusive*/
                includeSnapshots = src.includeSnapshots
            }
        }

        // turns out i can't even upload checksums to modrinth..?? 😭 bc they're... already provided? but not shown to anyone unless explicitly API requested?
        // sigh.
        // todo modrinth's api only accepts signatures on v3 (unstable) not MPP compatible v2 </3
        //additionalFiles.from(tasks[signFinalJarTask]/*, checksumTask*/)

        // specify id OR slug NOT both, +OPTIONAL specific version
        required.forEach(::requires)
        optionals.forEach(::optional)
        incompatibles.forEach(::incompatible)
        embedded.forEach(::embeds)
    }

    if(currentIsActive) { // only announce the version once
        discord {
            webhookUrl = token("discord") // official
            dryRunWebhookUrl = token("discord_debug") // testing
            username = "Publisher Bot"
            avatarUrl = "https://cdn.modrinth.com/data/MOqt4Z5n/56c954dea290ef4dd1b0d6ea92a811acac62ca85.png"
            changelog = trimChanges()
        }
    }
}