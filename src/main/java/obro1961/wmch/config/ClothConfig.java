package obro1961.wmch.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Language;
import obro1961.wmch.WMCH;
import obro1961.wmch.util.Util;

/**
 * The extended config menu, supported by Cloth Config and Mod Menu.
 * @see Config
 */
public class ClothConfig extends Config {
    public ClothConfig() {
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
        ConfigCategory other = bldr.getOrCreateCategory(Text.translatable("text.wmch.category.other"));

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

        eBldr = Option.SAVE_CHAT.updateEntryBuilder(eBldr, other);
        //eBldr = Option.HIDE_UNSECURE_NOTIF.updateEntryBuilder(eBldr, other);
        eBldr = Option.NAME_STR.updateEntryBuilder(eBldr, other);
        eBldr = Option.MAX_MESSAGES.updateEntryBuilder(eBldr, other);

        if(WMCH.fbl.isDevelopmentEnvironment())
            other.addEntry(
                eBldr.startBooleanToggle(Util.getStrTextF("&cPrint GitHub Tables"), false)
                    .setDefaultValue(false)
                    .setTooltip(Text.of("Debug button"))
                    .setSaveConsumer(inc -> { if(inc) Option.printTableEntries(Language.getInstance()); })
                    .build()
                );

        bldr.setSavingRunnable(() -> {
            logDiffs();
            write(WMCH.config);
            lg.info("Saved config data from the Mod Menu interface using Cloth Config!");
        });

        return bldr.build();
    }
}