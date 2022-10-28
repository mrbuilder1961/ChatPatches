package mechanicalarcane.wmch.config;

import static mechanicalarcane.wmch.WMCH.LOGGER;
import static mechanicalarcane.wmch.WMCH.config;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.jetbrains.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;

import mechanicalarcane.wmch.WMCH;
import mechanicalarcane.wmch.util.Util;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

/** The config class for WMCH. Extended by {@link ClothConfig} for use with ModMenu. */
public class Config {
    public static final boolean hasModMenu = WMCH.FABRICLOADER.getModContainer("modmenu").isPresent();
    public static final boolean hasClothConfig = WMCH.FABRICLOADER.getModContainer("cloth-config").isPresent();

    // configurable
    public boolean time;
    public String timeStr;
    public String timeFormat;
    public int timeColor;
    public boolean hover;
    public String hoverStr;
    public boolean counter;
    public String counterStr;
    public int counterColor;
    public boolean boundary;
    public String boundaryStr;
    public int boundaryColor;
    public boolean saveChat;
    public boolean shiftHudPos;
    public String nameStr;
    public int maxMsgs;

    protected Config() {
        Option.defaultAll(this);
    }

    /** Creates a new Config or ClothConfig depending on installed Relations. */
    public static Config newConfig() {
        return (hasModMenu && hasClothConfig) ? new ClothConfig() : new Config();
    }


    /**
     * Turns this Config object into a list of usable, properly formatted options
     */
    public void validate() {
        if( !(this instanceof ClothConfig) )
            LOGGER.warn("[Config.validate] Cloth Config and Mod Menu aren't installed in a recent enough version; no in-game config integration available.");

        readFromFile();
        Option.saveAll(this);

        Option.logDiff();
        LOGGER.info("[Config.validate] Finished validating config!");
    }


    /** Adds the {@code TIME_FORMAT} and {@code TIME_STR} strings together, adds a space, then formats them. */
    public MutableText getFormattedTime(Date when) {
        return (
            Util.formatString( Option.TIME_FORMAT.get() + (new SimpleDateFormat(this.timeStr).format(when)) + " " )
        ).fillStyle(Style.EMPTY.withColor(Option.TIME_COLOR.get()));
    }

    /**
     * If the {@code HOVER} option is enabled, returns a Style with a
     * formatted HoverEvent and ClickEvent and {@code TIME_COLOR} applied.
     * Otherwise returns a Style with only {@code TIME_COLOR} applied.
     * @param when The time to use for formatting the hover time string
     */
    public @Nullable Style getHoverStyle(Date when) {
        String hoverText = new SimpleDateFormat(Option.HOVER_STR.get()).format(when);

        return Style.EMPTY
            .withHoverEvent( Option.HOVER.get() ? new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of(hoverText)) : null )
            .withClickEvent( Option.HOVER.get() ? new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, hoverText) : null )
            .withColor(Option.TIME_COLOR.get())
        ;
    }

    public MutableText getFormattedName(GameProfile player) {
        String name = player.getName();
        return Util.formatString(Option.NAME_STR.get().replaceAll("\\$", name) + " ")
            .setStyle( Style.EMPTY
                .withHoverEvent(
                    new HoverEvent(
                        HoverEvent.Action.SHOW_ENTITY,
                        new HoverEvent.EntityContent( net.minecraft.entity.EntityType.PLAYER, player.getId(), Text.of(player.getName()) )
                    )
                )
                .withClickEvent( new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tell " + name + " ") )
            );
    }

    public MutableText getFormattedCounter(int dupes) {
        return
            (Util.formatString(" " + Option.COUNTER_STR.get().replaceAll("\\$", Integer.toString(dupes))))
                .fillStyle(Style.EMPTY.withColor(Option.COUNTER_COLOR.get()));
    }


    /** Loads the config settings saved at {@code ./config/wmch.json} into this Config instance */
    public void readFromFile() {
        try(FileReader fr = new FileReader(Util.CONFIG_PATH)) {
            config = new Gson().fromJson(fr, config.getClass());

            if( !(config instanceof Config) ) {
                reset();
                LOGGER.info("[Config.read] Something was broken, so the config has been reset.");
            }

            // replaces the 'this' Config object's field values with ones from the config file
            for(int i = 0; i < this.getClass().getFields().length; ++i) {
                Field option = this.getClass().getFields()[i];

                // ignores non-public or static fields (ex. logger)
                if( !Modifier.isPublic(option.getModifiers()) || Modifier.isStatic(option.getModifiers()) )
                    continue;

                option.set( this, config.getClass().getFields()[i].get(config) );
            }

            LOGGER.info("[Config.read] Loaded config info from config/wmch.json!");
        } catch(FileNotFoundException e) {
            reset();
            LOGGER.warn("[Config.read] Couldn't find {}'s config file in config/wmch.json; created a default one.", WMCH.NAMES[0], e);
        } catch(Exception e) {
            LOGGER.error("[Config.read] An error occurred while trying to load {}'s config data; resetting:", WMCH.NAMES[0], e);
            reset();
        }
    }

    /** Saves this {@code Config} instance to {@code ./config/wmch.json} */
    public void writeToFile() {
        try(FileWriter fw = new FileWriter(Util.CONFIG_PATH)) {
            new GsonBuilder()
                .excludeFieldsWithModifiers(Modifier.STATIC)
                .setPrettyPrinting()
            .create()
                .toJson(this, this.getClass(), fw);

            LOGGER.info("[Config.write] Saved config info to config/wmch.json!");
        } catch(Exception e) {
            LOGGER.error("[Config.write] An error occurred while trying to save {}'s config data:", WMCH.NAMES[0], e);
        }
    }

    /** Overwrites the {@code WMCH.config} object with default values and saves it */
    public static void reset() {
        config = Config.newConfig();
        config.writeToFile();
    }
}