package obro1961.wmch.config;

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
import obro1961.wmch.Util;
import obro1961.wmch.WMCH;

/** The extended config menu, supported by Cloth Config and Mod Menu. @see Config */
public class ClothConfig extends Config {
    public ClothConfig(boolean shouldReset) { super(shouldReset); }


    /**
     * Returns the config screen for the Where's My Chat History mod. Responsible for validating and parsing all config values.
     * @param prevScreen The last used screen, used when done with configuring settings
     * @return A new config Screen for player access
     */
    public Screen getWMCHConfigScreen(Screen prevScreen) {
        ConfigBuilder bldr = ConfigBuilder.create().setParentScreen(prevScreen).setDoesConfirmSave(true).setTitle(new TranslatableText("text.wmch.title"));
        ConfigEntryBuilder eBldr = bldr.entryBuilder();
        ConfigCategory time = bldr.getOrCreateCategory(new TranslatableText("text.wmch.time_category"));
        ConfigCategory hover = bldr.getOrCreateCategory(new TranslatableText("text.wmch.hover_category"));
        ConfigCategory counter = bldr.getOrCreateCategory(new TranslatableText("text.wmch.counter_category"));
        ConfigCategory boundary = bldr.getOrCreateCategory(new TranslatableText("text.wmch.boundary_category"));
        ConfigCategory other = bldr.getOrCreateCategory(new TranslatableText("text.wmch.other_category"));

        eBldr = quick("time", cfg.time, eBldr, time, null, current -> {
            cfg.time = (boolean)Util.or(current, TIME);
        }, null);
        eBldr = quick("timeStr", cfg.timeStr, eBldr, time, current -> {
            try {
                cfg.timeStr = ((String)Util.or(current, TIMESTR)).replaceAll("'","").replaceAll("([ABCIJN-RTUVbcefgijln-rtvx]+)", "'$1'");
                SimpleDateFormat sdf = new SimpleDateFormat(cfg.timeStr);
                if(sdf==null || !(sdf instanceof SimpleDateFormat)) cfg.timeStr = TIMESTR;
            } catch(IllegalArgumentException e) {
                lg.warn("An IllegalArgumentException occurred while trying to make the timestamp:"); e.printStackTrace();
                cfg.timeStr = TIMESTR;
            }
        }, null, null);
        time.addEntry(eBldr.startColorField(new TranslatableText("text.wmch.timeColor"), cfg.timeColor)
                .setDefaultValue(TIMECOLOR)
                .setTooltip(new TranslatableText("text.wmch.timeColor_desc"))
                .setSaveConsumer(current -> cfg.timeColor = current)
            .build()
        );
        eBldr = quick("timeFormatting", cfg.timeFormatting, eBldr, time, null, null, current -> {
            List<Formatting> tFmts = new ArrayList<>(current.size());
                for(int i=0;i<current.size();i++) tFmts.add(Formatting.byName(current.get(i)));
                tFmts.removeIf(f -> Objects.isNull(f) || f==Formatting.RESET);
            cfg.timeFormatting = (Formatting[])Util.or(tFmts.toArray(new Formatting[0]), TIMEFORMATTING);
        });

        eBldr = quick("hover", cfg.hover, eBldr, hover, null, current -> { cfg.hover = (Boolean)Util.or(current, HOVER); }, null);
        eBldr = quick("hoverStr", cfg.hoverStr, eBldr, hover, current -> {
            try {
                cfg.hoverStr = ((String)Util.or(current, HOVERSTR)).replaceAll("'","").replaceAll("([ABCIJN-RTUVbcefgijln-rtvx]+)", "'$1'");
                SimpleDateFormat sdf = new SimpleDateFormat(cfg.hoverStr);
                if(sdf==null || !(sdf instanceof SimpleDateFormat)) cfg.hoverStr = HOVERSTR;
            } catch(IllegalArgumentException e) {
                lg.warn("An IllegalArgumentException occurred while trying to make the timestamp:"); e.printStackTrace();
                cfg.hoverStr = HOVERSTR;
            }
        }, null, null);

        eBldr = quick("counter", cfg.counter, eBldr, counter, null, current -> cfg.counter = current, null);
        eBldr = quick("counterStr", cfg.counterStr, eBldr, counter, current -> cfg.counterStr = current.contains("$") ? current : cfg.counterStr, null, null);
        counter.addEntry(eBldr.startColorField(new TranslatableText("text.wmch.counterColor"), cfg.counterColor)
                .setDefaultValue(COUNTERCOLOR)
                .setTooltip(new TranslatableText("text.wmch.counterColor_desc"))
                .setSaveConsumer(current -> cfg.counterColor = current)
            .build()
        );
        eBldr = quick("counterFormatting", cfg.counterFormatting, eBldr, time, null, null, current -> {
            List<Formatting> tFmts = new ArrayList<>(current.size());
                for(int i=0;i<current.size();i++) tFmts.add(Formatting.byName(current.get(i)));
                tFmts.removeIf(f -> Objects.isNull(f) || f==Formatting.RESET);
            cfg.timeFormatting = (Formatting[])Util.or(tFmts.toArray(new Formatting[0]), COUNTERFORMATTING);
        });


        eBldr = quick("boundary", cfg.boundary, eBldr, boundary, null, current -> { cfg.boundary = (Boolean)Util.or(current, BOUNDARY); }, null);
        eBldr = quick("boundaryStr", cfg.boundaryStr, eBldr, boundary, current -> { cfg.boundaryStr = (String)Util.or(current, BOUNDARYSTR); }, null, null);
        boundary.addEntry(eBldr.startColorField(new TranslatableText("text.wmch.boundaryColor"), cfg.boundaryColor)
                .setDefaultValue(BOUNDARYCOLOR)
                .setTooltip(new TranslatableText("text.wmch.boundaryColor_desc"))
                .setSaveConsumer(current -> cfg.boundaryColor = current)
            .build()
        );
        eBldr = quick("boundaryFormatting", cfg.boundaryFormatting, eBldr, boundary, null, null, current -> {
            List<Formatting> bFmts = new ArrayList<>(current.size());
                for(int i=0;i<current.size();i++) bFmts.add(Formatting.byName(current.get(i)));
                bFmts.removeIf(f -> Objects.isNull(f) || f==Formatting.RESET);
            cfg.boundaryFormatting = (Formatting[])Util.or(bFmts.toArray(new Formatting[0]), BOUNDARYFORMATTING);
        });

        other.addEntry( eBldr.startIntField(new TranslatableText("text.wmch.maxMsgs"), cfg.maxMsgs)
                .setDefaultValue(maxMsgs)
                .setTooltip(new TranslatableText("text.wmch.maxMsgs_desc"))
                .setSaveConsumer(current ->  cfg.maxMsgs = current>100 && current<16835 ? current : cfg.maxMsgs>100 && cfg.maxMsgs<16835 ? cfg.maxMsgs : MAXMSGS )
            .build()
        );
        eBldr = quick("nameStr", cfg.nameStr, eBldr, other, current -> {
            cfg.nameStr = current!=null && current.contains("$") ? current : cfg.nameStr;
        }, null, null);
        eBldr = quick("reset", cfg.reset, eBldr, other, null, current -> { if(current) reset(); cfg.reset = false; }, null);
        bldr.setSavingRunnable(() -> {
            write(cfg);
            logDiffs(cfg, WMCH.config);
            lg.info("Saved config data from the Mod Menu interface using Cloth Config!");
        });
        return bldr.build();
    }

    private static ConfigEntryBuilder quick(String key, Object def, ConfigEntryBuilder eBldr, ConfigCategory category, Consumer<String> saveStr, Consumer<Boolean> saveBool, Consumer<List<String>> saveList) {
        TranslatableText[] lang = {new TranslatableText("text.wmch."+key), new TranslatableText("text.wmch."+key+"_desc")};

        switch ( WMCH.fbl.getMappingResolver().unmapClassName(WMCH.fbl.getMappingResolver().getCurrentRuntimeNamespace(),def.getClass().getName()) ) {
            case "java.lang.String":
                category.addEntry(
                    eBldr.startStrField(lang[0], (String)def)
                        .setDefaultValue((String)def)
                        .setTooltip(lang[1])
                        .setSaveConsumer(saveStr)
                    .build()
                );
            break;
            case "java.lang.Boolean":
                category.addEntry(
                    eBldr.startBooleanToggle(lang[0], (boolean)def)
                        .setDefaultValue((boolean)def)
                        .setTooltip(lang[1])
                        .setSaveConsumer(saveBool)
                    .build()
                );
            break;
            case "[Lnet.minecraft.util.Formatting;": // mapped
                List<String> strFmts = new ArrayList<>(); List<Formatting> fmts = new ArrayList<>(Arrays.asList( (Formatting[])def ));
                fmts.removeIf(f -> Objects.isNull(f) || f==Formatting.RESET);
                fmts.forEach(f -> strFmts.add(f.getName()));
                category.addEntry(
                    eBldr.startStrList(lang[0], strFmts)
                        .setDefaultValue(strFmts)
                        .setTooltip(lang[1])
                        .setSaveConsumer(saveList)
                    .build()
                );
            break;
            case "[Lnet.minecraft.class_124;": // intermediary
                strFmts = new ArrayList<>(); fmts = new ArrayList<>(Arrays.asList( (Formatting[])def ));
                fmts.removeIf(f -> Objects.isNull(f) || f==Formatting.RESET);
                fmts.forEach(f -> strFmts.add(f.getName()));
                category.addEntry(
                    eBldr.startStrList(lang[0], strFmts)
                        .setDefaultValue(strFmts)
                        .setTooltip(lang[1])
                        .setSaveConsumer(saveList)
                    .build()
                );
            break;
            default: lg.error("Invalid type '{}', expected one of the following: String,Boolean,Formatting[](class_124[])", def.getClass().getName());
            break;
        }
        return eBldr;
    }
}