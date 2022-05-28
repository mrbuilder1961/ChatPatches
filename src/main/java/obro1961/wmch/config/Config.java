package obro1961.wmch.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Modifier;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.logging.log4j.Logger;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import obro1961.wmch.Util;
import obro1961.wmch.WMCH;

/**
 * The config class for WMCH. Extended by others for external versions.
 *
 * @see ClothConfig
 */
public class Config {
    protected static final Logger lg = WMCH.log;
    protected static Config cfg = new Config();

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
    public int dupeThreshold;
    public boolean leniantEquals;
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
        this.dupeThreshold = Option.DUPETHRESHOLD.getDefault();
        this.leniantEquals = Option.LENIANTEQUALS.getDefault();
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
        if (!WMCH.inGameConfig) lg.warn("Cloth config v{}+ and Mod Menu v{}+ are not installed, so the config can only be accessed through the JSON file.", WMCH.VERSION_REQS[0], WMCH.VERSION_REQS[1]);
        read();

        Option.TIME.onSave(cfg.time, Option.TIME);
        Option.TIMESTR.onSave(cfg.timeStr, Option.TIMESTR);
        Option.TIMEFORMAT.onSave(cfg.timeFormat, Option.TIMEFORMAT);
        Option.TIMECOLOR.onSave(cfg.timeColor, Option.TIMECOLOR);

        Option.HOVER.onSave(cfg.hover, Option.HOVER);
        Option.HOVERSTR.onSave(cfg.hoverStr, Option.HOVERSTR);

        Option.BOUNDARY.onSave(cfg.boundary, Option.BOUNDARY);
        Option.BOUNDARYSTR.onSave(cfg.boundaryStr, Option.BOUNDARYSTR);
        Option.BOUNDARYCOLOR.onSave(cfg.boundaryColor, Option.BOUNDARYCOLOR);

        Option.COUNTER.onSave(cfg.counter, Option.COUNTER);
        Option.COUNTERSTR.onSave(cfg.counterStr, Option.COUNTERSTR);
        Option.COUNTERCOLOR.onSave(cfg.counterColor, Option.COUNTERCOLOR);
        //Option.DUPETHRESHOLD.onSave(cfg.dupeThreshold, Option.DUPETHRESHOLD);
        Option.LENIANTEQUALS.onSave(cfg.leniantEquals, Option.LENIANTEQUALS);

        Option.NAMESTR.onSave(cfg.nameStr, Option.NAMESTR);
        Option.MAXMSGS.onSave(cfg.maxMsgs, Option.MAXMSGS);

        logDiffs();
        WMCH.config = cfg;
        lg.info("Finished saving config!");
    }

    /** Formats timeStr, then uses that to format timeFormat, adds a space, then finally colors it */
    public Text getTimeF(Date when) {
        return ((LiteralText)Util.getStrTextF(Option.TIMEFORMAT.get() + (new SimpleDateFormat(cfg.timeStr).format(when)) + " "))
            .fillStyle(Style.EMPTY.withColor(Option.TIMECOLOR.get()));
    }
    public String getHoverF(Date when) {
        return new SimpleDateFormat(Option.HOVERSTR.get()).format(when);
    }
    public String getNameF(String name) {
        return Option.NAMESTR.get().replaceAll("\\$", name);
    }
    public Text getDupeF(int dupes) {
        return ((LiteralText)Util.getStrTextF(" " + Option.COUNTERSTR.get().replaceAll("\\$", Integer.toString(dupes))))
            .fillStyle(Style.EMPTY.withColor(Option.COUNTERCOLOR.get()));
    }

    /** Prints any changes between altering of Options */
    protected static void logDiffs() {
        // only log if changes were made
        if (Option.diff == "Changes made:") lg.info("No changes made!");
        else lg.info(Option.diff);
        Option.diff = "Changes made:";
    }

    /** Sets the {@code cfg} field in {@link Config} to the file at {@code ./config/wmch.json} */
    public static void read() {
        File cfgFile = new File( FabricLoader.getInstance().getConfigDir().toFile().getAbsolutePath() + File.separator + "wmch.json" );
        Gson gson = new Gson();

        try (FileReader fr = new FileReader(cfgFile)) {
            if (!cfgFile.exists()) {
                reset();
                lg.warn("Could not find wmch's config file (searched in '{}'); created a default one.", cfgFile.getAbsolutePath());
            } else {
                Config.cfg = gson.fromJson(fr, WMCH.config.getClass());

                if (cfg == null || !(cfg instanceof Config)) {
                    reset();
                    lg.info("Something was broken, so the config has been reset.");
                }

                lg.debug("Loaded config info {} from '{}'", cfg, cfgFile.getAbsolutePath());
            }
        } catch (Exception e) {
            lg.error("An error occurred while trying to load wmch's config data; resetting:");
            e.printStackTrace();
            reset();
        }
    }

    /**
     * Saves a Config to {@code ./config/wmch.json}
     * @param c The Config to save
     */
    public static void write(Config c) {
        String cfgPath = FabricLoader.getInstance().getConfigDir().toFile().getAbsolutePath() + File.separator + "wmch.json";

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
        Config.cfg = WMCH.config = new Config();
        write(cfg);
    }

    @Override
    public String toString() {
        return new GsonBuilder().setPrettyPrinting().create().toJson(this);
    }
}