package obro1961.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import obro1961.WheresMyChatHistory;

public class Config {
    public static Config cfg = new Config(false);

    public boolean time_enabled = true;
    public String time_text = "[HH:mm:ss]";
    public Formatting[] time_formatting = {Formatting.LIGHT_PURPLE};

    public boolean hover_enabled = true;
    public String hover_string = "yyyy dd, MM, @ HH:mm:ss.SSSS";

    public boolean boundary_enabled = true;
    public String boundary_string = "<]===---{ SESSION BOUNDARY LINE }---===[>";
    public Formatting[] boundary_formatting = {Formatting.DARK_AQUA, Formatting.BOLD};

    public int max_messages = 1024; // dynamic
    public boolean reset = false;

    public Config(boolean reset) {
        this.time_enabled = true;
        this.time_text = "[HH:mm:ss]";
        this.time_formatting = new Formatting[] {Formatting.LIGHT_PURPLE};
        this.hover_enabled = true;
        this.hover_string = "yyyy dd, MM, @ HH:mm:ss.SSSS";
        this.boundary_enabled = true;
        this.boundary_string = "<]===---{ SESSION BOUNDARY LINE }---===[>";
        this.boundary_formatting = new Formatting[] {Formatting.DARK_AQUA, Formatting.BOLD};
        this.max_messages = 1024;
        this.reset = false;

        if(reset) cfg = this;
    }


    /**
     * Returns the config screen for the Where's My Chat History mod. Responsible for validating and parsing all config values.
     * @param prevScreen The last used screen, used when done with configuring settings
     * @return A new config Screen for player access
     */
    public static Screen getWMCHConfigScreen(Screen prevScreen) {
        ConfigBuilder bldr = ConfigBuilder.create().setParentScreen(prevScreen).setDoesConfirmSave(true).setTitle(new TranslatableText("wmch.config.title"));
        ConfigEntryBuilder iBldr = bldr.entryBuilder();
        ConfigCategory time = bldr.getOrCreateCategory(new TranslatableText("wmch.config.time_category"));
        ConfigCategory hover = bldr.getOrCreateCategory(new TranslatableText("wmch.config.hover_category"));
        ConfigCategory boundary = bldr.getOrCreateCategory(new TranslatableText("wmch.config.boundary_category"));
        ConfigCategory other = bldr.getOrCreateCategory(new TranslatableText("wmch.config.other_category"));

        iBldr = quickStart("time_enabled", cfg.time_enabled, iBldr, time, null, current -> {
            cfg.time_enabled = current;
        }, null);
        iBldr = quickStart("time_text", cfg.time_text, iBldr, time, current -> {
            try {
                current = current.replaceAll("([ABCIJN-RTUVbcefgijln-rtvx]+)", "'$1'");
                SimpleDateFormat sdf = new SimpleDateFormat(current);
                if(sdf!=null && sdf instanceof SimpleDateFormat) cfg.time_text = current;
            }
            catch(IllegalArgumentException e) {
                WheresMyChatHistory.log.warn("An IllegalArgumentException occurred while trying to make the timestamp: {}", (Object)e.getStackTrace());
                cfg.time_text = "[HH:mm:ss]";
            }
        }, null, null);
        iBldr = quickStart("time_formatting", cfg.time_formatting, iBldr, time, null, null, current -> {
            ArrayList<Formatting> tFmts = new ArrayList<>(current.size());
            for(int i=0;i<current.size();i++)
                if(current.get(i)!=null)
                    tFmts.add( Formatting.byName(current.get(i).toUpperCase()) );
            cfg.time_formatting = tFmts.toArray(new Formatting[] {});
        });

        iBldr = quickStart("hover_enabled", cfg.hover_enabled, iBldr, hover, null, current -> {
            cfg.hover_enabled = current;
        }, null);
        iBldr = quickStart("hover_string", cfg.hover_string, iBldr, hover, current -> {
            try {
                current = current.replaceAll("([ABCIJN-RTUVbcefgijln-rtvx]+)", "'$1'");
                SimpleDateFormat sdf = new SimpleDateFormat(current);
                if(sdf!=null && sdf instanceof SimpleDateFormat) cfg.hover_string = current;
            }
            catch(IllegalArgumentException e) {
                WheresMyChatHistory.log.warn("An IllegalArgumentException occurred while trying to make the timestamp: {}", (Object)e.getStackTrace());
                cfg.hover_string = "HH:mm:ss";
            }
        }, null, null);

        iBldr = quickStart("boundary_enabled", cfg.boundary_enabled, iBldr, boundary, null, current -> {
            cfg.boundary_enabled = current;
        }, null);
        iBldr = quickStart("boundary_string", cfg.boundary_string, iBldr, boundary, current -> {
            cfg.boundary_string = current;
        }, null, null);
        iBldr = quickStart("boundary_formatting", cfg.boundary_formatting, iBldr, boundary, null, null, current -> {
            ArrayList<Formatting> bFormattings = new ArrayList<>(current.size());
            for(int i=0;i<current.size();i++)
                if(current.get(i)!=null)
                    bFormattings.add( Formatting.byName(current.get(i).toUpperCase()) );
            cfg.boundary_formatting = bFormattings.toArray(new Formatting[] {});
        });

        other.addEntry(
            iBldr.startIntSlider(new TranslatableText("wmch.config.max_messages"), cfg.max_messages, 100, 16384)
                .requireRestart()
                .setDefaultValue(cfg.max_messages)
                .setTooltip(new TranslatableText("wmch.config.max_messages_desc"))
                .setSaveConsumer(current -> { cfg.max_messages = current>100 && current<16384 ? current : 1024; })
            .build()
        );
        quickStart("reset", false, iBldr, other, null, current -> { if(current) reset(); }, null);

        bldr.setSavingRunnable(() -> {
            save();
            MinecraftClient.getInstance().reloadResources();
        });
        return bldr.build();
    }
    public static String getFormattedTime(Date when) {
        return new SimpleDateFormat(cfg.time_text).format(when);
    }
    public static String getFormattedHover(Date when) {
        return new SimpleDateFormat(cfg.hover_string).format(when);
    }

    private static ConfigEntryBuilder quickStart(String key, Object def, ConfigEntryBuilder iBldr, ConfigCategory category, Consumer<String> saveStr, Consumer<Boolean> saveBool, Consumer<List<String>> saveList) {
        TranslatableText[] texts = {new TranslatableText("wmch.config."+key), new TranslatableText("wmch.config."+key+"_desc")};
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
            case "[Lnet.minecraft.util.Formatting;":
                List<String> strFmts = new ArrayList<>(((Formatting[])def).length);
                Arrays.asList((Formatting[])def).forEach(format -> {
                    strFmts.add(format.getName().toUpperCase());
                });
                category.addEntry(
                    iBldr.startStrList(texts[0], strFmts)
                        .setDefaultValue(strFmts)
                        .setTooltip(texts[1])
                        .setSaveConsumer(saveList)
                    .build()
                );
            break;
            default:
                WheresMyChatHistory.log.error("Invalid type '{}', expected one of the following: String,Boolean,Formatting[]", def.getClass().getName());
            break;
        }
        return iBldr;
    }


    public static void load() {
        File cfgFile = new File(FabricLoader.getInstance().getConfigDir().toFile().getAbsolutePath()+File.separator+"wmch.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        try {
            if(cfgFile.exists()) {
                FileReader fr = new FileReader(cfgFile);
                Config.cfg = gson.fromJson(fr, Config.class);
                WheresMyChatHistory.log.debug("Loaded config info {} from '{}'", cfg.toString(), cfgFile.getAbsolutePath());
                fr.close();
            } else {
                WheresMyChatHistory.log.warn("Could not find wmch's config file (searched in '{}'); creating a default one now.");
                reset();
                save();
            }
        } catch(Exception e) {
            WheresMyChatHistory.log.error("An error occurred while trying to load wmch's config data: {}",(Object)e.getStackTrace());
        }
    }
    public static void save() {
        try {
            String cfgPath = FabricLoader.getInstance().getConfigDir().toFile().getAbsolutePath()+File.separator+"wmch.json";
            FileWriter fw = new FileWriter(cfgPath);
            fw.write( new GsonBuilder().setPrettyPrinting().create().toJson(cfg, Config.class) );
            WheresMyChatHistory.log.debug("Saved config info {} from '{}'", cfg.toString(), cfgPath);
            fw.close();
        } catch(Exception e) {
            WheresMyChatHistory.log.error("An error occurred while trying to save wmch's config data: {}",(Object)e.getStackTrace());
        }
    }
    public static void reset() {
        Config.cfg = new Config(true);
    }


    @Override
    public String toString() {
        return String.format(
            "{\n\ttime_enabled: %b,\n\ttime_text: '%s',\n\ttime_formatting: %s,\n\thover_enabled: %b,\n\thover_string: '%s',\n\tboundary_enabled: %b,\n\tboundary_string: '%s',\n\tboundary_formatting: '%s',\n\tmax_messages: %d,\n}",
            this.time_enabled, this.time_text, Arrays.asList(this.time_formatting).toString(),
            this.hover_enabled, this.hover_string,
            this.boundary_enabled, this.boundary_string, Arrays.asList(this.boundary_formatting).toString(),
            this.max_messages
        );
    }
}