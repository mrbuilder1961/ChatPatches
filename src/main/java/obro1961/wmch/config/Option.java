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
import net.minecraft.text.Text;
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
     * @param onSave The save consumer that takes an incoming value
     * and decides IF it's valid how to save it; Otherwise does nothing.
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
    public Text getName() {return Text.translatable("text.wmch."+key);}
    public Text getTooltip() {return Text.translatable("text.wmch.desc."+key);}
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
    public void save(T inc) {
        this.saveConsumer.accept(inc, this);
    }
    /** Sets the value in the config provided for writing to disk, and this option */
    public void setInConfig(Config cfg, T inc) {
        this.set(inc);

        try {
            cfg.getClass().getField(key).set(cfg, inc);
        } catch(IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
            lg.fatal("You should not be seeing this unless I screwed something up; in which case please open a bug report with this log attached (preferably debug if it exists).");
            e.printStackTrace();
        }
    }
    public void setInDefConfig(T inc) { setInConfig(WMCH.config, inc); }

    @SuppressWarnings("unchecked")
    public ConfigEntryBuilder updateEntryBuilder(ConfigEntryBuilder builder, ConfigCategory category) {
        switch(value.getClass().getName()) {
            case "java.lang.String": {
                category.addEntry(
                    builder.startStrField(getName(), value.toString())
                        .setDefaultValue(def.toString())
                        .setTooltip(getTooltip())
                        .setSaveConsumer(val -> save((T)val))
                    .build()
                );
                break;
            }
            case "java.lang.Integer": {
                category.addEntry( this.getKey() == "maxMsgs"
                    ? builder.startIntField(getName(), (int)value)
                        .setDefaultValue((int)def)
                        .setTooltip(getTooltip())
                        .setSaveConsumer(val -> save((T)val))
                      .build()
                    : builder.startColorField(getName(), (int)value)
                        .setDefaultValue((int)def)
                        .setTooltip(getTooltip())
                        .setSaveConsumer(val -> save((T)val))
                      .build()
                );
                break;
            }
            case "java.lang.Boolean": {
                category.addEntry(
                    builder.startBooleanToggle(getName(), (boolean)value)
                        .setDefaultValue((boolean)def)
                        .setTooltip(getTooltip())
                        .setSaveConsumer(val -> save((T)val))
                    .build()
                );
                break;
            }
            default:
                lg.error("Unexpected class \"{}\", expected java.lang.(String|Integer|Boolean)", def.getClass().getName());
                break;
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
    public static final Option<String> TIME_STR = new Option<>("[HH:mm:ss]", "timeStr", (inc, me) -> {
        boolean failed = false;
        try {
            new SimpleDateFormat( inc.replaceAll("([ABCIJN-RTUVbcefgijln-rtvx]+)", "'$1'").replaceAll("'{2,}", "'") ).toPattern();
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
    public static final Option<String> TIME_FORMAT = new Option<>("", "timeFormat", (inc, me) -> {
        try {
            String out = delAll(inc, "\s{3,}");
            getStrTextF(out);
            me.setInDefConfig(out);
        } catch(Exception e) { lg.warn(e.getStackTrace()); }
    });
    public static final Option<Integer> TIME_COLOR = new Option<>(Formatting.LIGHT_PURPLE.getColorValue(), "timeColor", (inc, me) -> {
        if(16777216 > inc && inc >= 0) me.setInDefConfig(inc);
    });

    public static final Option<Boolean> HOVER = new Option<>(true, "hover", TIME.getSaveConsumer());
    public static final Option<String> HOVER_STR = new Option<>("yyyy dd, MM, @ HH:mm:ss.SSSS", "hoverStr", TIME_STR.getSaveConsumer());

    public static final Option<Boolean> COUNTER = new Option<>(true, "counter", TIME.getSaveConsumer());
    public static final Option<String> COUNTER_STR = new Option<>("&8(&7x&e$&8)", "counterStr", (inc, me) -> {
        if(inc.contains("$")) {
            String out = delAll(inc, "\s{2,}");
            getStrTextF(out);
            me.setInDefConfig(out);
        }
    });
    public static final Option<Integer> COUNTER_COLOR = new Option<>(Formatting.YELLOW.getColorValue(), "counterColor", TIME_COLOR.getSaveConsumer());

    public static final Option<Boolean> BOUNDARY = new Option<>(true, "boundary", TIME.getSaveConsumer());
    public static final Option<String> BOUNDARY_STR = new Option<>("&b[==============]", "boundaryStr", (inc, me) -> {
        if(inc.length() > 0) {
            getStrTextF(inc.strip());
            me.setInDefConfig(inc.strip());
        }
    });
    public static final Option<Integer> BOUNDARY_COLOR = new Option<>(Formatting.DARK_AQUA.getColorValue(), "boundaryColor", TIME_COLOR.getSaveConsumer());

    public static final Option<Boolean> SAVE_CHAT = new Option<>(false, "saveChat", TIME.getSaveConsumer());
    public static final Option<String> NAME_STR = new Option<>("<$>", "nameStr", (inc, me) -> {if(inc.contains("$")) me.setInDefConfig(inc);});
    //public static final Option<Boolean> HIDE_UNSECURE_NOTIF = new Option<>(true, "hideUnsecureNotif", TIME.getSaveConsumer());
    public static final Option<Integer> MAX_MESSAGES = new Option<>(1024, "maxMsgs", (inc, me) -> {
        if(4097 > inc && inc > 99) me.setInDefConfig(inc);
    });

    public static final List<Option<?>> OPTIONS = new ArrayList<>(Arrays.asList(
        TIME, TIME_STR, TIME_COLOR, TIME_FORMAT,
        HOVER, HOVER_STR,
        COUNTER, COUNTER_STR, COUNTER_COLOR,
        BOUNDARY, BOUNDARY_STR, BOUNDARY_COLOR,
        SAVE_CHAT, NAME_STR, /* HIDE_UNSECURE_NOTIF, */ MAX_MESSAGES
    ));
}