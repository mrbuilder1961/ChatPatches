package obro1961.chatpatches.config;

import com.google.gson.*;
import com.google.gson.stream.JsonWriter;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.*;
import dev.isxander.yacl3.api.Option;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.Team;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.*;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import obro1961.chatpatches.ChatPatches;
import obro1961.chatpatches.accessor.ChatHudAccessor;
import obro1961.chatpatches.chatlog.ChatLog;
import obro1961.chatpatches.util.ChatUtils;
import obro1961.chatpatches.util.TextUtils;

import java.io.EOFException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.function.Function;

import static net.minecraft.util.Formatting.*;
import static obro1961.chatpatches.ChatPatches.LOGGER;
import static obro1961.chatpatches.ChatPatches.config;
import static obro1961.chatpatches.util.RenderUtils.BLANK_STYLE;
import static obro1961.chatpatches.util.TextUtils.fillVars;
import static obro1961.chatpatches.util.TextUtils.text;

public class Config {
    public static final Config DEFAULTS = new Config();
    public static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("chatpatches.json");

    protected static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    /** @see #sendBoundaryLine() */
    protected static String lastWorld = "";


    // prepub #297,000,000: figure out some way to do config migration aka field aliases. they should be hardcoded, so maybe with annotations? but they'll look weird with the
    // current system, so maybe just like a Map<Str, List<Str>> with field names as keys and the list of aliases as strings contained in the list value?
    // ->> OR a separate MIGRATION_CODEC where we explicitly define field names' aliases, and then use that to parse the config file if on reg failure
// prepub: impl `timestampedSystemMessages`
//todo: import ~~dynamicShift PR~~ + commits from 1.21.4/5
//prepub #INT_LIMIT: regen (and while ur at it test!) the option table
//prepub #INT_LIMIT+1: make colors serialize as strings (name else hex else int)
    // tab categories: message, boundary, chatlog, chat
	// subgroups: [time, hover, counter, counter.compact], [boundary], [chatlog], [chat.name, chat, chat.context, chat.search]
    public boolean time = true; public String timeDate = "HH:mm:ss", timeFormat = "[$]"; public int timeColor = LIGHT_PURPLE.getColorValue();
    public boolean hover = true; public String hoverDate = "MM/dd/yyyy", hoverFormat = "$"; public int hoverColor = WHITE.getColorValue();
    public boolean counter = true; public String counterFormat = "&8(&7x&r$&8)"; public int counterColor = YELLOW.getColorValue(); public boolean counterCheckStyle = false;
    public boolean compactChat = false; public int compactDistance = 0;

    public boolean boundary = true; public String boundaryFormat = "&8[&r$&8]"; public int boundaryColor = AQUA.getColorValue();

    public boolean chatlog = true; public int chatlogSaveInterval = 0;

    public boolean name = true; public String nameFormat = "<$>"; public int nameColor = WHITE.getColorValue();
    public int chatMaxMessages = 16384, chatWidth = 0, chatHeight = 0, chatShift = 0; public boolean vanillaClearing = false, chatHidePacket = true, dynamicChatShift = true, messageDrafting = false,
        onlyInvasiveDrafting = false;
    public boolean contextMenu = true; public int contextOutlineColor = AQUA.getColorValue(); public String contextReplyFormat = "/msg $ ";
    public boolean search = true, searchDrafting = true, searchPrefix = false,
        caseSensitive = true, formatting = false, regex = false;

    /**
     * Creates a new Config or YACLConfig, depending
     * on installed mods. Should only be called once.
     */
    public static Config create() {
        FabricLoader fbr = FabricLoader.getInstance();
		boolean accessibleInGame = fbr.isModLoaded("modmenu") || (fbr.isModLoaded("catalogue") && fbr.isModLoaded("menulogue"));
        config = accessibleInGame ? new YACLConfig() : DEFAULTS;

        read();
        write();

        return config;
    }


    public Screen getConfigScreen(Screen parent) {
        boolean suggestYACL = SharedConstants.getProtocolVersion() >= 759; // 1.19 or higher
        String link = "https://modrinth.com/mod/" + (suggestYACL ? "yacl" : "cloth-config");

        return new ConfirmScreen(
            clicked -> {
                if(clicked)
                    ConfirmLinkScreen.open(link, parent, true);
                else
                    mc.setScreen(parent);
            },
            Text.translatable(YACLConfig.HELP_PREFIX + "missing"),
            Text.translatable(YACLConfig.DESCRIPTION_PREFIX + "help.missing", (suggestYACL ? "YACL" : "Cloth Config")),
            ScreenTexts.CONTINUE,
            ScreenTexts.BACK
        );
    }


    /**
     * Creates a new {@link MutableText} from {@code formatStr} with
     * all '{@code $}'s replaced with {@code varStr}, and with the
     * specified {@code prefix}, {@code suffix}, and {@code style}
     * applied.
     * <br> Util method for the other 'make' methods.
     */
    private MutableText makeObject(String formatStr, String varStr, String prefix, String suffix, Style style) {
        // style layering: override all BLANK_STYLE properties w text style, and override those w style
        return text(prefix + fillVars(formatStr, varStr) + suffix).fillStyle(BLANK_STYLE.withParent(style));
    }

    /**
     * Creates a MutableText with a timestamp; uses the {@link #timeFormat},
     * {@link #timeDate}, and {@link #timeColor} config options. Note
     * that this still creates a timestamp even if {@link #time} is false.
     */
    public MutableText makeTimestamp(Date when) {
        return makeObject(timeFormat, new SimpleDateFormat(timeDate).format(when), "", " ", BLANK_STYLE.withColor(timeColor));
    }

    /**
     * Creates a text Style that contains extra timestamp information
     * when hovered over in-game. Uses {@link #hoverFormat}, {@link #hoverDate},
     * and {@link #hoverColor} to format the tooltip text. If {@link #hover} is
     * false, this will return a Style with only {@link #timeColor} used.
     */
    public Style makeHoverStyle(Date when) {
		MutableText hoverText = makeObject(hoverFormat, new SimpleDateFormat(hoverDate).format(when), "", "", BLANK_STYLE.withColor(hoverColor));

        return BLANK_STYLE
            .withHoverEvent( hover ? new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText) : null )
            .withClickEvent( hover ? new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, hoverText.getString()) : null )
            .withInsertion(String.valueOf( when.getTime() ))
            .withColor(timeColor)
        ;
    }

    /**
     * Formats the provided playername, using {@link #nameFormat},
     * {@link #nameColor}, and the player's team properties. Uses
     * the player's team color if set, otherwise {@link #nameColor}.
     * Hover and click events are sourced from the style of
     * {@link PlayerEntity#getDisplayName()}.
     *
     * @implNote {@code player} must reference a valid, existing
     * player entity and have both a valid name and UUID. Additionally,
     * the {@linkplain MinecraftClient#world client world} must exist.
     */
    public MutableText formatPlayername(GameProfile profile) {
        Style style = Style.EMPTY.withColor(nameColor); // defaults to the config-specified color
        try {
            Team team = mc.world.getScoreboard().getPlayerTeam(profile.getName());
            Style hoverStyle = new OtherClientPlayerEntity(mc.world, profile).getDisplayName().getStyle() // gets the correct style (hover/click/insertion)
                .withParent(style); // fills in the color with nameColor if not specified by the team
            String[] configFormat = nameFormat.equals("$") ? new String[] {"", ""} : nameFormat.split("\\$"); // a singular $ results in an empty array
            ObjectList<Text> components = new ObjectArrayList<>(team != null ? 5 : 3);


            components.add(text( configFormat[0] ));                   // config prefix
            components.add(text( profile.getName() ));                 // playername
            components.add(text( configFormat[1] + " " )); // config suffix

            if(team != null) {
                components.add(1, team.getPrefix()); // team prefix
                components.add(3, team.getSuffix()); // team suffix
            }

            return TextUtils.newText(LiteralTextContent.EMPTY, components, hoverStyle);
        } catch(RuntimeException e) {
            LOGGER.error("[Config.formatPlayername] /!\\ An error occurred while trying to format '{}'s playername /!\\", profile.getName());

            if(mc.world == null)
                e.addSuppressed(new IllegalStateException("[Config#formatPlayername] Expected existing ClientWorld"));

            ChatPatches.logReportMsg(e);
        }

        return makeObject(nameFormat, profile.getName(), "", " ", style);
    }

    public MutableText makeDupeCounter(int dupes) {
		return makeObject(counterFormat, Integer.toString(dupes), " ", "", BLANK_STYLE.withColor(counterColor));
    }

    public Text makeBoundaryLine(String levelName) {
        // needs empty strings to avoid errors when comparing the dupe counter
        return ChatUtils.buildMessage(null, null, makeObject(boundaryFormat, levelName, "", "", BLANK_STYLE.withColor(boundaryColor)), null);
    }

    /**
     * Sends a boundary line in chat when the player
     * switches worlds. This only runs if
     * {@link #boundary} is enabled,
     * {@link #vanillaClearing} is disabled, and
     * the chat isn't empty.
     *
     * <p>Grabs the level name from the current world
     * (singleplayer) or server entry (multiplayer),
     * the latter of which uses the server IP if the
     * name is blank. The boundary line will not send
     * if the server hasn't changed since the last
     * boundary line was sent.
     */
    public void sendBoundaryLine() {
        if(!boundary || vanillaClearing)
            return;

        ChatHudAccessor chat = (ChatHudAccessor) mc.inGameHud.getChatHud();
		String current = mc.isIntegratedServerRunning() // this check prevents NPEs for both if branches
            ? "C_" + mc.getServer().getSaveProperties().getLevelName()
            : mc.getCurrentServerEntry() instanceof ServerInfo entry
                ? "S_" + (entry.name.isBlank() ? entry.address : entry.name) // if the name is blank, uses the address instead
                : "";

        // continues if messages in chat and if the last and current worlds were servers, that they aren't the same
        if( !chat.chatpatches$getMessages().isEmpty() && (!current.startsWith("S_") || !lastWorld.startsWith("S_") || !current.equals(lastWorld)) ) {
            try {
                String levelName = (lastWorld = current).substring(2); // makes a variable to update lastWorld in a cleaner way
                boolean time = config.time;

                config.time = false; // disables the time so the boundary line doesn't have a timestamp
                mc.inGameHud.getChatHud().addMessage( makeBoundaryLine(levelName) );
                config.time = time; // re-enables the time accordingly
            } catch(RuntimeException e) {
                LOGGER.warn("[Config.sendBoundaryLine] An error occurred while adding the boundary line:", e);
            }
        }
    }

	/**
	 * Calculates the appropriate chat shifting offset to use if
	 * {@link Config#dynamicChatShift} is enabled, which accounts
	 * for the player's visible armor and health bars. If this
	 * option is disabled or the player is null (shouldn't
	 * ever happen), simply returns {@link Config#chatShift}.
     * Doesn't dynamically shift if the player is in creative or
     * spectator mode, as the health and armor bars are not visible.
	 *
	 * @author <a href="https://github.com/radioactive-exe">radioactive-exe</a>!
	 * The majority of this code was contributed in
	 * <a href="https://github.com/mrbuilder1961/ChatPatches/pull/224">#224</a>.
	 */
	public int calcDynamicChatShift() {
		PlayerEntity player = mc.player;

		if(!config.dynamicChatShift || player == null)
			return chatShift;
        // don't shift the chat if there are no hearts visible (not in survival or adventure)
        // also note that player is always non-null by this point
        if(mc.getNetworkHandler().getPlayerListEntry(player.getUuid()) instanceof PlayerListEntry entry && !entry.getGameMode().isSurvivalLike())
            return chatShift;

		// get player stats and standardize to scaled number of rows
		int armor = player.getArmor();
		float absorption = player.getAbsorptionAmount();
		float health = player.getMaxHealth();
		double scale = mc.inGameHud.getChatHud().getChatScale();

		// calculate health multiplier here to avoid an extra call to PlayerEntity#getHeartRows()
		int armorHeightMultiplier = (armor == 0) ? 0 : 1 + ((armor - 1) / 20);
		int healthHeightMultiplier = (int) (health + absorption - 1) / 20;

		//float specificHealthScales[] = {0.75f, 0.6f, 0.5f, 0.45f, 0.3f, 0.3f, 0.3f, 0.3f}; // contingency
		// currently uses a third-degree polynomial regression to calculate the health scale
		float healthScale = healthHeightMultiplier > 7
			? 0.3f
			: 0.00583333f * (float)Math.pow(healthHeightMultiplier, 3) - 0.0722619f * (float)Math.pow(healthHeightMultiplier, 2) + 0.154048f * healthHeightMultiplier + 0.918571f;

		return (armorHeightMultiplier * MathHelper.floor(10 / scale))
			+ (healthHeightMultiplier * MathHelper.floor(10 * healthScale / scale))
			+ chatShift;
	}


    /**
     * Loads the config settings saved at {@link Config#PATH}
     * into {@link ChatPatches#config}.
     *
     * @implNote Changed recently to better match
     * {@link ChatLog#deserialize()} and to fix
     * <a href="https://github.com/mrbuilder1961/ChatPatches/issues/208">#208</a>,
     * which was caused by loading an invalid config.
     */
    public static void read() {
        if(Files.exists(PATH)) {
            try {
                String rawData = Files.readString(PATH);
                if(rawData.length() < 2 || !rawData.startsWith("{") || !rawData.endsWith("}"))
                    throw new EOFException("ChatPatches config file is empty or corrupted");

                config = GSON.fromJson(rawData, config.getClass());
                LOGGER.info("[Config.read] Loaded config info from '{}'!", PATH);
            } catch(JsonIOException | JsonSyntaxException | EOFException e) {
                LOGGER.info("[Config.read] The config couldn't be loaded; backing up and resetting:", e);
                writeCopy();
                config = DEFAULTS;
            } catch(IOException e) {
                LOGGER.error("[Config.read] An error occurred while trying to load config data from '{}'; resetting:", PATH, e);
                config = DEFAULTS;
            }
        } else {
            // config already has default values
            LOGGER.info("[Config.read] No config file found; using default values");
        }
    }

    /** Saves the {@code ChatPatches.config} instance to {@link Config#PATH} */
    public static void write() {
        try(FileWriter fw = new FileWriter(PATH.toFile())) {
            GSON.toJson(config, config.getClass(), fw);
            LOGGER.info("[Config.write] Saved config info to '{}'!", PATH);
        } catch(IOException | JsonIOException e) {
            LOGGER.error("[Config.write] An error occurred while trying to save the config to '{}':", PATH, e);
        }
    }

    /**
     * Creates a backup of the current config file
     * located at {@link #PATH} and saves it
     * as "config_" + current time + ".json" in the
     * same directory as the original file.
     * If an error occurs, a warning will be logged.
     * Doesn't modify the current config.
     */
    public static void writeCopy() {
		try {
			Files.copy(PATH, PATH.resolveSibling( "chatpatches_" + Util.getFormattedCurrentTime() + ".json" ));
		} catch(IOException e) {
            LOGGER.warn("[Config.writeCopy] An error occurred trying to write a copy of the original config file:", e);
		}
	}


    public ObjectList<Setting<?>> getOptions() { // todo?: getAll
        Field[] fields = Config.class.getFields();
        ObjectList<Setting<?>> options = new ObjectArrayList<>( fields.length );

        try {
            for(Field f : fields)
                if(!Modifier.isStatic(f.getModifiers()))
                    options.add(new Setting<>( f.get(config), f.get(DEFAULTS), f.getName() ));
        } catch(IllegalAccessException e) {
            ChatPatches.logReportMsg(e);
        }

        return options;
    }

    /**
     * Returns a {@link Setting} representing the option with the given name,
     * or one populated with blank objects and the given key if an error
     * occurred. Note that the returned Setting's value is sourced from the
     * value in {@link ChatPatches#config}, and as a result may not be accurate
     * forever.
     *
     * @param key The name of the Setting to get, as defined in {@linkplain
     * Config this class}
     */
    @SuppressWarnings("unchecked")
    public <T> Setting<T> getOption(String key) { //todo?: get
        return (Setting<T>)
            getOptions()
            .stream()
            .filter(opt -> opt.key.equals(key))
            .findFirst()
            .orElseGet(() -> {
                ChatPatches.logReportMsg(new IllegalArgumentException("No such option: " + key));
                return new Setting<>(new Object(), new Object(), key);
            });
    }


    /**
     * Encodes this {@link Config} instance into a {@link DataResult}
     * dynamically based on its fields.
     *
     * @return A {@link DataResult} containing the encoded {@link
     * ChatPatches#config}, otherwise one with an error message.
     */
    @SuppressWarnings("unchecked")
    public <R, T> DataResult<R> encodeStart(DynamicOps<R> ops) {
        RecordBuilder<R> builder = ops.mapBuilder();

        for(Setting<?> opt : getOptions()) {
            T val = (T) opt.val;
            MapCodec<T> optCodec = (MapCodec<T>) opt.getCodec();

            // note: currently uses optionalFieldOf as specified in #getCodec; could in theory silently ignore missing fields
            builder = optCodec.encode(val, ops, builder);
        }
        // error: saving colors in the menu fails and logs 'Option value mismatch after applying! Reset to binding's getter.'

        return builder.build(ops.empty());
    }

    /**
     * Parses the {@link S}(ource) parameter {@code encoded} into
     * {@code this}, or more specifically {@link ChatPatches#config}.
     *
     * @return A {@link DataResult} containing the {@link Config}
     * stored in {@link ChatPatches#config} if successful, otherwise
     * an error message. Said message will be printed to the log before
     * returning.
     */
    @SuppressWarnings("unchecked")
    public <S, T> DataResult<Config> parse(DynamicOps<S> ops, S encoded) {
        for(Setting<?> opt : getOptions()) {
            MapCodec<T> optCodec = (MapCodec<T>) opt.getCodec();
            DataResult<T> result = optCodec.decoder().parse(ops, encoded);

            if(result.error().isPresent() || result.result().isEmpty()) {
                String message = "[Config.parse] Failed to parse field '" + opt.key + "' : " + result.error().map(DataResult.PartialResult::message).orElse("<unknown>");
                ChatPatches.logReportMsg(new IllegalStateException(message));
                return DataResult.error(() -> message);
            }

            opt.set( result.result().get() );
        }

        return DataResult.success(this);
    }

    /**
     * A simple class that wraps the String/Class
     * pair used for each config field. This is
     * merely an abstraction used for simplifying
     * mod config implementations.
     *
     * @apiNote This class is commonly explained
     * as a "config option" or "option". This is
     * because those are correct, but "setting"
     * is used only to avoid confusion with the
     * existing {@link Option} class.
     */
    public static class Setting<T> {
        /**
         * The lang key of this setting option, aka
         * the field name in {@link Config}.
         */
        public final String key;
        /**
         * The default value of this setting option.
         */
        public final T def;
        protected T val;

        public Setting(T val, T def, String key) {
            this.val = Objects.requireNonNull(val, "Cannot create a setting option without a value");
            this.def = Objects.requireNonNull(def, "Cannot create a setting option without a default value");
            this.key = Objects.requireNonNull(key, "Cannot create a setting option without a lang key");
        }


        public T get() { return val; }

        @SuppressWarnings("unchecked")
        public Class<T> getType() { return (Class<T>) def.getClass(); }

        /**
         * Sets this setting option's value to {@code obj} in
         * {@link ChatPatches#config}. This only changes the value
         * if {@code obj} is not null, not equal to the
         * current value, and of the correct type.
         */
        public void set(Object obj) {
            try {
                @SuppressWarnings("unchecked")
                T inc = (T) obj;

                if(inc != null && !inc.equals(val)) {
                    config.getClass().getField(key).set(config, inc);

                    this.val = inc;
                }
            } catch(NoSuchFieldException | IllegalAccessException | ClassCastException e) {
                LOGGER.error("[Setting.set({})] An error occurred trying to change config option '{}'", obj, key);
                ChatPatches.logReportMsg(e);
            }
        }
    }
}