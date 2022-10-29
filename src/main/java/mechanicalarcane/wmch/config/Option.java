package mechanicalarcane.wmch.config;

import static mechanicalarcane.wmch.WMCH.LOGGER;
import static mechanicalarcane.wmch.util.Util.delAll;
import static mechanicalarcane.wmch.util.Util.formatString;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import mechanicalarcane.wmch.WMCH;
import mechanicalarcane.wmch.util.Util;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** A Config option class for creating, accessing, and modifying settings. */
public class Option<T> {
    public static String diff = "[Option.logDiff] Changes made:";

    private T val;
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

        this.val = this.def = def;
        this.key = key;
        this.saveConsumer = onSave;
    }


    // getters
    public T get() {return val;}
    public T getDefault() {return def;}
    public String getKey() {return key;}
    public Text getName() {return Text.translatable("text.wmch."+key);}
    public Text getTooltip() {return Text.translatable("text.wmch.desc."+key);}
    public BiConsumer<T, Option<T>> getSaveConsumer() {return saveConsumer;}
    public Class<?> getType() {return val.getClass();}

    // setters
    public void set(T inc) {
        if( inc != null && !inc.equals(val) )
            diff += ( "\n\t%s: '%s' => '%s'".formatted(key, val, inc) );
        this.val = Objects.requireNonNullElse(inc, val);
    }
    public void setDefault(T def) {this.def = Objects.requireNonNullElse(def, this.def);}
    public void setKey(String key) {this.key = Objects.requireNonNullElse(key, this.key);}
    public void setSaveConsumer(BiConsumer<T, Option<T>> saver) {this.saveConsumer = Objects.requireNonNullElse(saver, this.saveConsumer);}


    public boolean changed() {
        return val != def || !val.equals(def);
    }
    /** Runs this Option's save consumer with {@code inc}. */
    public void save(T inc) {
        this.saveConsumer.accept(inc, this);
    }
    /**
     * Takes {@code inc} and sets it as this Option's value and into {@code WMCH.config}
     * @param inc Value to save to {@code WMCH.config} and this Option object
     */
    public void setInDefConfig(T inc) {
        this.set(inc);

        try {
            WMCH.config.getClass().getField(key).set(WMCH.config, inc);
        } catch(IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
            LOGGER.fatal("[Option.setInDefConfig] You should not be seeing this unless I screwed something up; in which case please open a bug report of this on GitHub:", e);
        }
    }


    /** Prints any changes between altering of Options */
    public static void logDiff() {
        if(diff == "[Option.logDiff] Changes made:")
            LOGGER.info("[Option.logDiff] No changes made!");
        else
            LOGGER.info(diff);
        diff = "[Option.logDiff] Changes made:";
    }

    /** For updating the GitHub table */
    public static void printTableEntries() {
        StringBuilder bldr = new StringBuilder();
        OPTIONS.forEach(o -> {
            bldr.append("\n| %s | %s | %s | `` | `text.wmch.%s` |".formatted(
                I18n.translate("text.wmch." + o.key),
                o.def.getClass() == Integer.class && o.key != "maxMsgs"
                    ? "`0x" + Integer.toHexString((int)o.def).toUpperCase() + "` (`" + o.def + "`)"
                    : "`" + o.def + "`",
                I18n.translate("text.wmch.desc." + o.key),
                o.key
            ));
        });

        LOGGER.warn("[Option.printGithubTables]" + bldr.toString());
    }


    public static final Option<Boolean> TIME = new Option<>(true, "time", (inc, me) -> me.setInDefConfig(inc));
    public static final Option<String> TIME_STR = new Option<>("[HH:mm:ss]", "timeStr", (inc, me) -> {
        boolean failed = false;
        try {
            new SimpleDateFormat( inc.replaceAll("([ABCIJN-RTUVbcefgijln-rtvx]+)", "'$1'").replaceAll("'{2,}", "'") ).toPattern();
        } catch(IllegalArgumentException e) {
            failed = true;

            LOGGER.warn("[Option.TIME_STR()] An IllegalArgumentException occurred while trying to verify the timestamp (if you see this make a bug report!):", e);
        } finally {
            me.setInDefConfig(
                new SimpleDateFormat( (failed ? me.get() : inc).replaceAll("([ABCIJN-RTUVbcefgijln-rtvx]+)", "'$1'").replaceAll("'{2,}", "'") ).toPattern()
            );
        }
    });
    public static final Option<String> TIME_FORMAT = new Option<>("", "timeFormat", (inc, me) -> {
        try {
            String out = delAll(inc, "\s{3,}");
            formatString(out);
            me.setInDefConfig(out);
        } catch(Exception e) {
            LOGGER.warn("[Option.TIME_FORMAT()] An error occurred while trying to save {}:", me.key.toUpperCase(), e);
        }
    });
    public static final Option<Integer> TIME_COLOR = new Option<>(Formatting.LIGHT_PURPLE.getColorValue(), "timeColor", (inc, me) -> {
        if(0xFFFFFF > inc && inc >= 0) me.setInDefConfig(inc);
    });

    public static final Option<Boolean> HOVER = new Option<>(true, "hover", TIME.getSaveConsumer());
    public static final Option<String> HOVER_STR = new Option<>("MM/dd/yyyy", "hoverStr", TIME_STR.getSaveConsumer());

    public static final Option<Boolean> COUNTER = new Option<>(true, "counter", TIME.getSaveConsumer());
    public static final Option<String> COUNTER_STR = new Option<>("&8(&7x&e$&8)", "counterStr", (inc, me) -> {
        if(inc.contains("$")) {
            String out = delAll(inc, "\s{2,}");
            formatString(out);
            me.setInDefConfig(out);
        }
    });
    public static final Option<Integer> COUNTER_COLOR = new Option<>(Formatting.YELLOW.getColorValue(), "counterColor", TIME_COLOR.getSaveConsumer());

    public static final Option<Boolean> BOUNDARY = new Option<>(true, "boundary", TIME.getSaveConsumer());
    public static final Option<String> BOUNDARY_STR = new Option<>("&b[==============]", "boundaryStr", (inc, me) -> {
        if(inc.length() > 0) {
            formatString(inc.strip());
            me.setInDefConfig(inc.strip());
        }
    });
    public static final Option<Integer> BOUNDARY_COLOR = new Option<>(Formatting.DARK_AQUA.getColorValue(), "boundaryColor", TIME_COLOR.getSaveConsumer());

    public static final Option<Boolean> SAVE_CHAT = new Option<>(false, "saveChat", TIME.getSaveConsumer());
    public static final Option<Boolean> SHIFT_HUD_POS = new Option<>(true, "shiftHudPos", TIME.getSaveConsumer());
    public static final Option<Integer> MAX_MESSAGES = new Option<>(16384, "maxMsgs", (inc, me) -> {
        if(Short.MAX_VALUE >= inc && inc >= 100)
            me.setInDefConfig(inc);
    });
    public static final Option<String> NAME_STR = new Option<>("$", "nameStr", (inc, me) -> {if(inc.contains("$")) me.setInDefConfig(inc);});

    public static final List<Option<?>> OPTIONS = new ArrayList<>(Arrays.asList(
        TIME, TIME_STR, TIME_COLOR, TIME_FORMAT,
        HOVER, HOVER_STR,
        COUNTER, COUNTER_STR, COUNTER_COLOR,
        BOUNDARY, BOUNDARY_STR, BOUNDARY_COLOR,
        SAVE_CHAT, SHIFT_HUD_POS, NAME_STR, MAX_MESSAGES
    ));


    @SuppressWarnings("unchecked")
    public ConfigEntryBuilder updateEntryBuilder(ConfigEntryBuilder builder, ConfigCategory category) {
        switch(val.getClass().getName()) {
            case "java.lang.String": {
                category.addEntry(
                    builder.startStrField(getName(), val.toString())
                        .setDefaultValue(def.toString())
                        .setTooltip(getTooltip())
                        .setSaveConsumer(val -> save((T)val))
                    .build()
                );
                break;
            }
            case "java.lang.Integer": {
                category.addEntry( this.getKey() == "maxMsgs"
                    ? builder.startIntSlider(getName(), (int)val, 100, Short.MAX_VALUE)
                        .setDefaultValue((int)def)
                        .setTooltip(getTooltip())
                        .setSaveConsumer(val -> save((T)val))
                      .build()
                    : builder.startColorField(getName(), (int)val)
                        .setDefaultValue((int)def)
                        .setTooltip(getTooltip())
                        .setSaveConsumer(val -> save((T)val))
                      .build()
                );
                break;
            }
            case "java.lang.Boolean": {
                category.addEntry(
                    builder.startBooleanToggle(getName(), (boolean)val)
                        .setDefaultValue((boolean)def)
                        .setTooltip(getTooltip())
                        .setSaveConsumer(val -> save((T)val))
                    .build()
                );
                break;
            }
            default:
                LOGGER.error("[Option.updateEntryBuilder] Unexpected class \"{}\", expected java.lang.(String|Integer|Short|Boolean)", val.getClass().getName());
                break;
        }

        return builder;
    }

    /** Runs each save consumer for all Fields of {@code config}. Used in {@link Config#validate()} */
    @SuppressWarnings("all")
    public static void saveAll(Config config) {
        try {
            for(Option option : OPTIONS)
                option.save( config.getClass().getField(option.key).get(config) );
        } catch(IllegalStateException | IllegalAccessException | NoSuchFieldException e) {
            LOGGER.fatal("[Option.saveAll] You should not be seeing this unless I screwed something up, report this on GitHub:", e);
        }
    }

    /** Sets the default of all config values into {@code config}. Used in {@link Config#validate()} */
    public static void defaultAll(Config config) {
        try {
            for(Field field : config.getClass().getFields()) {
                if( Modifier.isStatic(field.getModifiers()) )
                    continue;

                Option<?> optField = Util.find( OPTIONS, opt -> field.getName().equals(opt.key) ).get(0);

                if(optField == null)
                    throw new NullPointerException("[Option.defaultAll] optField should have found an equivalent Field '" + field.getName() + "', got null.");

                field.set(config, optField.getDefault());
            }
        } catch(NullPointerException | IllegalStateException | IllegalAccessException e) {
            LOGGER.fatal("[Option.defaultAll] You should not be seeing this unless I screwed something up, report this on GitHub:", e);
        }
    }
}