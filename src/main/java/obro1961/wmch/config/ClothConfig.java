package obro1961.wmch.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Language;
import obro1961.wmch.WMCH;
import obro1961.wmch.util.Util;

/**
 * The extended config menu, supported by Cloth Config and Mod Menu. @see Config
 */
public class ClothConfig extends Config {
    public ClothConfig() {
        super();
    }

    public Screen getWMCHConfigScreen(Screen prevScreen) {
        ConfigBuilder bldr = ConfigBuilder.create()
            .setParentScreen(prevScreen)
            .setDoesConfirmSave(true)
        .setTitle(new TranslatableText("text.wmch.title"));
        ConfigEntryBuilder eBldr = bldr.entryBuilder();

        ConfigCategory time = bldr.getOrCreateCategory(new TranslatableText("text.wmch.category.time"));
        ConfigCategory hover = bldr.getOrCreateCategory(new TranslatableText("text.wmch.category.hover"));
        ConfigCategory counter = bldr.getOrCreateCategory(new TranslatableText("text.wmch.category.counter"));
        ConfigCategory boundary = bldr.getOrCreateCategory(new TranslatableText("text.wmch.category.boundary"));
        ConfigCategory other = bldr.getOrCreateCategory(new TranslatableText("text.wmch.category.other"));

        eBldr = Option.TIME.updateEntryBuilder(eBldr, time, Option.TIME);
        eBldr = Option.TIMESTR.updateEntryBuilder(eBldr, time, Option.TIMESTR);
        eBldr = Option.TIMEFORMAT.updateEntryBuilder(eBldr, time, Option.TIMEFORMAT);
        eBldr = Option.TIMECOLOR.updateEntryBuilder(eBldr, time, Option.TIMECOLOR);

        eBldr = Option.HOVER.updateEntryBuilder(eBldr, hover, Option.HOVER);
        eBldr = Option.HOVERSTR.updateEntryBuilder(eBldr, hover, Option.HOVERSTR);

        eBldr = Option.COUNTER.updateEntryBuilder(eBldr, counter, Option.COUNTER);
        eBldr = Option.COUNTERSTR.updateEntryBuilder(eBldr, counter, Option.COUNTERSTR);
        eBldr = Option.COUNTERCOLOR.updateEntryBuilder(eBldr, counter, Option.COUNTERCOLOR);

        eBldr = Option.BOUNDARY.updateEntryBuilder(eBldr, boundary, Option.BOUNDARY);
        eBldr = Option.BOUNDARYSTR.updateEntryBuilder(eBldr, boundary, Option.BOUNDARYSTR);
        eBldr = Option.BOUNDARYCOLOR.updateEntryBuilder(eBldr, boundary, Option.BOUNDARYCOLOR);

        eBldr = Option.SAVECHAT.updateEntryBuilder(eBldr, other, Option.SAVECHAT);
        eBldr = Option.NAMESTR.updateEntryBuilder(eBldr, other, Option.NAMESTR);
        eBldr = Option.MAXMSGS.updateEntryBuilder(eBldr, other, Option.MAXMSGS);

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