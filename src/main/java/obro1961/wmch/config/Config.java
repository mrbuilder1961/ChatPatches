package obro1961.wmch.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.logging.log4j.Logger;

import joptsimple.internal.Strings;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Formatting;
import obro1961.wmch.Util;
import obro1961.wmch.WMCH;

/**
 * The config class for WMCH. Extended by others for external versions.
 * @see ClothConfig
 */
public class Config {
    protected static final Logger lg = WMCH.log; // Just shortens lots of log references
    protected static Config cfg;
    protected static String diff = Strings.EMPTY;

    // default
    public static final boolean TIME = true;
    public static final String TIMESTR = "[HH:mm:ss]";
    public static final int TIMECOLOR = Formatting.LIGHT_PURPLE.getColorValue();
    public static final Formatting[] TIMEFORMATTING = {Formatting.LIGHT_PURPLE};
    public static final boolean HOVER = true;
    public static final String HOVERSTR = "yyyy dd, MM, @ HH:mm:ss.SSSS";
    public static final boolean COUNTER = true;
    public static final String COUNTERSTR = "(x$)";
    public static final int COUNTERCOLOR = Formatting.YELLOW.getColorValue();
    public static final Formatting[] COUNTERFORMATTING = {Formatting.GRAY};
    public static final boolean BOUNDARY = true;
    public static final String BOUNDARYSTR = "<]===---{ SESSION BOUNDARY LINE }---===[>";
    public static final int BOUNDARYCOLOR = Formatting.DARK_AQUA.getColorValue();
    public static final Formatting[] BOUNDARYFORMATTING = {Formatting.DARK_AQUA, Formatting.BOLD};
    public static final String NAMESTR = "<$>";
    public static final int MAXMSGS = 1024;
    public static final boolean RESET = false;

    // configurable
    public boolean time = TIME;
    public String timeStr = TIMESTR;
    public int timeColor = TIMECOLOR;
    public Formatting[] timeFormatting = TIMEFORMATTING;
    public boolean hover = HOVER;
    public String hoverStr = HOVERSTR;
    public boolean counter = COUNTER;
    public String counterStr = COUNTERSTR;
    public int counterColor = COUNTERCOLOR;
    public Formatting[] counterFormatting = COUNTERFORMATTING;
    public boolean boundary = BOUNDARY;
    public String boundaryStr = BOUNDARYSTR;
    public int boundaryColor = BOUNDARYCOLOR;
    public Formatting[] boundaryFormatting = BOUNDARYFORMATTING;
    public String nameStr = NAMESTR;
    public int maxMsgs = MAXMSGS;
    public boolean reset = RESET;


    public Config(boolean shouldReset) {
        this.time = TIME;
        this.timeStr = TIMESTR;
        this.timeColor = TIMECOLOR;
        this.timeFormatting = TIMEFORMATTING;
        this.hover = HOVER;
        this.hoverStr = HOVERSTR;
        this.counter = COUNTER;
        this.counterStr = COUNTERSTR;
        this.counterColor = COUNTERCOLOR;
        this.counterFormatting = COUNTERFORMATTING;
        this.boundary = BOUNDARY;
        this.boundaryStr = BOUNDARYSTR;
        this.boundaryColor = BOUNDARYCOLOR;
        this.boundaryFormatting = BOUNDARYFORMATTING;
        this.nameStr = NAMESTR;
        this.maxMsgs = MAXMSGS;
        this.reset = RESET;

        if(shouldReset) WMCH.config = this;
    }

    /** Turns the config into usable, publicly accessible values. Have a nice trip... */
    public static void validate() {
        if(!WMCH.moddedConfig) lg.warn("Cloth config v6.1.48+ and Mod Menu v3.0.0+ are not installed, config can only be accessed by the file. Make sure your mods are in the right folder and are the right version or higher.");
        read();

        cfg.time = (Boolean)Util.or(cfg.time, TIME);
        cfg.hover = (Boolean)Util.or(cfg.hover, HOVER);
        cfg.boundary = (Boolean)Util.or(cfg.boundary, BOUNDARY);
        cfg.counter = (Boolean)Util.or(cfg.counter, COUNTER);
        
        try {
            cfg.timeStr = ((String)Util.or(cfg.timeStr, TIMESTR)).replaceAll("'","").replaceAll("([ABCIJN-RTUVbcefgijln-rtvx]+)", "'$1'");
            SimpleDateFormat sdf = new SimpleDateFormat(cfg.timeStr);
            if(sdf==null || !(sdf instanceof SimpleDateFormat)) cfg.timeStr = TIMESTR;
        } catch(IllegalArgumentException e) {
            lg.warn("An IllegalArgumentException occurred while trying to make the timestamp (if you see this make a bug report!):"); e.printStackTrace();
            cfg.timeStr = TIMESTR;
        }
        try {
            cfg.hoverStr = ((String)Util.or(cfg.hoverStr, HOVERSTR)).replaceAll("'","").replaceAll("([ABCIJN-RTUVbcefgijln-rtvx]+)", "'$1'");
            SimpleDateFormat sdf = new SimpleDateFormat(cfg.hoverStr);
            if(sdf==null || !(sdf instanceof SimpleDateFormat)) cfg.hoverStr = HOVERSTR;
        } catch(IllegalArgumentException e) {
            lg.warn("An IllegalArgumentException occurred while trying to make the timestamp (if you see this make a bug report!):"); e.printStackTrace();
            cfg.hoverStr = HOVERSTR;
        }
        cfg.counterStr = cfg.counterStr!=null && cfg.counterStr.contains("$") ? cfg.counterStr.replaceAll("\\d","") : COUNTERSTR;
        cfg.boundaryStr = (String)Util.or(cfg.boundaryStr, BOUNDARYSTR);
        
        List<Formatting> tFmts = new ArrayList<>(Arrays.asList( (Formatting[])Util.or(cfg.timeFormatting, new Formatting[0]) ));
        List<Formatting> cFmts = new ArrayList<>(Arrays.asList( (Formatting[])Util.or(cfg.counterFormatting, new Formatting[0]) ));
        List<Formatting> bFmts = new ArrayList<>(Arrays.asList( (Formatting[])Util.or(cfg.boundaryFormatting, new Formatting[0]) ));
        tFmts.removeIf(f -> Objects.isNull(f) || f==Formatting.RESET || f.isColor());
        cFmts.removeIf(f -> Objects.isNull(f) || f==Formatting.RESET);
        bFmts.removeIf(f -> Objects.isNull(f) || f==Formatting.RESET || f.isColor());
        cfg.timeFormatting = (Formatting[])Util.or(tFmts.toArray(new Formatting[0]), TIMEFORMATTING);
        cfg.counterFormatting = (Formatting[])Util.or(cFmts.toArray(new Formatting[0]), COUNTERFORMATTING);
        cfg.boundaryFormatting = (Formatting[])Util.or(bFmts.toArray(new Formatting[0]), BOUNDARYFORMATTING);

        cfg.nameStr = cfg.nameStr!=null && cfg.nameStr.contains("$") ? cfg.nameStr : NAMESTR;
        cfg.maxMsgs = (Integer)cfg.maxMsgs!=null && Util.inRange(101, 16383, cfg.maxMsgs) ? cfg.maxMsgs : 1024;

        if(cfg.reset)reset(); cfg.reset = false;

        logDiffs(cfg, WMCH.config);
        WMCH.config = cfg;
        lg.info("Finished saving config!");
    }

    // these methods return a "formatted" version of their input, (ex. getHoverF(Date) returns the formatted hover string)
    public String getTimeF(Date when) { return new SimpleDateFormat(cfg.timeStr).format(when); }
    public String getHoverF(Date when) { return new SimpleDateFormat(cfg.hoverStr).format(when); }
    public String getNameF() { return cfg.nameStr.replace("$", (String)WMCH.lastMsgData[0]); }

    /** Prints any changes between 2 Configs, or nothing if they're (objectically) identical */
    protected static void logDiffs(Config neww, Config old) {
        diff = "Changes made:";

        List<Field> news = new ArrayList<>( Arrays.asList(neww.getClass().getFields()) );
        List<Field> olds = new ArrayList<>( Arrays.asList(old.getClass().getFields()) );
        Predicate<Field> delNotNeeded = new Predicate<>() {@Override public boolean test(Field f){ return !Modifier.isPublic(f.getModifiers()) || Modifier.isStatic(f.getModifiers()); }};
        news.removeIf(delNotNeeded); olds.removeIf(delNotNeeded);

        if(!old.equals(neww)) try {
            // loops over both Configs to compare fields; quite a mess lol
            for(int i=0,j=0; i<news.size() && j<olds.size(); i++,j++) {
                Object[] things = {
                    news.get(i).get(neww).getClass().isArray() ? Util.formattingArrToStr((Formatting[]) news.get(i).get(neww)) : news.get(i).get(neww).toString(),
                    olds.get(i).get(old).getClass().isArray() ? Util.formattingArrToStr((Formatting[]) olds.get(i).get(old)) : olds.get(i).get(old).toString()
                }; // arrays are so annoying to stringify

                if(!things[0].equals(things[1])) diff += "\n\t"+news.get(i).getName()+": '"+things[1]+"' => '"+things[0]+"'";
            }
        } catch(IllegalAccessException e) {lg.fatal("IllegalAccessException: "+e.getLocalizedMessage());}

        if(diff.length() == 13) diff = ""; // only log if changes were made
        else lg.info(diff);
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
                cfg = gson.fromJson(fr, WMCH.moddedConfig ? ClothConfig.class : Config.class);

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
            //* ignore null values
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
    public String toString() { return new GsonBuilder().setPrettyPrinting().create().toJson(this); }
}