package obro1961.wmch.config;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.apache.logging.log4j.Logger;

import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Language;
import obro1961.wmch.Util;
import obro1961.wmch.WMCH;

//! instead of defaulting a value if it's invalid, don't do anything so it will use the old value instead
/** A Config option class for creating, accessing, and modifying settings. */
public class Option<T> {
    public static String diff = "Changes made:";
    private static final Logger lg = WMCH.log;

    private T value;
    private T def;
    private String key;
    private BiConsumer<T, Option<T>> saveConsumer;

    /**
     * Creates a new Config option.
     * @param def The default value for creation and resetting.
     * @param key The key that is used in the translation files.
     * @param onSave The consumer that takes an incoming value and decides how to save
     * it. When used with {@link Option#onSave(Object)}, it automatically validates the
     * incoming object before writing to disk.
     */
    public Option(T def, String key, BiConsumer<T, Option<T>> onSave) {
        Objects.requireNonNull(def, "Cannot instantiate a ConfigOption without a default value");
        Objects.requireNonNull(key, "Cannot instantiate a ConfigOption without a lang key");
        Objects.requireNonNull(onSave, "Cannot instantiate a ConfigOption without a save consumer");

        this.value = def;
        this.def = def;
        this.key = key;
        this.saveConsumer = onSave;
    }


    // getters
    public T get() {return value;}
    public T getDefault() {return def;}
    public String getKey() {return key;}
    public TranslatableText getName() {return new TranslatableText("text.wmch."+key);}
    public TranslatableText getTooltip() {return new TranslatableText("text.wmch."+key+"_desc");}
    public BiConsumer<T, Option<T>> getSaveConsumer() {return saveConsumer;}
    public Class<?> getType() {return value.getClass();}
    // setters
    public void set(T value) {
        if( Objects.nonNull(value) && !Objects.equals(this.value, value) ) diff += String.format("\n\t%s: '%s' => '%s'", key, this.value, value);
        this.value = Objects.requireNonNullElse(value, this.value);
    } //! add diff here!!!
    public void setDefault(T def) {this.def = Objects.requireNonNullElse(def, this.def);}
    public void setKey(String key) {this.key = Objects.requireNonNullElse(key, this.key);}
    public void setSaveConsumer(BiConsumer<T, Option<T>> saver) {this.saveConsumer = Objects.requireNonNullElse(saver, this.saveConsumer);}

    public boolean changed() {
        return value != def || !value.equals(def);
    }
    public void onSave(T val, Option<T> me) {
        this.getSaveConsumer().accept(val, me);
    }
    /** ONLY sets the value, does NOT validate it. */
    public void setInConfig(Config cfg, T inc) {
        this.set(inc);

        try {
            cfg.getClass().getField(key).set(cfg, inc);
        } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
            lg.fatal("you should not be seeing this unless I screwed something up");
            e.printStackTrace();
        }
    }
    public void setInDefConfig(T inc) { setInConfig(WMCH.config, inc); }

    @SuppressWarnings("unchecked")
    public ConfigEntryBuilder updateEntryBuilder(ConfigEntryBuilder builder, ConfigCategory category, Option<T> me) {
        switch(value.getClass().getName()) {
            case "java.lang.String": {
                category.addEntry(
                    builder.startStrField(getName(), value.toString())
                        .setDefaultValue(def.toString())
                        .setTooltip(getTooltip())
                        .setSaveConsumer(val -> saveConsumer.accept((T)val, me))
                    .build()
                );
                break;
            }
            case "java.lang.Integer": {
                category.addEntry( me.getKey() == "maxMsgs" || me.getKey() == "dupeThreshold"
                    ? builder.startIntField(getName(), (int)value)
                        .setDefaultValue((int)def)
                        .setTooltip(getTooltip())
                        .setSaveConsumer(val -> saveConsumer.accept((T)val, me))
                      .build()
                    : builder.startColorField(getName(), (int)value)
                        .setDefaultValue((int)def)
                        .setTooltip(getTooltip())
                        .setSaveConsumer(val -> saveConsumer.accept((T)val, me))
                      .build()
                );
                break;
            }
            case "java.lang.Boolean": {
                category.addEntry(
                    builder.startBooleanToggle(getName(), (boolean)value)
                        .setDefaultValue((boolean)def)
                        .setTooltip(getTooltip())
                        .setSaveConsumer(val -> saveConsumer.accept((T)val, me))
                    .build()
                );
                break;
            }
            default: lg.error("Unknown class \"{}\", expected java.lang.String,Integer,Boolean", def.getClass().getName()); break;
        }
        return builder;
    }

    /** For updating the GitHub table */
    public static void printTableEntries() {
        Language l = Language.getInstance();
        OPTIONS.forEach(o -> {
            lg.info("| %s | %s | %s | `` | text.wmch.%s |".formatted(
                l.get("text.wmch."+o.key),
                o.def.getClass() == Integer.class && o.key != "maxMsgs"
                    ? "`0x" + Integer.toHexString((int)o.def) + "` (`" + o.def + "`)"
                    : "`" + o.def + "`",
                l.get("text.wmch."+o.key+"_desc"),
                o.key
            ));
        });
    }


    //! most of these just take the first 4 and reuse them
    public static final Option<Boolean> TIME = new Option<>(true, "time", (inc, me) -> me.setInDefConfig(inc));
    public static final Option<String> TIMESTR = new Option<>("[HH:mm:ss]", "timeStr", (inc, me) -> {
        try {
            me.setInDefConfig(
                new SimpleDateFormat( Util.delAll(Util.or(inc, me.get()), "'").replaceAll("([ABCIJN-RTUVbcefgijln-rtvx]+)", "'$1'") ).toPattern()
            );
        } catch(IllegalArgumentException e) {
            lg.warn("An IllegalArgumentException occurred while trying to make the timestamp (if you see this make a bug report!):"); e.printStackTrace();
        }
    });
    public static final Option<String> TIMEFORMAT = new Option<>("", "timeFormat", (inc, me) -> {
        try {
            String out = Util.delAll(inc, "\s{3,}");
            Util.getStrTextF(out);
            me.setInDefConfig(out);
        } catch (Exception e) { lg.warn(e.getStackTrace()); }
    });
    public static final Option<Integer> TIMECOLOR = new Option<>(Formatting.LIGHT_PURPLE.getColorValue(), "timeColor", (inc, me) -> {
        if(16777216 > inc && inc >= 0) me.setInDefConfig(inc);
    });
    public static final Option<Boolean> HOVER = new Option<>(true, "hover", TIME.getSaveConsumer());
    public static final Option<String> HOVERSTR = new Option<>("yyyy dd, MM, @ HH:mm:ss.SSSS", "hoverStr", TIMESTR.getSaveConsumer());
    public static final Option<Boolean> COUNTER = new Option<>(true, "counter", TIME.getSaveConsumer());
    public static final Option<String> COUNTERSTR = new Option<>("&8(&7x&e$&8)", "counterStr", (inc, me) -> {
        if(inc.contains("$")) {
            String out = Util.delAll(inc, "\s{2,}");
            Util.getStrTextF(out);
            me.setInDefConfig(out);
        }
    });
    public static final Option<Integer> COUNTERCOLOR = new Option<>(Formatting.YELLOW.getColorValue(), "counterColor", TIMECOLOR.getSaveConsumer());
    public static final Option<Integer> DUPETHRESHOLD = new Option<>(3, "dupeThreshold", (inc, me) -> {
        if(inc > 1 && inc < Option.MAXMSGS.get()) me.setInDefConfig(inc);
    });
    public static final Option<Boolean> LENIANTEQUALS = new Option<>(false, "leniantEquals", TIME.getSaveConsumer());
    public static final Option<Boolean> BOUNDARY = new Option<>(true, "boundary", TIME.getSaveConsumer());
    public static final Option<String> BOUNDARYSTR = new Option<>("&b[==============]", "boundaryStr", (inc, me) -> {
        if(inc.length() > 0) {
            Util.getStrTextF(inc.strip());
            me.setInDefConfig(inc.strip());
        }
    });
    public static final Option<Integer> BOUNDARYCOLOR = new Option<>(Formatting.DARK_AQUA.getColorValue(), "boundaryColor", TIMECOLOR.getSaveConsumer());
    public static final Option<String> NAMESTR = new Option<>("<$>", "nameStr", (inc, me) -> {if(inc.contains("$")) me.setInDefConfig(inc);});
    public static final Option<Integer> MAXMSGS = new Option<>(1024, "maxMsgs", (inc, me) -> {
        if(4097 > inc && inc > 99) me.setInDefConfig(inc);
    });

    public static final List<Option<?>> OPTIONS = new ArrayList<>(Arrays.asList(
        TIME, TIMESTR, TIMECOLOR, TIMEFORMAT,
        HOVER, HOVERSTR,
        COUNTER, COUNTERSTR, COUNTERCOLOR, DUPETHRESHOLD, LENIANTEQUALS,
        BOUNDARY, BOUNDARYSTR, BOUNDARYCOLOR,
        NAMESTR, MAXMSGS
    ));
}