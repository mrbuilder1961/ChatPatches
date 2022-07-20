package obro1961.wmch.config;

import static obro1961.wmch.util.Util.delAll;
import static obro1961.wmch.util.Util.getStrTextF;

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
import obro1961.wmch.WMCH;

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
    public TranslatableText getTooltip() {return new TranslatableText("text.wmch.desc."+key);}
    public BiConsumer<T, Option<T>> getSaveConsumer() {return saveConsumer;}
    public Class<?> getType() {return value.getClass();}

    // setters
    public void set(T value) {
        if( Objects.nonNull(value) && !Objects.equals(this.value, value) ) diff += String.format("\n\t%s: '%s' => '%s'", key, this.value, value);
        this.value = Objects.requireNonNullElse(value, this.value);
    }
    public void setDefault(T def) {this.def = Objects.requireNonNullElse(def, this.def);}
    public void setKey(String key) {this.key = Objects.requireNonNullElse(key, this.key);}
    public void setSaveConsumer(BiConsumer<T, Option<T>> saver) {this.saveConsumer = Objects.requireNonNullElse(saver, this.saveConsumer);}


    public boolean changed() {
        return value != def || !value.equals(def);
    }
    public void onSave(T val, Option<T> me) {
        this.getSaveConsumer().accept(val, me);
    }
    /** Sets the value in the config provided for writing to disk, and this option */
    public void setInConfig(Config cfg, T inc) {
        this.set(inc);

        try {
            cfg.getClass().getField(key).set(cfg, inc);
        } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
            lg.fatal("You should not be seeing this unless I screwed something up; in which case please open a bug report with this log attached (preferably debug if it exists).");
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
    public static void printTableEntries(Language lang) {
        StringBuilder bldr = new StringBuilder();
        OPTIONS.forEach(o -> {
            bldr.append("\n| %s | %s | %s | `` | text.wmch.%s |\n".formatted(
                lang.get("text.wmch." + o.key),
                o.def.getClass() == Integer.class && o.key != "maxMsgs"
                    ? "`0x" + Integer.toHexString((int)o.def).toUpperCase() + "` (`" + o.def + "`)"
                    : "`" + o.def + "`",
                lang.get("text.wmch.desc." + o.key),
                o.key
            ));
        });

        lg.warn(bldr.toString());
    }


    public static final Option<Boolean> TIME = new Option<>(true, "time", (inc, me) -> me.setInDefConfig(inc));
    public static final Option<String> TIMESTR = new Option<>("[HH:mm:ss]", "timeStr", (inc, me) -> {
        boolean failed = false;
        try {
            new SimpleDateFormat( inc.replaceAll("([ABCIJN-RTUVbcefgijln-rtvx]+)", "'$1'").replaceAll("'{2,}", "'") ).toPattern();
            // "MM/dd  'ms':SSS"
            // inc.replaceAll(useless_chars, "'$1'").replaceAll("'{2,}", "'")
            // delAll(inc, "'").replaceAll("([ABCIJN-RTUVbcefgijln-rtvx]+)", "'$1'")
        } catch(IllegalArgumentException e) {
            failed = true;

            lg.warn("An IllegalArgumentException occurred while trying to make the timestamp (if you see this make a bug report!):");
            e.printStackTrace();
        } finally {
            me.setInDefConfig(
                new SimpleDateFormat( (failed ? me.get() : inc).replaceAll("([ABCIJN-RTUVbcefgijln-rtvx]+)", "'$1'").replaceAll("'{2,}", "'") ).toPattern()
            );
        }
    });
    public static final Option<String> TIMEFORMAT = new Option<>("", "timeFormat", (inc, me) -> {
        try {
            String out = delAll(inc, "\s{3,}");
            getStrTextF(out);
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
            String out = delAll(inc, "\s{2,}");
            getStrTextF(out);
            me.setInDefConfig(out);
        }
    });
    public static final Option<Integer> COUNTERCOLOR = new Option<>(Formatting.YELLOW.getColorValue(), "counterColor", TIMECOLOR.getSaveConsumer());
    public static final Option<Boolean> BOUNDARY = new Option<>(true, "boundary", TIME.getSaveConsumer());
    public static final Option<String> BOUNDARYSTR = new Option<>("&b[==============]", "boundaryStr", (inc, me) -> {
        if(inc.length() > 0) {
            getStrTextF(inc.strip());
            me.setInDefConfig(inc.strip());
        }
    });
    public static final Option<Integer> BOUNDARYCOLOR = new Option<>(Formatting.DARK_AQUA.getColorValue(), "boundaryColor", TIMECOLOR.getSaveConsumer());
    /* public static final Option<Enum<String>> CHATPRIVACY = new Option<>(new Enum<String>(){"old","secret","new"}, "chatPrivacy", (inc, me) -> {

    }); */
    public static final Option<Boolean> SAVECHAT = new Option<>(false, "saveChat", TIME.getSaveConsumer());
    public static final Option<String> NAMESTR = new Option<>("<$>", "nameStr", (inc, me) -> {if(inc.contains("$")) me.setInDefConfig(inc);});
    public static final Option<Integer> MAXMSGS = new Option<>(1024, "maxMsgs", (inc, me) -> {
        if(4097 > inc && inc > 99) me.setInDefConfig(inc);
    });

    public static final List<Option<?>> OPTIONS = new ArrayList<>(Arrays.asList(
        TIME, TIMESTR, TIMECOLOR, TIMEFORMAT,
        HOVER, HOVERSTR,
        COUNTER, COUNTERSTR, COUNTERCOLOR,
        BOUNDARY, BOUNDARYSTR, BOUNDARYCOLOR,
        /* CHATPRIVACY, */ SAVECHAT, NAMESTR, MAXMSGS
    ));
}