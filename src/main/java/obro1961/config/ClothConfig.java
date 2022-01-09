package obro1961.config;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import obro1961.WMCH;

/** The extended config menu, supported by Cloth Config and Mod Menu. @see Config */
public class ClothConfig extends Config {
    public ClothConfig(boolean resetCfgObj) { super(resetCfgObj); }
    protected ClothConfig(boolean timeEnabled, String timeString, Formatting[] timeFormattings, boolean hoverEnabled, String hoverString, boolean boundaryEnabled, String boundaryString, Formatting[] boundaryFormattings, int maxMessages, boolean resetOptions) {
        super(timeEnabled, timeString, timeFormattings, hoverEnabled, hoverString, boundaryEnabled, boundaryString, boundaryFormattings, maxMessages, resetOptions);
    }


    /**
     * Returns the config screen for the Where's My Chat History mod. Responsible for validating and parsing all config values.
     * @param prevScreen The last used screen, used when done with configuring settings
     * @return A new config Screen for player access
     */
    public Screen getWMCHConfigScreen(Screen prevScreen) {
        ConfigBuilder bldr = ConfigBuilder.create().setParentScreen(prevScreen).setDoesConfirmSave(true).setTitle(new TranslatableText("text.wmch.title"));
        ConfigEntryBuilder iBldr = bldr.entryBuilder();
        ConfigCategory time = bldr.getOrCreateCategory(new TranslatableText("text.wmch.time_category"));
        ConfigCategory hover = bldr.getOrCreateCategory(new TranslatableText("text.wmch.hover_category"));
        ConfigCategory boundary = bldr.getOrCreateCategory(new TranslatableText("text.wmch.boundary_category"));
        ConfigCategory other = bldr.getOrCreateCategory(new TranslatableText("text.wmch.other_category"));

        iBldr = quickStart("time", cfg.time, iBldr, time, null, current -> {
            cfg.time = (boolean)WMCH.or(current, cfg.time_enabled, TIME);
        }, null);
        iBldr = quickStart("timeStr", cfg.timeStr, iBldr, time, current -> {
            try {
                cfg.timeStr = ((String)WMCH.or(cfg.timeStr, cfg.time_text, TIMESTR)).replaceAll("'","").replaceAll("([ABCIJN-RTUVbcefgijln-rtvx]+)", "'$1'");
                SimpleDateFormat sdf = new SimpleDateFormat(cfg.timeStr);
                if(sdf==null || !(sdf instanceof SimpleDateFormat)) cfg.timeStr = TIMESTR;
            } catch(IllegalArgumentException e) {
                lg.warn("An IllegalArgumentException occurred while trying to make the timestamp:"); e.printStackTrace();
                cfg.timeStr = TIMESTR;
            }
        }, null, null);
        iBldr = quickStart("timeFormatting", cfg.timeFormatting, iBldr, time, null, null, current -> {
            List<Formatting> tFmts = new ArrayList<>(current.size());
                for(int i=0;i<current.size();i++) tFmts.add(Formatting.byName(current.get(i)));
                tFmts.removeIf(f -> Objects.isNull(f) || f==Formatting.RESET);
            cfg.timeFormatting = (Formatting[])WMCH.or(tFmts.toArray(EMPTY), cfg.time_formatting, TIMEFORMATTING);
        });

        iBldr = quickStart("hover", cfg.hover, iBldr, hover, null, current -> {
            cfg.hover = (Boolean)WMCH.or(current, cfg.hover_enabled, HOVER);
        }, null);
        iBldr = quickStart("hoverStr", cfg.hoverStr, iBldr, hover, current -> {
            try {
                cfg.hoverStr = ((String)WMCH.or(current, cfg.hover_string, HOVERSTR)).replaceAll("'","").replaceAll("([ABCIJN-RTUVbcefgijln-rtvx]+)", "'$1'");
                SimpleDateFormat sdf = new SimpleDateFormat(cfg.hoverStr);
                if(sdf==null || !(sdf instanceof SimpleDateFormat)) cfg.hoverStr = HOVERSTR;
            } catch(IllegalArgumentException e) {
                lg.warn("An IllegalArgumentException occurred while trying to make the timestamp:"); e.printStackTrace();
                cfg.hoverStr = HOVERSTR;
            }
        }, null, null);

        iBldr = quickStart("boundary", cfg.boundary, iBldr, boundary, null, current -> {
            cfg.boundary = (Boolean)WMCH.or(current, cfg.boundary_enabled, BOUNDARY);

        }, null);
        iBldr = quickStart("boundaryStr", cfg.boundaryStr, iBldr, boundary, current -> {
            cfg.boundaryStr = (String)WMCH.or(current, cfg.boundary_string, BOUNDARYSTR);
        }, null, null);
        iBldr = quickStart("boundaryFormatting", cfg.boundaryFormatting, iBldr, boundary, null, null, current -> {
            List<Formatting> bFmts = new ArrayList<>(current.size());
                for(int i=0;i<current.size();i++) bFmts.add(Formatting.byName(current.get(i)));
                bFmts.removeIf(f -> Objects.isNull(f) || f==Formatting.RESET);
            cfg.boundaryFormatting = (Formatting[])WMCH.or(bFmts.toArray(EMPTY), cfg.boundary_formatting, BOUNDARYFORMATTING);
        });

        other.addEntry( iBldr.startIntField(new TranslatableText("text.wmch.maxMsgs"), cfg.maxMsgs)
                .setDefaultValue(maxMsgs)
                .setTooltip(new TranslatableText("text.wmch.maxMsgs_desc"))
                .setSaveConsumer(current -> { cfg.maxMsgs = current>100 && current<16835 ? current : cfg.maxMsgs>100 && cfg.maxMsgs<16835 ? cfg.maxMsgs : MAXMSGS; })
            .build() );
        quickStart("reset", cfg.reset, iBldr, other, null, current -> { if(current) reset(); cfg.reset = false; }, null);

        bldr.setSavingRunnable(() -> {
            write(cfg);
            lg.info("Saved config info from Mod Menu interface using Cloth Config!");
        });
        return bldr.build();
    }

    private static ConfigEntryBuilder quickStart(String key, Object def, ConfigEntryBuilder iBldr, ConfigCategory category, Consumer<String> saveStr, Consumer<Boolean> saveBool, Consumer<List<String>> saveList) {
        TranslatableText[] texts = {new TranslatableText("text.wmch."+key), new TranslatableText("text.wmch."+key+"_desc")};
        switch (def.getClass().getName()) {
            case "java.lang.String":
                category.addEntry(
                    iBldr.startStrField(texts[0], (String)def)
                        .setDefaultValue((String)def)
                        .setTooltip(texts[1])
                        .setSaveConsumer(saveStr)
                    .build()
                );
            break;
            case "java.lang.Boolean":
                category.addEntry(
                    iBldr.startBooleanToggle(texts[0], (boolean)def)
                        .setDefaultValue((boolean)def)
                        .setTooltip(texts[1])
                        .setSaveConsumer(saveBool)
                    .build()
                );
            break;
            case "[Lnet.minecraft.class_124;": // "[Lnet.minecraft.util.Formatting;"
                List<String> strFmts = new ArrayList<>(); List<Formatting> fmts = new ArrayList<>(Arrays.asList( (Formatting[])def ));
                fmts.removeIf(f -> Objects.isNull(f) || f==Formatting.RESET);
                fmts.forEach(f -> strFmts.add(f.getName()));
                category.addEntry(
                    iBldr.startStrList(texts[0], strFmts)
                        .setDefaultValue(strFmts)
                        .setTooltip(texts[1])
                        .setSaveConsumer(saveList)
                    .build()
                );
            break;
            default: lg.error("Invalid type '{}', expected one of the following: String,Boolean,Formatting[]", def.getClass().getName());
            break;
        }
        return iBldr;
    }
}