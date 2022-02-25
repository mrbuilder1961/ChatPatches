package obro1961.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Modifier;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.logging.log4j.Logger;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Formatting;
import obro1961.Util;
import obro1961.WMCH;

/**
 * The config class for WMCH. Extended by others for external versions.
 * @see ClothConfig
 */
public class Config {
    protected static Logger lg = WMCH.log; // Just shortens lots of log references
    protected static final Formatting[] EMPTY = new Formatting[0];
    protected static Config cfg;
    protected static String diff = ""; // config change log
    protected static Object diffObj;

    // default values
    public static final boolean TIME = true;
    public static final String TIMESTR = "[HH:mm:ss]";
    public static final Formatting[] TIMEFORMATTING = new Formatting[] {Formatting.LIGHT_PURPLE};
    public static final boolean HOVER = true;
    public static final String HOVERSTR = "yyyy dd, MM, @ HH:mm:ss.SSSS";
    public static final boolean BOUNDARY = true;
    public static final String BOUNDARYSTR = "<]===---{ SESSION BOUNDARY LINE }---===[>";
    public static final Formatting[] BOUNDARYFORMATTING = new Formatting[] {Formatting.DARK_AQUA, Formatting.BOLD};
    public static final String NAMESTR = "<$>";
    public static final int MAXMSGS = 1024;
    public static final boolean RESET = false;

    // currently used
    public boolean time = TIME;
    public String timeStr = TIMESTR;
    public Formatting[] timeFormatting = TIMEFORMATTING;
    public boolean hover = HOVER;
    public String hoverStr = HOVERSTR;
    public boolean boundary = BOUNDARY;
    public String boundaryStr = BOUNDARYSTR;
    public Formatting[] boundaryFormatting = BOUNDARYFORMATTING;
    public String nameStr = NAMESTR;
    public int maxMsgs = MAXMSGS;
    public boolean reset = RESET;

    // for converting
    protected boolean time_enabled;
    protected String time_text;
    protected Formatting[] time_formatting;
    protected boolean hover_enabled;
    protected String hover_string;
    protected boolean boundary_enabled;
    protected String boundary_string;
    protected Formatting[] boundary_formatting;
    protected int max_messages;


    public Config(boolean resetCfgObj) {
        this.time = TIME;
        this.timeStr = TIMESTR;
        this.timeFormatting = TIMEFORMATTING;
        this.hover = HOVER;
        this.hoverStr = HOVERSTR;
        this.boundary = BOUNDARY;
        this.boundaryStr = BOUNDARYSTR;
        this.boundaryFormatting = BOUNDARYFORMATTING;
        this.maxMsgs = MAXMSGS;
        this.reset = RESET;
        this.nameStr = NAMESTR;

        if(resetCfgObj) WMCH.config = this;
    }
    protected Config(boolean timeEnabled, String timeString, Formatting[] timeFormattings, boolean hoverEnabled, String hoverString, boolean boundaryEnabled, String boundaryString, Formatting[] boundaryFormattings, int maxMessages, boolean resetOptions, String name) {
        this.time = timeEnabled;
        this.timeStr = timeString;
        this.timeFormatting = timeFormattings;
        this.hover = hoverEnabled;
        this.hoverStr = hoverString;
        this.boundary = boundaryEnabled;
        this.boundaryStr = boundaryString;
        this.boundaryFormatting = boundaryFormattings;
        this.maxMsgs = maxMessages;
        this.reset = resetOptions;
        this.nameStr = name;
    }
    /** Returns a ClothConfig if available, otherwise returns null. */
    public ClothConfig getClothConfig() {
        if(WMCH.isConfigModded) return new ClothConfig(this.time, this.timeStr, this.timeFormatting, this.hover, this.hoverStr, this.boundary, this.boundaryStr, this.boundaryFormatting, this.maxMsgs, this.reset, this.nameStr);
        else return null;
    }

    /** Turns the config into usable, publicly accessible values. Quite a mess... */
    public static void validateConfig() {
        if(!WMCH.isConfigModded) lg.warn("Cloth config v6.1.48+ and Mod Menu v3.0.1+ are not installed, config can only be accessed by the file. Make sure your mods are in the right folder and are the right version or higher.");
        read();

        cfg.time = (Boolean)Util.or(cfg.time, cfg.time_enabled, TIME);
        cfg.hover = (Boolean)Util.or(cfg.hover, cfg.hover_enabled, HOVER);
        cfg.boundary = (Boolean)Util.or(cfg.boundary, cfg.boundary_enabled, BOUNDARY);
        cfg.boundaryStr = (String)Util.or(cfg.boundaryStr, cfg.boundary_string, BOUNDARYSTR);

        try {
            cfg.timeStr = ((String)Util.or(cfg.timeStr, cfg.time_text, TIMESTR)).replaceAll("'","").replaceAll("([ABCIJN-RTUVbcefgijln-rtvx]+)", "'$1'");
            SimpleDateFormat sdf = new SimpleDateFormat(cfg.timeStr);
            if(sdf==null || !(sdf instanceof SimpleDateFormat)) cfg.timeStr = TIMESTR;
        } catch(IllegalArgumentException e) {
            lg.warn("An IllegalArgumentException occurred while trying to make the timestamp:"); e.printStackTrace();
            cfg.timeStr = TIMESTR;
        }
        try {
            cfg.hoverStr = ((String)Util.or(cfg.hoverStr, cfg.hover_string, HOVERSTR)).replaceAll("'","").replaceAll("([ABCIJN-RTUVbcefgijln-rtvx]+)", "'$1'");
            SimpleDateFormat sdf = new SimpleDateFormat(cfg.hoverStr);
            if(sdf==null || !(sdf instanceof SimpleDateFormat)) cfg.hoverStr = HOVERSTR;
        } catch(IllegalArgumentException e) {
            lg.warn("An IllegalArgumentException occurred while trying to make the timestamp:"); e.printStackTrace();
            cfg.hoverStr = HOVERSTR;
        }

        List<Formatting> tFmts = new ArrayList<>(Arrays.asList( (Formatting[])Util.or(cfg.timeFormatting, cfg.time_formatting, EMPTY) ));
        List<Formatting> bFmts = new ArrayList<>(Arrays.asList( (Formatting[])Util.or(cfg.boundaryFormatting, cfg.boundary_formatting, EMPTY) ));
        tFmts.removeIf(f -> Objects.isNull(f) || f==Formatting.RESET);
        bFmts.removeIf(f -> Objects.isNull(f) || f==Formatting.RESET);
        cfg.timeFormatting = (Formatting[])Util.or(tFmts.toArray(EMPTY), TIMEFORMATTING);
        cfg.boundaryFormatting = (Formatting[])Util.or(bFmts.toArray(EMPTY), BOUNDARYFORMATTING);

        cfg.maxMsgs = (Integer)cfg.maxMsgs!=null && Util.inRange(101, 16383, cfg.maxMsgs) ? cfg.maxMsgs : ((Integer)cfg.max_messages!=null && Util.inRange(101, 16383, cfg.max_messages) ? cfg.max_messages : MAXMSGS);
        if(cfg.reset) reset(); cfg.reset = false;

        cfg.nameStr = cfg.nameStr!=null && cfg.nameStr.contains("$") ? cfg.nameStr : NAMESTR;


        WMCH.config = cfg;
        lg.info(diff=="" ? "Nothing happened; no changes were made." : "Finished saving config; changes made:"+diff);
    }


    public String getTimeF(Date when) { return new SimpleDateFormat(cfg.timeStr).format(when); }
    public String getHoverF(Date when) { return new SimpleDateFormat(cfg.hoverStr).format(when); }
    public String getNameF() { return cfg.nameStr.replace("$", (String)WMCH.lastMDat[0]); }

    /** Takes 3 objects and adds them to the config difference string if any changes were made. */
    public void addDiff(Object latest, Object prev, Object out) {
        if( prev.equals(latest) && out.equals(prev) ) return; // no change
        else if( !prev.equals(latest) && out.equals(latest) && !out.equals(prev) ) diff += "\n\t"+prev+" => "+out; // a change
    }

    /**
     * Extracts a {@link Config} from {@code ./config/wmch.json}.
     */
    public static void read() {
        File cfgFile = new File(FabricLoader.getInstance().getConfigDir().toFile().getAbsolutePath()+File.separator+"wmch.json");
        Gson gson = new Gson();

        try (FileReader fr = new FileReader(cfgFile)) {
            if(!cfgFile.exists()) { reset(); lg.warn("Could not find wmch's config file (searched in '{}'); created a default one.", cfgFile.getAbsolutePath()); }
            else {
                cfg = gson.fromJson(fr, WMCH.isConfigModded ? ClothConfig.class : Config.class);

                if(cfg==null || !(cfg instanceof Config)) { reset(); lg.info("Something was broken, so the config has been reset."); }

                lg.debug("Loaded config info {} from '{}'", cfg, cfgFile.getAbsolutePath());
            }
        } catch(Exception e) { lg.error("An error occurred while trying to load wmch's config data; resetting:"); e.printStackTrace(); reset(); }
    }
    /**
     * Saves {@code Config.cfg} to the {@code ./config/wmch.json} file.
     * @param c The Config to save
     */
    public static void write(Config c) {
        String cfgPath = FabricLoader.getInstance().getConfigDir().toFile().getAbsolutePath()+File.separator+"wmch.json";

        try (FileWriter fw = new FileWriter(cfgPath)) {
            // ignore null values
            new GsonBuilder().excludeFieldsWithModifiers(Modifier.PROTECTED, Modifier.STATIC).setPrettyPrinting().create().toJson(c, c.getClass(), fw);

            lg.debug("Saved config info {} from '{}'", c.toString(), cfgPath);
        } catch(Exception e) { lg.error("An error occurred while trying to save wmch's config data:"); e.printStackTrace(); }
    }
    /**
     * Overwrites the {@code cfg} object and {@code WMCH.config} with default values and writes it to {@code ./config/wmch.json}
     */
    public static void reset() {
        Config.cfg = new Config(true);
        WMCH.config = cfg;
        write(cfg);
    }

    @Override
    public String toString() {
        List<Formatting> tFmts = new ArrayList<>(Arrays.asList( (Formatting[])Util.or(cfg.timeFormatting, cfg.time_formatting, EMPTY) ));
        List<Formatting> bFmts = new ArrayList<>(Arrays.asList( (Formatting[])Util.or(cfg.boundaryFormatting, cfg.boundary_formatting, EMPTY) ));
        tFmts.removeIf(f -> Objects.isNull(f) || f==Formatting.RESET);
        bFmts.removeIf(f -> Objects.isNull(f) || f==Formatting.RESET);
        cfg.timeFormatting = (Formatting[])Util.or(tFmts.toArray(EMPTY), TIMEFORMATTING);
        cfg.boundaryFormatting = (Formatting[])Util.or(bFmts.toArray(EMPTY), BOUNDARYFORMATTING);

        return String.format(
            "{\n\ttime: %b,\n\ttimeStr: '%s',\n\ttimeFormatting: %s,\n\thover: %b,\n\thoverStr: '%s',\n\tboundary: %b,\n\tboundaryStr: '%s',\n\tboundaryFormatting: '%s',\n\tmaxMsgs: %d,\n}",
            this.time, this.timeStr, tFmts.toString(),
            this.hover, this.hoverStr,
            this.boundary, this.boundaryStr, bFmts.toString(),
            this.maxMsgs
        );
    }
}