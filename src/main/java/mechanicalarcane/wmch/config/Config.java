package mechanicalarcane.wmch.config;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import mechanicalarcane.wmch.WMCH;
import mechanicalarcane.wmch.util.Util;
import net.minecraft.entity.EntityType;
import net.minecraft.text.*;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static mechanicalarcane.wmch.WMCH.LOGGER;
import static mechanicalarcane.wmch.WMCH.config;
import static mechanicalarcane.wmch.util.Util.fillVars;

public class Config {
    private static final Config DEFAULTS = new Config();
    public static final boolean hasModMenu = WMCH.FABRICLOADER.isModLoaded("modmenu");
    public static final boolean hasClothConfig = WMCH.FABRICLOADER.isModLoaded("cloth-config");

    // configurable
    public boolean time = true, hover = true, counter = true, boundary = true;
    public String timeDate = "HH:mm:ss", hoverDate = "MM/dd/yyyy";
    public String counterStr = "&8(&7x&e$&8)", boundaryStr = "&8[&b$&8]";
    public String timeFormat = "[$]", hoverFormat = "$";
    public int timeColor = 0xff55ff, hoverColor = 0xffffff, counterColor = 0xffff55, boundaryColor = 0x55ffff;
    public boolean saveChat = true;
    public int shiftChat = 10;
    public String nameStr = "$";
    public int maxMsgs = 16384;


    /** Creates a new Config or ClothConfig depending on installed mods. */
    public static Config newConfig(boolean reset) {
        config = (hasModMenu && hasClothConfig) ? new ClothConfig() : new Config();
        if(!reset)
            read();
        return config;
    }


    /** Returns all Config options as a List of string keys and class types that can be used with {@link #getOption(String)}. */
    public static List<Option<?>> getOptions() {
        List<Option<?>> options = new ArrayList<>( Config.class.getDeclaredFields().length );

        for(Field opt : Config.class.getDeclaredFields()) {
            if(Modifier.isStatic( opt.getModifiers() ))
                continue;

            options.add( Config.getOption(opt.getName()) );
        }

        return options;
    }

    /** Returns the value of the option in the {@link WMCH#config} identified by {@code key}. */
    @SuppressWarnings("unchecked")
    public static <T> Option<T> getOption(String key) {
        try {
            return new Option<>( (T)config.getClass().getField(key).get(config), (T)Config.class.getField(key).get(DEFAULTS), key );

        } catch (IllegalAccessException | NoSuchFieldException e) {
            LOGGER.error("[Config.getOption({})] An error occurred while trying to get an option value, please report this on GitHub:", key, e);

            return new Option<>( (T)new Object(), (T)new Object(), key );
        }
    }


    /** Adds the {@code TIME_FORMAT} and {@code TIME_STR} strings together, adds a space, then formats them. */
    public MutableText getFormattedTime(Date when) {
        return (
            Util.formatString( fillVars(timeFormat, new SimpleDateFormat(timeDate).format(when)) + " " )
        ).fillStyle( Style.EMPTY.withColor(timeColor) );
    }

    /**
     * If the {@code HOVER} option is enabled, returns a Style with a
     * formatted HoverEvent and ClickEvent and {@code TIME_COLOR} applied.
     * Otherwise, returns a Style with only {@code TIME_COLOR} applied.
     * @param when The time to use for formatting the hover time string
     */
    public Style getHoverStyle(Date when) {
        MutableText hoverText = Util.formatString(
            fillVars(hoverFormat, new SimpleDateFormat(hoverDate).format(when))
        ).fillStyle( Style.EMPTY.withColor(hoverColor) );

        return Style.EMPTY
            .withHoverEvent( hover ? new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText) : null )
            .withClickEvent( hover ? new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, hoverText.getString()) : null )
            .withColor(timeColor)
        ;
    }

    public MutableText getFormattedName(GameProfile player) {
        String name = player.getName();
        return Util.formatString( fillVars(nameStr, name) + " " )
            .setStyle( Style.EMPTY
                .withHoverEvent(
                    new HoverEvent(
                        HoverEvent.Action.SHOW_ENTITY,
                        new HoverEvent.EntityContent( EntityType.PLAYER, player.getId(), Text.of(player.getName()) )
                    )
                )
                .withClickEvent( new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tell " + name + " ") )
            );
    }

    public MutableText getFormattedCounter(int dupes) {
        return
            Util.formatString(" " + fillVars(counterStr, Integer.toString(dupes)))
                .fillStyle( Style.EMPTY.withColor(counterColor) );
    }

    public MutableText getFormattedBoundary(String levelName) {
        return
            Util.formatString( fillVars(boundaryStr, levelName) )
                .fillStyle(Style.EMPTY.withColor(boundaryColor));
    }


    /** Loads the config settings saved at {@link Util#CONFIG_PATH} into this Config instance */
    public static void read() {
        try(FileReader fr = new FileReader(Util.CONFIG_PATH)) {
            config = new Gson().fromJson(fr, config.getClass());

            LOGGER.info("[Config.read] Loaded config info from '{}'!", Util.CONFIG_PATH);
        } catch(JsonIOException | JsonSyntaxException e) {

            LOGGER.info("[Config.read] The config couldn't be loaded; copying old data and resetting...");
            writeCopy();
            reset();

        } catch(IOException e) {
            reset();
            LOGGER.error("[Config.read] An error occurred while trying to load config data from '{}':", Util.CONFIG_PATH, e);
        }
    }

    /** Saves the {@code WMCH.config} instance to {@link Util#CONFIG_PATH} */
    public static void write() {
        try(FileWriter fw = new FileWriter(Util.CONFIG_PATH)) {

            new GsonBuilder()
                .excludeFieldsWithModifiers(Modifier.STATIC)
                .setPrettyPrinting()
            .create()
                .toJson(config, config.getClass(), fw);

            LOGGER.info("[Config.write] Saved config info to '{}'!", Util.CONFIG_PATH);
        } catch(Exception e) {
            LOGGER.error("[Config.write] An error occurred while trying to save config data to '{}':", Util.CONFIG_PATH, e);
        }
    }

    /** Overwrites the {@code WMCH.config} object with default values and saves it */
    public static void reset() {
        config = Config.newConfig(true);
        write();
    }

    /** Copies the current Config file data to {@code ./wmch_old.json} for copying old configurations over */
    public static void writeCopy() {
        try(
            FileInputStream cfg = new FileInputStream(Util.CONFIG_PATH);
            FileOutputStream copy = new FileOutputStream(Util.CONFIG_PATH.replace("wmch", "wmch_old"))
        ) {
            copy.write( cfg.readAllBytes() );
        } catch (IOException e) {
            LOGGER.error("An error occurred trying to copy the original config file from '{}':", Util.CONFIG_PATH, e);
        }
    }

    /**
     * A simple Option class that wraps the internally-used
     * String/Class pair for each Config field. This is
     * merely an abstraction used for simplification.
     */
    public static class Option<T> {
        private T val;
        public final T def;
        public final String key;

        /**
         * Creates a new Simple Config option.
         * @param def The default value for creation and resetting.
         * @param key The lang key of the Option; for identification
         */
        public Option(T val, T def, String key) {
            this.val = Objects.requireNonNull(val, "Cannot create a ConfigOption without a default value");
            this.def = Objects.requireNonNull(def, "Cannot create a ConfigOption without a default value");
            this.key = Objects.requireNonNull(key, "Cannot create a ConfigOption without a key");
        }


        public T get() { return val; }

        /**
         * Sets this Option's value to {@code obj} in {@code this} and also in the config;
         * assuming {@code obj.getClass().equals(T.class)} returns true.
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
            } catch (NoSuchFieldException | IllegalAccessException | ClassCastException e) {
                LOGGER.error("[ConfigOption.set({})] An error occurred trying to set a config option:", obj, e);
            }
        }
        public void set(Object obj) {
            this.set(obj, true);
        }

        public boolean changed() {
            return !val.equals(def);
        }

        public void reset() {
            this.set( this.val = def );
        }
    }
}