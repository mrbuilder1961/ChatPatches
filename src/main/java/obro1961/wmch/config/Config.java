package obro1961.wmch.config;

import static obro1961.wmch.WMCH.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Modifier;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import obro1961.wmch.WMCH;
import obro1961.wmch.util.Util;

/** The config class for WMCH. Extended by {@link ClothConfig} for use with ModMenu. */
public class Config {
    protected static final Logger lg = WMCH.log;

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
    public String boundaryFormat;
    public int boundaryColor;
    public boolean saveChat;
    //public boolean hideUnsecureNotif;
    public String nameStr;
    public int maxMsgs;

    public Config() {
        this.time = Option.TIME.getDefault();
        this.timeStr = Option.TIME_STR.getDefault();
        this.timeFormat = Option.TIME_FORMAT.getDefault();
        this.timeColor = Option.TIME_COLOR.getDefault();
        this.hover = Option.HOVER.getDefault();
        this.hoverStr = Option.HOVER_STR.getDefault();
        this.counter = Option.COUNTER.getDefault();
        this.counterStr = Option.COUNTER_STR.getDefault();
        this.counterColor = Option.COUNTER_COLOR.getDefault();
        this.boundary = Option.BOUNDARY.getDefault();
        this.boundaryStr = Option.BOUNDARY_STR.getDefault();
        this.boundaryColor = Option.BOUNDARY_COLOR.getDefault();
        this.saveChat = Option.SAVE_CHAT.getDefault();
        //this.hideUnsecureNotif = Option.HIDE_UNSECURE_NOTIF.getDefault();
        this.nameStr = Option.NAME_STR.getDefault();
        this.maxMsgs = Option.MAX_MESSAGES.getDefault();
    }

    /**
     * Turns the config into usable, publicly accessible values.
     */
    public static void validate() {
        if(!WMCH.inGameConfig)
            lg.warn("Cloth config v{}+ and Mod Menu v{}+ are not installed, so the config can only be accessed through the JSON file.", WMCH.DEPENDENTS[0], WMCH.DEPENDENTS[1]);
        read();

        Option.TIME.save(config.time);
        Option.TIME_STR.save(config.timeStr);
        Option.TIME_FORMAT.save(config.timeFormat);
        Option.TIME_COLOR.save(config.timeColor);

        Option.HOVER.save(config.hover);
        Option.HOVER_STR.save(config.hoverStr);

        Option.BOUNDARY.save(config.boundary);
        Option.BOUNDARY_STR.save(config.boundaryStr);
        Option.BOUNDARY_COLOR.save(config.boundaryColor);

        Option.COUNTER.save(config.counter);
        Option.COUNTER_STR.save(config.counterStr);
        Option.COUNTER_COLOR.save(config.counterColor);

        Option.SAVE_CHAT.save(config.saveChat);
        //Option.HIDE_UNSECURE_NOTIF.onSave(config.hideUnsecureNotif);
        Option.NAME_STR.save(config.nameStr);
        Option.MAX_MESSAGES.save(config.maxMsgs);

        logDiffs();
        lg.info("Finished saving config!");
    }

    /**
     * Formats timeStr, uses that to format timeFormat, adds a space, then
     * finally colors it
     */
    public MutableText getTimeF(Date when) {
        return
            (Util.getStrTextF(Option.TIME_FORMAT.get() + (new SimpleDateFormat(config.timeStr).format(when)) + " "))
                .fillStyle(Style.EMPTY.withColor(Option.TIME_COLOR.get()));
    }

    public String getHoverF(Date when) {
        return new SimpleDateFormat(Option.HOVER_STR.get()).format(when);
    }

    public String getNameF(String name) {
        return Option.NAME_STR.get().replaceAll("\\$", name);
    }

    public MutableText getDupeF(int dupes) {
        return
            (Util.getStrTextF(" " + Option.COUNTER_STR.get().replaceAll("\\$", Integer.toString(dupes))))
                .fillStyle(Style.EMPTY.withColor(Option.COUNTER_COLOR.get()));
    }

    /** Prints any changes between altering of Options */
    protected static void logDiffs() {
        // only log if changes were made
        if(Option.diff == "Changes made:")
            lg.info("No changes made!");
        else
            lg.info(Option.diff);
        Option.diff = "Changes made:";
    }

    /**
     * Sets the {@code cfg} field in {@link Config} to the file at
     * {@code ./config/wmch.json}
     */
    public static void read() {
        final File cfgFile = new File(FabricLoader.getInstance().getConfigDir().toFile().getAbsolutePath() + "/wmch.json");

        try (FileReader fr = new FileReader(cfgFile)) {
            if(!cfgFile.exists()) {
                reset();
                lg.warn("Could not find wmch's config file (searched in '{}'); created a default one.", cfgFile.getAbsolutePath());
            } else {
                config = new Gson().fromJson(fr, config.getClass());

                if(config == null || !(config instanceof Config)) {
                    reset();
                    lg.info("Something was broken, so the config has been reset.");
                }

                lg.debug("Loaded config info {} from '{}'", config, cfgFile.getAbsolutePath());
            }
        } catch(Exception e) {
            lg.error("An error occurred while trying to load wmch's config data; resetting:");
            e.printStackTrace();
            reset();
        }
    }

    /**
     * Saves a Config to {@code ./config/wmch.json}
     *
     * @param c The Config to save
     */
    public static void write(Config c) {
        String cfgPath = WMCH.fbl.getConfigDir().toFile().getAbsolutePath() + "/wmch.json";

        try (FileWriter fw = new FileWriter(cfgPath)) {
            new GsonBuilder().excludeFieldsWithModifiers(Modifier.STATIC).setPrettyPrinting().create().toJson(c, c.getClass(), fw);
            lg.debug("Saved config info {} from '{}'", c.toString(), cfgPath);
        } catch(Exception e) {
            lg.error("An error occurred while trying to save wmch's config data:");
            e.printStackTrace();
        }
    }

    /**
     * Overwrites the {@code cfg} object and {@code WMCH.config} with default values
     * and writes it to {@code ./config/wmch.json}
     */
    public static void reset() {
        WMCH.config = new Config();
        write(config);
    }

    @Override
    public String toString() {
        return new GsonBuilder().setPrettyPrinting().create().toJson(this);
    }
}