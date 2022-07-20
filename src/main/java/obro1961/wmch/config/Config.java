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
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import obro1961.wmch.WMCH;
import obro1961.wmch.util.Util;

/**
 * The config class for WMCH. Extended by others for external versions.
 *
 * @see ClothConfig
 */
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
    public boolean saveChat;
    public boolean boundary;
    public String boundaryStr;
    public String boundaryFormat;
    public int boundaryColor;
    public String nameStr;
    public int maxMsgs;

    public Config() {
        this.time = Option.TIME.getDefault();
        this.timeStr = Option.TIMESTR.getDefault();
        this.timeFormat = Option.TIMEFORMAT.getDefault();
        this.timeColor = Option.TIMECOLOR.getDefault();
        this.hover = Option.HOVER.getDefault();
        this.hoverStr = Option.HOVERSTR.getDefault();
        this.counter = Option.COUNTER.getDefault();
        this.counterStr = Option.COUNTERSTR.getDefault();
        this.counterColor = Option.COUNTERCOLOR.getDefault();
        this.saveChat = Option.SAVECHAT.getDefault();
        this.boundary = Option.BOUNDARY.getDefault();
        this.boundaryStr = Option.BOUNDARYSTR.getDefault();
        this.boundaryColor = Option.BOUNDARYCOLOR.getDefault();
        this.nameStr = Option.NAMESTR.getDefault();
        this.maxMsgs = Option.MAXMSGS.getDefault();
    }

    /**
     * Turns the config into usable, publicly accessible values.
     */
    public static void validate() {
        if (!WMCH.inGameConfig)
            lg.warn("Cloth config v{}+ and Mod Menu v{}+ are not installed, so the config can only be accessed through the JSON file.",
                    WMCH.DEPENDENTS[0], WMCH.DEPENDENTS[1]);
        read();

        Option.TIME.onSave(config.time, Option.TIME);
        Option.TIMESTR.onSave(config.timeStr, Option.TIMESTR);
        Option.TIMEFORMAT.onSave(config.timeFormat, Option.TIMEFORMAT);
        Option.TIMECOLOR.onSave(config.timeColor, Option.TIMECOLOR);

        Option.HOVER.onSave(config.hover, Option.HOVER);
        Option.HOVERSTR.onSave(config.hoverStr, Option.HOVERSTR);

        Option.BOUNDARY.onSave(config.boundary, Option.BOUNDARY);
        Option.BOUNDARYSTR.onSave(config.boundaryStr, Option.BOUNDARYSTR);
        Option.BOUNDARYCOLOR.onSave(config.boundaryColor, Option.BOUNDARYCOLOR);

        Option.COUNTER.onSave(config.counter, Option.COUNTER);
        Option.COUNTERSTR.onSave(config.counterStr, Option.COUNTERSTR);
        Option.COUNTERCOLOR.onSave(config.counterColor, Option.COUNTERCOLOR);

        Option.SAVECHAT.onSave(config.saveChat, Option.SAVECHAT);
        Option.NAMESTR.onSave(config.nameStr, Option.NAMESTR);
        Option.MAXMSGS.onSave(config.maxMsgs, Option.MAXMSGS);

        logDiffs();
        lg.info("Finished saving config!");
    }

    /**
     * Formats timeStr, uses that to format timeFormat, adds a space, then
     * finally colors it
     */
    public Text getTimeF(Date when) {
        return ((LiteralText) Util
                .getStrTextF(Option.TIMEFORMAT.get() + (new SimpleDateFormat(config.timeStr).format(when)) + " "))
                .fillStyle(Style.EMPTY.withColor(Option.TIMECOLOR.get()));
    }

    public String getHoverF(Date when) {
        return new SimpleDateFormat(Option.HOVERSTR.get()).format(when);
    }

    public String getNameF(String name) {
        return Option.NAMESTR.get().replaceAll("\\$", name);
    }

    public Text getDupeF(int dupes) {
        return ((LiteralText) Util
                .getStrTextF(" " + Option.COUNTERSTR.get().replaceAll("\\$", Integer.toString(dupes))))
                .fillStyle(Style.EMPTY.withColor(Option.COUNTERCOLOR.get()));
    }

    /** Prints any changes between altering of Options */
    protected static void logDiffs() {
        // only log if changes were made
        if (Option.diff == "Changes made:")
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
            if (!cfgFile.exists()) {
                reset();
                lg.warn("Could not find wmch's config file (searched in '{}'); created a default one.", cfgFile.getAbsolutePath());
            } else {
                config = new Gson().fromJson(fr, config.getClass());

                if (config == null || !(config instanceof Config)) {
                    reset();
                    lg.info("Something was broken, so the config has been reset.");
                }

                lg.debug("Loaded config info {} from '{}'", config, cfgFile.getAbsolutePath());
            }
        } catch (Exception e) {
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
        } catch (Exception e) {
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