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
import net.minecraft.ChatFormatting;
import net.minecraft.util.Util;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
//? if <1.21.5 {
//import net.minecraft.client.multiplayer.PlayerInfo;
//?}
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents./*? if >1.20.2 {*/PlainTextContents/*?} else {*//*LiteralContents*//*?}*/;
//? if >1.20.1 && <=1.20.4 {
//import net.minecraft.util.ExtraCodecs;
//?}
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import obro1961.chatpatches.Boundary;
import obro1961.chatpatches.ChatLog;
import obro1961.chatpatches.ChatPatches;
import obro1961.chatpatches.util.TextUtil;
//? if <=1.20.1 {
//import obro1961.chatpatches.util.VersionUtil;
//?}

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;

import static net.minecraft.ChatFormatting.*;
import static obro1961.chatpatches.ChatPatches.*;
//? if >=1.21.9 {
import static obro1961.chatpatches.integration.ChatHeadsIntegration.*;
//?}
import static obro1961.chatpatches.util.TextUtil.fillVars;
import static obro1961.chatpatches.util.TextUtil.text;

public class Config {
    public static final Config DEFAULTS = new Config();
    public static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("chatpatches.json");
	public static final String PLACEHOLDER = "$"; // prepub use in all options..? like of(pre, suf) -> pre + PLACEHOLDER + suf

	protected static final int IO_THRESHOLD_SUGGESTION = 500;

    protected static Minecraft mc() { return Minecraft.getInstance(); }

    /** @see #sendBoundaryLine() */
    protected static Boundary lastBoundary = Boundary.UNKNOWN;
	/**
	 * The hash of the player's armor, absorption, health, and other values for
	 * use with {@link #lastDynamicShift}. Avoids recalculating the same value
	 * every render tick. Only used when {@link #dynamicChatShift} is enabled.
	 *
	 * @see #calcDynamicChatShift()
	 */
	protected static int lastShiftState = -1;
	/**
	 * The shifted height value for use with the current {@link #lastShiftState}
	 * value. Avoids recalculating the same value every render tick. Only used
	 * when {@link #dynamicChatShift} is enabled.
	 *
	 * @see #calcDynamicChatShift()
	 */
	protected static int lastDynamicShift = -1;


    // todo #297,000,000: figure out some way to do config migration aka field aliases. they should be hardcoded, so maybe with annotations? but they'll look weird with the
    //  current system, so maybe just like a Map<Str, List<Str>> with field names as keys and the list of aliases as strings contained in the list value?
    //  >> OR a separate MIGRATION_CODEC where we explicitly define field names' aliases, and then use that to parse the config file if on reg failure
    // tab categories: message, boundary, chatlog, chat
	// subgroups: [time, hover, counter, counter.compact], [boundary], [chatlog], [chat.name, chat, chat.context, chat.search]
    public boolean time = true, timeSystemMessages = true; public String timeDate = "HH:mm:ss", timeFormat = "[$]"; public int timeColor = LIGHT_PURPLE.getColor();
    public boolean hover = true; public String hoverDate = "MM/dd/yyyy", hoverFormat = PLACEHOLDER; public int hoverColor = WHITE.getColor();
    public boolean counter = true; public String counterFormat = "&8(&7x&r$&8)"; public int counterColor = YELLOW.getColor(); public boolean counterCheckStyle = false;
    public boolean compactChat = false; public int compactDistance = 0;

    public boolean boundary = true; public String boundaryFormat = "&8[&r$&8]"; public int boundaryColor = AQUA.getColor();

    public boolean chatlog = true; public int chatlogSaveInterval = 0;

    public boolean name = true; public String nameFormat = "<$>"; public int nameColor = WHITE.getColor();
    public int chatMaxMessages = 16384, chatWidth = 0, chatHeight = 0, chatShift = 0; public boolean dynamicChatShift = true, vanillaClearing = false, chatHidePacket = true,
		// idea: messageDrafting -> chatDrafting?
		messageDrafting = false, onlyInvasiveDrafting = false;
    public boolean contextMenu = true; public int contextOutlineColor = AQUA.getColor(); public String contextReplyFormat = "/msg $ ";
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
		//setDefaults() but req for the yaclconfig check to work

        deserialize();

		return config;
    }

    public Screen getConfigScreen(Screen parent) {
        String link = "https://modrinth.com/mod/" + /*? if config: =yacl {*/"yacl"/*?} else {*//*"cloth-config"*//*?}*/;
		String modTitle = /*? if config: =yacl {*/"YACL"/*?} else {*//*"Cloth Config"*//*?}*/;

        return new ConfirmScreen(
            clicked -> {
                if(clicked) {
					ConfirmLinkScreen.confirmLinkNow(/*? if >1.20.2 {*/ parent, link /*?} else {*//*link, parent, true*//*?}*/);
				} else {
					mc().setScreen(parent);
				}
            },
            Component.translatable(YaclConfig.HELP_PREFIX + "missing").withStyle(ChatFormatting.RED),
            Component.translatable(YaclConfig.DESCRIPTION_PREFIX + "help.missing", modTitle).withStyle(GRAY),
            CommonComponents.GUI_CONTINUE,
            CommonComponents.GUI_BACK
        );
    }

    /**
     * Creates a new {@link MutableComponent} based on {@code formatStr} with all
	 * instances of {@value #PLACEHOLDER} replaced with {@code varStr}. {@code prefix}, {@code
	 * suffix}, and {@code rgbColor} are then applied accordingly.
     */
    private MutableComponent makeText(String formatStr, String varStr, String prefix, String suffix, int rgbColor) {
        return text(prefix + fillVars(formatStr, varStr) + suffix)
			./*? if >1.20.2 {*/withColor(rgbColor)/*?} else {*//*setStyle(Style.EMPTY.withColor(rgbColor))*//*?}*/;
    }

	private MutableComponent makeText(String formatStr, String varStr, int rgbColor) {
		return makeText(formatStr, varStr, "", "", rgbColor);
	}


    /**
	 * Creates a timestamp from the given time and formats it according to
	 * {@link #timeFormat}, {@link #timeDate}, and {@link #timeColor}. The
	 * timestamp's style is specified by {@link #hoverFormat}, {@link #hoverDate},
	 * and {@link #hoverColor} (if {@link #hover} is true). An insertion is always
	 * added with a string representation of the given time so the context menu can
	 * always provide timestamp info.
	 * <br><br>
	 * If the following expression is true, the returned component will contain
	 * a timestamp; otherwise, empty styled component:
	 *
	 * <PRE>{@link #time} && !boundary && ({@link #timeSystemMessages} ? true : !system)</PRE>
	 *
	 * So if this is a boundary line, timestamps are disabled, or system messages
	 * shouldn't be timestamped and this is a system message, no timestamp will be
	 * returned.
     */
    public MutableComponent makeTimestamp(Date when, boolean system, boolean boundary) {
		MutableComponent timestamp = time && !boundary && (timeSystemMessages || !system)
			? makeText(timeFormat, new SimpleDateFormat(timeDate).format(when), "", " ", timeColor)
			: Component.empty();
		MutableComponent hoverText = makeText(hoverFormat, new SimpleDateFormat(hoverDate).format(when), hoverColor);

		return timestamp.withStyle(s ->
			s.withHoverEvent( hover ? TextUtil.showText(hoverText) : null )
			.withClickEvent( hover ? TextUtil.suggestCommand(hoverText.getString()) : null )
			.withInsertion(String.valueOf( when.getTime() ))
			.withColor(timeColor)
		);
    }

	/**
     * Formats the provided playername, using {@link #nameFormat},
     * {@link #nameColor}, and the player's team properties. Uses
     * the player's team color if set, otherwise {@link #nameColor}.
     * Hover and click events are sourced from the style of
     * {@link Player#getDisplayName()}.
     *
     * @implNote {@code player} must reference a valid, existing
     * player entity and have both a valid name and UUID. Additionally,
     * the {@linkplain Minecraft#level client world} must exist.
     */
    public MutableComponent formatPlayername(Component originalMessage, GameProfile profile) {
        Style style = Style.EMPTY.withColor(nameColor); // defaults to the config-specified color
		String name = profile != null ? profile./*? if >=1.21.9 {*/name/*?} else {*//*getName*//*?}*/() : "<null>";
		var level = mc().level;

        try {
            PlayerTeam team = level.getScoreboard().getPlayersTeam(name);
            Style hoverStyle = new RemotePlayer(level, profile).getDisplayName().getStyle() // gets the correct style (hover/click/insertion)
                .applyTo(style); // fills in the color with nameColor if not specified by the team
            String[] configFormat = nameFormat.equals(PLACEHOLDER) ? new String[] {"", ""} : nameFormat.split("\\$"); // note: changing placeholder requires removing the backslashes in the split regex
            ObjectList<Component> components = new ObjectArrayList<>(team != null ? 5 : 3);

            components.add(text( configFormat[0] ));                    // config prefix
            components.add(text( name ));							    // playername
            components.add(text( configFormat[1] + " " )); // config suffix

			/*? if >=1.21.9 {*/
			if(/*ChatHeadsIntegration.*/installed() && usingBeforeName()) {
				// if installed and necessary, gets the already-added head
				// component from the message and adds it to the playername!
				// see #285 (ChatHeads#83) for why it's easier for Chat Patches to do this going forward
				var head = extractHeadComponent(originalMessage);
				head.ifPresent(mutableComponent -> components.set(1, mutableComponent.append(components.get(1))));
			}
			/*?}*/

            if(team != null) {
                components.add(1, team.getPlayerPrefix()); // team prefix
                components.add(3, team.getPlayerSuffix()); // team suffix
            }

            return new MutableComponent(
				/*? if >1.20.2 {*/PlainTextContents/*?} else {*//*LiteralContents*//*?}*/.EMPTY,
				components,
				hoverStyle
			);
        } catch(RuntimeException e) {
            LOGGER.error("Failed to format playername '{}'", name);

            if(level == null) {
				e.addSuppressed(new IllegalStateException("Expected existing ClientLevel"));
			}

            logReportMsg(e);
			pushErrorToast("Playername formatting error", e.getMessage());
        }

        return makeText(nameFormat, name, "", " ", style.getColor().getValue()).withStyle(style);
    }

    public MutableComponent makeDupeCounter(int dupes) {
		return makeText(counterFormat, Integer.toString(dupes), " ", "", counterColor);
    }

    /**
     * Sends a boundary line in chat when the player switches worlds. This only
	 * happens if {@link #boundary} is enabled, {@link #vanillaClearing} is disabled,
	 * and the chat isn't empty. If the last message received was a different
	 * boundary line from another level, it will be deleted and replaced with the
	 * current level.
	 *
	 * @see #lastBoundary
	 * @see Boundary
     */
    public void sendBoundaryLine() {
        if(!boundary || vanillaClearing) return;

		ChatComponent chat = mc().gui.getChat();
        List<GuiMessage> messages = chat.allMessages;
		Boundary currentLevel = Boundary.createFromCurrentLevel();

		if(messages.isEmpty() || currentLevel == Boundary.UNKNOWN) return; // prepub: if we still want to impl that per-world history using boundary lines, we'll need to remove this isEmpty -> return condition

		Component boundaryLine = currentLevel.format(makeText(boundaryFormat, currentLevel.levelName(), boundaryColor)); // boundary message itself
		Component lastMessage = messages.getFirst().content();
		boolean lastWasBoundary = Boundary.isBoundaryLine(lastMessage);

		try {
			// if the last message received was a different boundary line, we can delete it - no messages were sent
			if(lastWasBoundary && !lastMessage.getString().equals(boundaryLine.getString())) {
				// todo: delete message method - call here! should delete both real and visible message(s), and update search results however possible (if extra needs to be done)
				messages.removeFirst(); // deletes the useless boundary line

				var visibles = chat.trimmedMessages;
				// removes all associated visible messages (99% of the time this should run once)
				do visibles.removeFirst();
				while(!visibles.isEmpty() && !visibles.getFirst().endOfEntry());
			} else if(lastWasBoundary) {
				return; // if the last message received was the same boundary line we want to send, it's already there
			}

			lastBoundary = currentLevel;
			chat.addMessage(boundaryLine);

		} catch(RuntimeException e) {
			LOGGER.warn("An error occurred while sending the boundary line:", e);
		}
	}

	/**
	 * Calculates the appropriate chat shifting offset to use if
	 * {@link Config#dynamicChatShift} is enabled, which accounts for the player's
	 * visible armor and health bars. If this option is disabled or the player is
	 * null (shouldn't ever happen), simply returns {@link Config#chatShift}.
     * Doesn't dynamically shift if the player is in creative or spectator mode,
	 * as the health and armor bars are not visible.
	 *
	 * @implNote Thanks to {@link #lastShiftState}, only recalculates the shift
	 * if values have changed - avoiding a third-degree polynomial and
	 * floating-point multiplication many times a second!
	 *
	 * @author <a href="https://github.com/radioactive-exe">radioactive-exe</a>!
	 * The majority of this code was contributed in
	 * <a href="https://github.com/mrbuilder1961/ChatPatches/pull/224">#224</a>.
	 */
	public int calcDynamicChatShift() {
		Player player = mc().player;

		if(!config.dynamicChatShift || player == null) {
			return chatShift;
		}
        // don't shift the chat if there are no hearts visible (not in survival or adventure)
		/*? if >=1.21.5 {*/
		if(player.gameMode() == null || !player.gameMode().isSurvival()) {
		/*?} else {*/
        /*if((Object)mc().getConnection().getPlayerInfo(player.getUUID()) instanceof PlayerInfo entry && !entry.getGameMode().isSurvival()) {*/
		/*?}*/
			return chatShift;
		}

		// get player stats and standardize to scaled number of rows
		int armor = player.getArmorValue();
		float absorption = player.getAbsorptionAmount();
		float health = player.getMaxHealth();
		double scale = mc().gui.getChat().getScale();

		int playerState = Objects.hash(armor, absorption, health, scale);
		// if the last player state is the same as the current one and a shift value is available, use it
		if(lastShiftState == playerState && lastDynamicShift >= 0) {
			return lastDynamicShift; // avoids that pesky third-degree polynomial and floating-point multiplication every render tick!
		}

		// calculate health multiplier here to avoid an extra call to PlayerEntity#getHeartRows()
		int armorHeightMultiplier = (armor == 0) ? 0 : 1 + ((armor - 1) / Player.MAX_HEALTH);
		int healthHeightMultiplier = (int) (health + absorption - 1) / Player.MAX_HEALTH;

		//float specificHealthScales[] = {0.75f, 0.6f, 0.5f, 0.45f, 0.3f, 0.3f, 0.3f, 0.3f}; // contingency
		float healthScale = healthHeightMultiplier > 7
			? 0.3f // currently uses a third-degree polynomial regression to calculate the health scale for multipliers under 7
			: 0.00583333f * (float)Math.pow(healthHeightMultiplier, 3) - 0.0722619f * (float)Math.pow(healthHeightMultiplier, 2) + 0.154048f * healthHeightMultiplier + 0.918571f;

		int result = (armorHeightMultiplier * Mth.floor(10 / scale)) + (healthHeightMultiplier * Mth.floor(10 * healthScale / scale)) + chatShift;

		lastShiftState = playerState;
		lastDynamicShift = result;
		return result;
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
		LOGGER.info("Reading...");

		if(!Files.exists(PATH)) {
			config.resetValues();
			LOGGER.info("No config file found; using default values");
			return;
		}

		try {
			// on different lines to make exception line numbers more useful
			String raw = Files.readString(PATH);
			JsonObject json = GsonHelper.parse(raw);

			// note: removed registry-backed JsonOps here bc this loads before the world is available, throwing an NPE. shouldn't matter tho
			config = config.parse(JsonOps.INSTANCE, json)
				.resultOrPartial(e -> logReportMsg(new JsonParseException(e)))
				.orElseThrow();

			LOGGER.info("Read config data from '{}'!", PATH);
		} catch(IOException | NoSuchElementException e) {
			config.resetValues();
			String action = e instanceof NoSuchElementException ? "decode" : "read";
			LOGGER.error("An error occurred while trying to {} config data from '{}', backing up and using default settings:", action, PATH, e);
			backup();
		} catch(RuntimeException e) {
			config.resetValues();
			LOGGER.error("An unexpected error occurred, backing up and using default settings");
			logReportMsg(e);
			backup();
		}
		logDuration(start, IO_THRESHOLD_SUGGESTION);
	}

    /**
     * Saves {@link ChatPatches#config} to {@link #PATH}. <b>Executed on an
	 * {@linkplain Util#ioPool() I/O worker thread} to avoid freezing
	 * the render thread.</b>
     */
    public static void serialize() {
		executeIoTask(() -> {
			long start = System.currentTimeMillis();
			LOGGER.info("Saving...");

			try {
				JsonElement json = config.encodeStart(JsonOps.INSTANCE) // FIXME: i dont like that it doesn't write the config to disk when it's default anymore - not good practice
					.resultOrPartial(e -> logReportMsg(new JsonParseException(e)))
					.orElseThrow();

				String pretty = new GsonBuilder()
					.setPrettyPrinting()
					.disableHtmlEscaping() // also disables non-ASCII characters becoming \\uXXXX codes
					//.serializeNulls() // might be needed in the future; hopefully this can save a few days of agonizing debugging
					.create()
						.toJson(json); // automatically sorts the keys as declared in this class

				Files.writeString(PATH, pretty, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

				LOGGER.info("Saved config data to '{}'!", PATH);
			} catch(IOException | NoSuchElementException e) {
				String action = e instanceof NoSuchElementException ? "encode" : "save";

				LOGGER.error("An error occurred while trying to {} config data to '{}'", action, PATH);
				logReportMsg(e);
			}
			logDuration(start, IO_THRESHOLD_SUGGESTION);
		});
	}

    /**
     * Creates a copy of the current config file located at {@link #PATH} and
     * saves it to {@code chatpatches_${now}.json} in the same directory as the
     * original. If an error occurs, a warning will be logged. Doesn't modify the
     * current config. <b>Executed on an {@linkplain Util#ioPool()
     * I/O worker thread} to avoid freezing the render thread.</b>
     */
    public static void backup() {
		executeIoTask(() -> {
			try {
				Files.copy(PATH, PATH.resolveSibling(MOD_ID + "_" + Util.getFilenameFormattedDateTime() + ".json"));
			} catch(IOException e) {
				LOGGER.warn("An error occurred trying to back up the original config file:", e);
			}
		});
	}


    public ObjectList<Setting<?>> getOptions() {
        Field[] fields = Config.class.getFields();
        ObjectList<Setting<?>> options = new ObjectArrayList<>( fields.length );

        try {
            for(Field f : fields) {
				if(!Modifier.isStatic(f.getModifiers())) {
					options.add(new Setting<>( f.get(config), f.get(DEFAULTS), f.getName() ));
				}
			}
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

	protected void resetValues() {
		config = (config instanceof YaclConfig) ? new YaclConfig() : DEFAULTS;
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
				//noinspection Convert2MethodRef: if >=1.20.5 DataResult.PartialResult no longer exists
				String message = "Failed to parse field '" + opt.key + "': " + result.error().map(e -> e.message()).orElse("<unknown>");
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
                LOGGER.error("An error occurred trying to set config option '{}' to {}", key, obj);
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
        @SuppressWarnings("unchecked") // java is stupid about T casting
		public MapCodec<T> getTypeCodec() {
			Codec<?> codec;

			// ensures the TextColor codec is used
			if(key.contains("Color")) {
				// parses int -> TextColor (migration) and String <-> TextColor (default); the final result is always of type int
				// thx to TheWhyEvenHow: https://discord.com/channels/507304429255393322/721100785936760876/1385863368300040244
				codec =
					/*? if <=1.20.1 {*//*VersionUtil*//*?} elif <=1.20.4 {*//*ExtraCodecs*//*?} else {*/Codec/*?}*/
					.withAlternative(TextColor.CODEC, Codec.INT.xmap(TextColor::fromRgb, TextColor::getValue))
					.xmap(TextColor::getValue, TextColor::fromRgb);
			} else {
				codec = switch(getType().getName()) { // rip 21 pattern matching ;(
					case "java.lang.Boolean", "boolean" -> Codec.BOOL;
					case "java.lang.Integer", "int" -> Codec.INT;
					case "java.lang.String" -> {
						if(key.contains("Format")) {
							yield Codec.STRING.comapFlatMap(
								raw -> raw.contains(PLACEHOLDER)
									? DataResult.success(raw)
									: DataResult.error(() -> "Format string '" + raw + "' for option '" + key + "' is missing a '" + PLACEHOLDER + "' placeholder"),
								Function.identity()
							);
						} else if(key.contains("Date")) {
							yield Codec.STRING.comapFlatMap(
								raw -> {
									try {
										new SimpleDateFormat(raw);
										return DataResult.success(raw);
									} catch(IllegalArgumentException e) {
										return DataResult.error(() -> "Date string '" + raw + "' for option '" + key + "' is not a valid SimpleDateFormat");
									}
								},
								Function.identity()
							);
						} else {
							yield Codec.STRING;
						}
					}
					default -> {
						logReportMsg(new IllegalStateException(String.format("Option '%s' (of type %s) is not a valid type for serialization", key, getType().getName())));
						yield Codec.STRING;
					}
				};
			}

			return ((Codec<T>) codec).optionalFieldOf(key, def);
        }
    }

}