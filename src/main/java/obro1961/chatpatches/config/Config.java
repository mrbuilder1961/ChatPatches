package obro1961.chatpatches.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
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
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.MathHelper;
import obro1961.chatpatches.ChatLog;
import obro1961.chatpatches.ChatPatches;
import obro1961.chatpatches.accessor.ChatHudAccess;
import obro1961.chatpatches.util.ChatUtils;
import obro1961.chatpatches.util.TextUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;

import static net.minecraft.util.Formatting.*;
import static obro1961.chatpatches.ChatPatches.*;
import static obro1961.chatpatches.util.RenderUtils.BLANK_STYLE;
import static obro1961.chatpatches.util.TextUtils.fillVars;
import static obro1961.chatpatches.util.TextUtils.text;

public class Config {
    public static final Config DEFAULTS = new Config();
    public static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("chatpatches.json");

	protected static final int IO_THRESHOLD_SUGGESTION = 500;
    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    /** @see #sendBoundaryLine() */
    protected static String lastWorld = "";


    // todo #297,000,000: figure out some way to do config migration aka field aliases. they should be hardcoded, so maybe with annotations? but they'll look weird with the
    //  current system, so maybe just like a Map<Str, List<Str>> with field names as keys and the list of aliases as strings contained in the list value?
    //  >> OR a separate MIGRATION_CODEC where we explicitly define field names' aliases, and then use that to parse the config file if on reg failure
    // tab categories: message, boundary, chatlog, chat
	// subgroups: [time, hover, counter, counter.compact], [boundary], [chatlog], [chat.name, chat, chat.context, chat.search]
    public boolean time = true, timeSystemMessages = true; public String timeDate = "HH:mm:ss", timeFormat = "[$]"; public int timeColor = LIGHT_PURPLE.getColorValue();
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
        caseSensitive = true, regex = false;
	public boolean logMessageStructures = false;

    /**
     * Initializes {@linkplain ChatPatches#config the config} according to installed
	 * mods ({@link YaclConfig} if relevant, else Config) and populates its values
	 * according to {@link #deserialize()}.
	 *
	 * @apiNote Should only be called once.
     */
    public static Config initialize() {
        FabricLoader f = FabricLoader.getInstance();
		boolean accessibleInGame = f.isModLoaded("modmenu") || (f.isModLoaded("catalogue") && f.isModLoaded("menulogue"));

		// ensures yacl config is used if available
		config = accessibleInGame ? new YaclConfig() : DEFAULTS;

        deserialize();

		return config;
    }


    public Screen getConfigScreen(Screen parent) {
		//stonecutter: will fix this silly protocol version access :D
        boolean suggestYACL = SharedConstants.getProtocolVersion() >= 759; // 1.19 or higher
        String link = "https://modrinth.com/mod/" + (suggestYACL ? "yacl" : "cloth-config");

        return new ConfirmScreen(
            clicked -> {
                if(clicked)
                    ConfirmLinkScreen.open(link, parent, true);
                else
                    mc.setScreen(parent);
            },
            Text.translatable(YaclConfig.HELP_PREFIX + "missing"),
            Text.translatable(YaclConfig.DESCRIPTION_PREFIX + "help.missing", (suggestYACL ? "YACL" : "Cloth Config")),
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

            logReportMsg(e);
        }

        return makeObject(nameFormat, profile.getName(), "", " ", style);
    }

    public MutableText makeDupeCounter(int dupes) {
		return makeObject(counterFormat, Integer.toString(dupes), " ", "", BLANK_STYLE.withColor(counterColor));
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

        ChatHudAccess chat = (ChatHudAccess) mc.inGameHud.getChatHud();
		String current = mc.isIntegratedServerRunning() // this check prevents NPEs for both if branches
            ? "C_" + mc.getServer().getSaveProperties().getLevelName()
            : mc.getCurrentServerEntry() instanceof ServerInfo entry
                ? "S_" + (entry.name.isBlank() ? entry.address : entry.name) // if the name is blank, uses the address instead
                : "?_?"; // prevents weird game states from throwing IOOBEs from the substring below

        // continues if messages in chat and if the last and current worlds were servers, that they aren't the same
        if( !chat.chatpatches$getMessages().isEmpty() && (!current.startsWith("S_") || !lastWorld.startsWith("S_") || !current.equals(lastWorld)) ) {
            try {
                String levelName = (lastWorld = current).substring(2); // makes a variable to update lastWorld in a cleaner way
                boolean time = config.time;

                config.time = false; // disables the time so the boundary line doesn't have a timestamp
                mc.inGameHud.getChatHud().addMessage(
					// first -> MSG_TEAM_INDEX, second -> MSG_SENDER_INDEX, third -> MSG_CONTENT_INDEX
					ChatUtils.buildMessage(null, null, null, makeObject(boundaryFormat, levelName, "", "", BLANK_STYLE.withColor(boundaryColor)))
				);
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
     * Reads the config settings saved at {@link Config#PATH} and puts them into
     * {@link ChatPatches#config}. <b>Cannot be executed on an I/O worker thread</b>
	 * because the ModMenu screen factory doesn't initialize properly when the
	 * config isn't ready right away. However, if there are any I/O issues with the
	 * config file specifically, they probably have to do with the user and not the
	 * mod.
     *
     * @implNote Changed recently to better match {@link ChatLog#deserialize()} and
	 * to fix <a href="https://github.com/mrbuilder1961/ChatPatches/issues/208">#208</a>,
     * which was caused by loading an invalid (or empty) config.
     */
    public static void deserialize() {
		long start = System.currentTimeMillis();
		LOGGER.info("[Config.deserialize] Reading...");

		if(!Files.exists(PATH)) {
			config = DEFAULTS;
			LOGGER.info("[Config.deserialize] No config file found; using default values");
			return;
		}

		try {
			// on different lines to make exception line numbers more useful
			String raw = Files.readString(PATH);
			JsonObject json = JsonHelper.deserialize(raw);

			config = config.parse(jsonOps(), json)
				.resultOrPartial(e -> logReportMsg(new JsonParseException(e)))
				.orElseThrow();

			LOGGER.info("[Config.deserialize] Read config data from '{}'!", PATH);
		} catch(IOException | NoSuchElementException e) {
			config = DEFAULTS;
			String action = e instanceof NoSuchElementException ? "decode" : "read";
			LOGGER.error("[Config.deserialize] An error occurred while trying to {} config data from '{}', backing up and using default settings:", action, PATH, e);
			backup();
		} catch(RuntimeException e) {
			config = DEFAULTS;
			LOGGER.error("[Config.deserialize] An unexpected error occurred, backing up and using default settings");
			logReportMsg(e);
			backup();
		}
		ChatPatches.logDuration(start, IO_THRESHOLD_SUGGESTION);
	}

    /**
     * Saves {@link ChatPatches#config} to {@link #PATH}. <b>Executed on an
	 * {@linkplain Util#getIoWorkerExecutor() I/O worker thread} to avoid freezing
	 * the render thread.</b>
     */
    public static void serialize() {
		ChatPatches.executeIoTask(() -> {
			long start = System.currentTimeMillis();
			LOGGER.info("[Config.serialize] Saving...");

			try {
				JsonElement json = config.encodeStart(ChatPatches.jsonOps())
					.resultOrPartial(e -> logReportMsg(new JsonParseException(e)))
					.orElseThrow();

				String pretty = new GsonBuilder()
					.setPrettyPrinting()
					.disableHtmlEscaping() // also disables non-ASCII characters becoming \\uXXXX codes
					//.serializeNulls() // might be needed in the future, hopefully this can save a few days of agonizing debugging
					.create()
						.toJson(json); // automatically sorts the keys as declared in this class

				Files.writeString(PATH, pretty, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

				LOGGER.info("[Config.serialize] Saved config data to '{}'!", PATH);
			} catch(IOException | NoSuchElementException e) {
				String action = e instanceof NoSuchElementException ? "encode" : "save";

				LOGGER.error("[Config.serialize] An error occurred while trying to {} config data to '{}'", action, PATH);
				logReportMsg(e);
			}
			ChatPatches.logDuration(start, IO_THRESHOLD_SUGGESTION);
		});
	}

    /**
     * Creates a copy of the current config file located at {@link #PATH} and
     * saves it to {@code chatpatches_${now}.json} in the same directory as the
     * original. If an error occurs, a warning will be logged. Doesn't modify the
     * current config. <b>Executed on an {@linkplain Util#getIoWorkerExecutor()
     * I/O worker thread} to avoid freezing the render thread.</b>
     */
    public static void backup() {
		ChatPatches.executeIoTask(() -> {
			try {
				Files.copy(PATH, PATH.resolveSibling(MOD_ID + "_" + Util.getFormattedCurrentTime() + ".json"));
			} catch(IOException e) {
				LOGGER.warn("[Config.backup] An error occurred trying to back up the original config file:", e);
			}
		});
	}


    public ObjectList<Setting<?>> getOptions() {
        Field[] fields = Config.class.getFields();
        ObjectList<Setting<?>> options = new ObjectArrayList<>( fields.length );

        try {
            for(Field f : fields)
                if(!Modifier.isStatic(f.getModifiers()))
                    options.add(new Setting<>( f.get(config), f.get(DEFAULTS), f.getName() ));
        } catch(IllegalAccessException e) {
            logReportMsg(e);
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
    public <T> Setting<T> getOption(String key) {
        return (Setting<T>)
            getOptions()
            .stream()
            .filter(opt -> opt.key.equals(key))
            .findFirst()
            .orElseGet(() -> {
                logReportMsg(new IllegalArgumentException("No such option: " + key));
                return new Setting<>(new Object(), new Object(), key);
            });
    }


    /**
	 * Encodes this Config instance into a {@link DataResult}
	 * dynamically based on its fields.
	 *
	 * @return A {@link DataResult} containing the encoded {@link
	 * ChatPatches#config}, otherwise one with an error message.
	 *
	 * @implNote Largely based on
     * <a href="https://github.com/isXander/YetAnotherConfigLib/blob/multiversion/dev/src/main/java/dev/isxander/yacl3/config/v3/CodecConfig.java">
     * YACL's built-in <code>CodecConfig</code> class</a>!
	 */
    @SuppressWarnings("unchecked")
    public <R, T> DataResult<R> encodeStart(DynamicOps<R> ops) {
        RecordBuilder<R> builder = ops.mapBuilder();

        for(Setting<?> opt : getOptions()) {
            T val = (T) opt.val;
            MapCodec<T> optCodec = (MapCodec<T>) opt.getTypeCodec();

            // note: currently uses optionalFieldOf as specified in #getTypeCodec; could in theory silently ignore missing fields
            builder = optCodec.encode(val, ops, builder);
        }

        return builder.build(ops.empty());
    }

    /**
     * Parses the {@link S}(ource) parameter {@code encoded} into
     * {@code this}, or more specifically {@link ChatPatches#config}.
     *
     * @return A {@link DataResult} containing the Config
     * stored in {@link ChatPatches#config} if successful, otherwise
     * an error message. Said message will be printed to the log before
     * returning.
     *
     * @implNote Largely based on
     * <a href="https://github.com/isXander/YetAnotherConfigLib/blob/multiversion/dev/src/main/java/dev/isxander/yacl3/config/v3/CodecConfig.java">
     * YACL's built-in <code>CodecConfig</code> class</a>!
     */
    @SuppressWarnings("unchecked")
    public <S, T> DataResult<Config> parse(DynamicOps<S> ops, S encoded) {
        for(Setting<?> opt : getOptions()) {
            MapCodec<T> optCodec = (MapCodec<T>) opt.getTypeCodec();
            DataResult<T> result = optCodec.decoder().parse(ops, encoded);

            if(result.error().isPresent() || result.result().isEmpty()) {
                String message = "[Config.parse] Failed to parse field '" + opt.key + "' : " + result.error().map(DataResult.PartialResult::message).orElse("<unknown>");
                logReportMsg(new IllegalStateException(message));
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
                logReportMsg(e);
            }
        }


        /**
         * @return The {@link Codec} for this setting's option value wrapped as an
         * {@linkplain Codec#optionalFieldOf(String, Object) optional field}
         * {@link MapCodec}, per this Setting's {@link #key} and {@linkplain #def
         * default value}. Provides required serialization checks, particularly for
         * {@link String}s and {@link Integer}s ({@link TextColor}s); however, no int
		 * range checks are performed.
         */
        @SuppressWarnings({"unchecked", "unused"}) // from pattern variables
        public MapCodec<T> getTypeCodec() {
			Codec<T> codec = (Codec<T>) switch(def) {
				// this monstrosity allows parsing int -> TextColor (migration) and String <-> TextColor (default) while the final result is always an int
				// much love to TheWhyEvenHow for the solution: https://discord.com/channels/507304429255393322/721100785936760876/1385863368300040244
                case Object o when key.contains("Color") -> Codecs.alternatively(TextColor.CODEC, Codec.INT.xmap(TextColor::fromRgb, TextColor::getRgb)).xmap(TextColor::getRgb, TextColor::fromRgb); // expands the
                case String s when key.contains("Format") -> Codec.STRING.comapFlatMap(
                    raw -> raw.contains("$")
                        ? DataResult.success(raw)
                        : DataResult.error(() -> "[Config$Setting#getTypeCodec] Format string '" + raw + "' for option '" + key + "' is missing a '$'"),
                    Function.identity()
                );
                case String s when key.contains("Date") -> Codec.STRING.comapFlatMap(
                    raw -> {
						try {
                            new SimpleDateFormat(raw);
							return DataResult.success(raw);
						} catch(IllegalArgumentException e) {
							return DataResult.error(() -> "[Config$Setting#getTypeCodec] Date string '" + raw + "' for option '" + key + "' is not a valid SimpleDateFormat");
						}
					},
                    Function.identity()
                );
                case String s -> Codec.STRING;
				case Integer i -> Codec.INT;
				case Boolean b -> Codec.BOOL;
                default -> {
                    logReportMsg(new IllegalStateException("Option '" + key + "' is not a valid type for serialization"));
                    yield Codec.STRING;
                }
            };

            return codec.optionalFieldOf(key, def);
        }
    }
}