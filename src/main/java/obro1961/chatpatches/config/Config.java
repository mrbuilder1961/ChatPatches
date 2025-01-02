package obro1961.chatpatches.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.mojang.authlib.GameProfile;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.Team;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.*;
import net.minecraft.util.Util;
import obro1961.chatpatches.ChatPatches;
import obro1961.chatpatches.accessor.ChatHudAccessor;
import obro1961.chatpatches.util.ChatUtils;

import java.io.EOFException;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static obro1961.chatpatches.ChatPatches.LOGGER;
import static obro1961.chatpatches.ChatPatches.config;
import static obro1961.chatpatches.util.RenderUtils.BLANK_STYLE;
import static obro1961.chatpatches.util.TextUtils.fillVars;
import static obro1961.chatpatches.util.TextUtils.text;

public class Config {
    public static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("chatpatches.json");
    public static final Config DEFAULTS = new Config();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    /** @see #sendBoundaryLine() */
    private static String lastWorld = "";

	// categories: time, hover, counter, counter.compact, boundary, chatlog, chat.hud, chat.screen, copy, search
    public boolean time = true; public String timeDate = "HH:mm:ss"; public String timeFormat = "[$]"; public int timeColor = 0xff55ff;
    public boolean hover = true; public String hoverDate = "MM/dd/yyyy"; public String hoverFormat = "$"; public int hoverColor = 0xffffff;
    public boolean counter = true; public String counterFormat = "&8(&7x&r$&8)"; public int counterColor = 0xffff55; public boolean counterCheckStyle = false;
    public boolean counterCompact = false; public int counterCompactDistance = 0;
    public boolean boundary = true; public String boundaryFormat = "&8[&r$&8]"; public int boundaryColor = 0x55ffff;
    public boolean chatlog = true; public int chatlogSaveInterval = 0;
    public boolean chatHidePacket = true; public int chatWidth = 0, chatMaxMessages = 16384; public String chatNameFormat = "<$>"; public int chatNameColor = 0xffffff;
    public int shiftChat = 10; public boolean contextMenu = true, hideSearchButton = false, messageDrafting = false, onlyInvasiveDrafting = false, searchDrafting = true, vanillaClearing = false, searchPrefix =
        false;
    public int copyColor = 0x55ffff; public String copyReplyFormat = "/msg $ ";
    public boolean caseSensitive = true, formatting = false, regex = false;

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
            Text.translatable("text.chatpatches.help.missing"),
            Text.translatable("text.chatpatches.desc.help.missing", (suggestYACL ? "YACL" : "Cloth Config")),
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

    /** Formats the provided playername, using {@link #chatNameFormat},
     * {@link #chatNameColor}, and the player's team properties. Uses
     * the player's team color if set, otherwise {@link #chatNameColor}.
     * Hover and click events are sourced from the style of
     * {@link PlayerEntity#getDisplayName()}.
     *
     * @implNote {@code player} must reference a valid, existing
     * player entity and have both a valid name and UUID.
     */
    public MutableText formatPlayername(GameProfile profile) {
        Style style = BLANK_STYLE.withColor(chatNameColor);
        try {
			PlayerEntity entity = mc.world.getPlayerByUuid(profile.getId());
            Team team = null;

            if(entity != null) {
                team = entity.getScoreboard().getPlayerTeam(profile.getName());
                style = entity.getDisplayName().getStyle().withColor( entity.getTeamColorValue() != 0xffffff ? entity.getTeamColorValue() : chatNameColor );
            }

            if(team != null) {
                // note: doesn't set the style on every append, as it's already set in the parent text. might cause issues?
                // if the player is on a team, add the prefix and suffixes from the config AND team (if they exist) to the formatted name
                MutableText playername = text(profile.getName());
                String[] configFormat = chatNameFormat.split("\\$");
                Text configPrefix = text(configFormat[0]);
                Text configSuffix = text(configFormat[1] + " ");

                return Text.empty().setStyle(style)
                    .append(configPrefix)
                    .append(team.getPrefix())
                    .append(playername)
                    .append(team.getSuffix())
                    .append(configSuffix);
            }
        } catch(Exception e) {
            LOGGER.error("[Config.formatPlayername] /!\\ An error occurred while trying to format '{}'s playername /!\\", profile.getName());
            ChatPatches.logReportMsg(e);
        }

        return makeObject(chatNameFormat, profile.getName(), "", " ", style);
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
        if(!config.boundary || config.vanillaClearing)
            return;

        ChatHudAccessor chat = (ChatHudAccessor) mc.inGameHud.getChatHud();
		//noinspection DataFlowIssue: see next line
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
                mc.inGameHud.getChatHud().addMessage( config.makeBoundaryLine(levelName) );
                config.time = time; // re-enables the time accordingly
            } catch(Exception e) {
                LOGGER.warn("[Config.sendBoundaryLine] An error occurred while adding the boundary line:", e);
            }
        }
    }


    /** Loads the config settings saved at {@link Config#PATH} into {@link ChatPatches#config} */
    public static void read() {
        if(Files.exists(PATH)) {
            try {//todo: make sure this works
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
        } catch(Exception e) {
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


    /**
     * Returns all Config options as a List of string keys and class types that can be used with {@link #getOption(String)}.
     */
    public static List<ConfigOption<?>> getOptions() {
        List<ConfigOption<?>> options = new ArrayList<>( Config.class.getDeclaredFields().length );

        for(Field field : Config.class.getDeclaredFields())
            if(!Modifier.isStatic( field.getModifiers() ))
                options.add( getOption(field.getName()) );

        return options;
    }

    /**
     * Returns the {@link ConfigOption} with field name
     * {@code key}, as configured in {@link ChatPatches#config}.
     * Logs an error if the field doesn't exist and returns
     * a blank ConfigOption with the specified key.
     */
    @SuppressWarnings("unchecked")
    public static <T> ConfigOption<T> getOption(String key) {
        try {
            return new ConfigOption<>( (T)config.getClass().getField(key).get(config), (T)config.getClass().getField(key).get(DEFAULTS), key );
        } catch(IllegalAccessException | NoSuchFieldException e) {
            LOGGER.error("[Config.getOption({})] An error occurred while trying to get an option value!", key);
            ChatPatches.logReportMsg(e);

            return new ConfigOption<>( (T)new Object(), (T)new Object(), key );
        }
    }

    /**
     * A simple Option class that wraps the internally-used
     * String/Class pair for each Config field. This is
     * merely an abstraction used for simplification.
     */
    public static class ConfigOption<T> {
        private T val;
        public final T def;
        public final String key;

        /**
         * Creates a new Simple Config option.
         * @param def The default value for creation and resetting.
         * @param key The lang key of the Option; for identification
         */
        public ConfigOption(T val, T def, String key) {
            this.val = Objects.requireNonNull(val, "Cannot create a ConfigOption without a default value");
            this.def = Objects.requireNonNull(def, "Cannot create a ConfigOption without a default value");
            this.key = Objects.requireNonNull(key, "Cannot create a ConfigOption without a key");
        }


        public T get() { return val; }

        @SuppressWarnings("unchecked")
        public Class<T> getType() { return (Class<T>) def.getClass(); }

        /**
         * Sets this Option's value to {@code obj} in {@code this} and also in the config;
         * assuming {@code obj.getClass().equals(T.class)} is true.
         * @param obj The new object to replace the old one with
         * @param set If false, doesn't change the value. For no check, see
         * {@link #set(Object)}
         */
        public void set(Object obj, boolean set) {
            try {
                @SuppressWarnings("unchecked")
                T inc = (T) obj;

                if( inc != null && !inc.equals(val) && set ) {
                    config.getClass().getField(key).set(config, inc);

                    this.val = inc;
                }
            } catch(NoSuchFieldException | IllegalAccessException | ClassCastException e) {
                LOGGER.error("[ConfigOption.set({})] An error occurred trying to set a config option", obj);
                ChatPatches.logReportMsg(e);
            }
        }

        public void set(Object obj) {
            this.set(obj, true);
        }

        public boolean changed() {
            return !val.equals(def);
        }
    }
}