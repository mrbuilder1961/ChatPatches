package mechanicalarcane.wmch.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import com.google.gson.JsonObject;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import mechanicalarcane.wmch.WMCH;
import mechanicalarcane.wmch.util.Util;
import mechanicalarcane.wmch.util.Util.Flag;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * The extended config menu, supported by Cloth Config and Mod Menu.
 * @see Config
 */
public class ClothConfig extends Config {
    protected ClothConfig() {
        super();
    }

    public Screen getWMCHConfigScreen(Screen prevScreen) {
        ConfigBuilder bldr = ConfigBuilder.create()
            .setParentScreen(prevScreen)
            .setDoesConfirmSave(true)
            .setTitle(Text.translatable("text.wmch.title"));
        ConfigEntryBuilder eBldr = bldr.entryBuilder();

        ConfigCategory time = bldr.getOrCreateCategory(Text.translatable("text.wmch.category.time"));
        ConfigCategory hover = bldr.getOrCreateCategory(Text.translatable("text.wmch.category.hover"));
        ConfigCategory counter = bldr.getOrCreateCategory(Text.translatable("text.wmch.category.counter"));
        ConfigCategory boundary = bldr.getOrCreateCategory(Text.translatable("text.wmch.category.boundary"));
        ConfigCategory hud = bldr.getOrCreateCategory(Text.translatable("text.wmch.category.hud"));
        //ConfigCategory other = bldr.getOrCreateCategory(Text.translatable("text.wmch.category.other"));

        eBldr = Option.TIME.updateEntryBuilder(eBldr, time);
        eBldr = Option.TIME_STR.updateEntryBuilder(eBldr, time);
        eBldr = Option.TIME_FORMAT.updateEntryBuilder(eBldr, time);
        eBldr = Option.TIME_COLOR.updateEntryBuilder(eBldr, time);

        eBldr = Option.HOVER.updateEntryBuilder(eBldr, hover);
        eBldr = Option.HOVER_STR.updateEntryBuilder(eBldr, hover);

        eBldr = Option.COUNTER.updateEntryBuilder(eBldr, counter);
        eBldr = Option.COUNTER_STR.updateEntryBuilder(eBldr, counter);
        eBldr = Option.COUNTER_COLOR.updateEntryBuilder(eBldr, counter);

        eBldr = Option.BOUNDARY.updateEntryBuilder(eBldr, boundary);
        eBldr = Option.BOUNDARY_STR.updateEntryBuilder(eBldr, boundary);
        eBldr = Option.BOUNDARY_COLOR.updateEntryBuilder(eBldr, boundary);

        eBldr = Option.SAVE_CHAT.updateEntryBuilder(eBldr, hud);
        eBldr = Option.SHIFT_HUD_POS.updateEntryBuilder(eBldr, hud);
        eBldr = Option.MAX_MESSAGES.updateEntryBuilder(eBldr, hud);
        eBldr = Option.NAME_STR.updateEntryBuilder(eBldr, hud);
        //eBldr = Option.HIDE_UNSECURE_NOTIF.updateEntryBuilder(eBldr, hud);

        // debug options
        if(WMCH.FABRICLOADER.isDevelopmentEnvironment()) {
            ConfigCategory debug = bldr.getOrCreateCategory(Text.of("Debug tings"));

            debug.addEntry(
                eBldr.startBooleanToggle(Util.formatString("&cPrint GitHub Tables"), false)
                    .setDefaultValue(false)
                    .setTooltip(Text.of("puts them into the logs at warn level"))
                    .setSaveConsumer(inc -> { if(inc) Option.printTableEntries(); })
                .build()
            );
            debug.addEntry(
                eBldr.startBooleanToggle(Util.formatString("&6Propogate Lang Files"), false)
                    .setDefaultValue(false)
                    .setTooltip(Text.of("replaces all da files with the one from en_us.json"))
                    .setSaveConsumer(inc -> {
                        if(inc) {
                            File lang = new File(java.nio.file.Paths.get("").toAbsolutePath().toString().replace("\\run", "") + "/src/main/resources/assets/wmch/lang/");
                            try(
                                FileReader in = new FileReader(lang + "/en_us.json");

                                FileWriter au = new FileWriter(lang + "/en_au.json");
                                FileWriter ca = new FileWriter(lang + "/en_ca.json");
                                FileWriter gb = new FileWriter(lang + "/en_gb.json");
                                FileWriter nz = new FileWriter(lang + "/en_nz.json");
                            ) {
                                com.google.gson.Gson json = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
                                final Class<JsonObject> obj = JsonObject.class;
                                JsonObject enUSFile = json.fromJson(in, obj);

                                json.toJson(enUSFile, obj, au);
                                json.toJson(enUSFile, obj, ca);
                                json.toJson(enUSFile, obj, gb);
                                json.toJson(enUSFile, obj, nz);
                            } catch (Exception e) {
                                WMCH.LOGGER.warn("[debug]: writing lang files failed:");
                                e.printStackTrace();
                            }
                        }
                    })
                .build()
            );
            debug.addEntry(
                eBldr.startIntSlider(
                    Util.formatString("&dEdit Flag.flags = (%d^10 / %s^2)".formatted(Util.Flag.flags, Flag.binary()) ),
                    Util.Flag.flags, 0, 8
                )
                    .setDefaultValue(0)
                    .setTooltip(Text.of("manually change bit flags"))
                    .setSaveConsumer(n -> Util.Flag.flags = n)
                .build()
            );
        }

        bldr.setSavingRunnable(() -> {
            Option.logDiffs();
            WMCH.config.writeToFile();
            WMCH.LOGGER.info("Finished validating the Mod Menu/Cloth Config config!");
        });

        return bldr.build();
    }
}