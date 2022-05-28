package obro1961.wmch.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.TranslatableText;

/**
 * The extended config menu, supported by Cloth Config and Mod Menu. @see Config
 */
public class ClothConfig extends Config {
    public ClothConfig() {
        super();
    }

    /**
     * Returns the config screen for the Where's My Chat History mod. Responsible
     * for validating and parsing all config values.
     *
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

        eBldr = Option.TIME.updateEntryBuilder(eBldr, time, Option.TIME);
        eBldr = Option.TIMESTR.updateEntryBuilder(eBldr, time, Option.TIMESTR);
        eBldr = Option.TIMEFORMAT.updateEntryBuilder(eBldr, time, Option.TIMEFORMAT);
        eBldr = Option.TIMECOLOR.updateEntryBuilder(eBldr, time, Option.TIMECOLOR);

        eBldr = Option.HOVER.updateEntryBuilder(eBldr, hover, Option.HOVER);
        eBldr = Option.HOVERSTR.updateEntryBuilder(eBldr, hover, Option.HOVERSTR);

        eBldr = Option.COUNTER.updateEntryBuilder(eBldr, counter, Option.COUNTER);
        eBldr = Option.COUNTERSTR.updateEntryBuilder(eBldr, counter, Option.COUNTERSTR);
        eBldr = Option.COUNTERCOLOR.updateEntryBuilder(eBldr, counter, Option.COUNTERCOLOR);
        //eBldr = Option.DUPETHRESHOLD.updateEntryBuilder(eBldr, counter, Option.DUPETHRESHOLD);
        eBldr = Option.LENIANTEQUALS.updateEntryBuilder(eBldr, counter, Option.LENIANTEQUALS);

        eBldr = Option.BOUNDARY.updateEntryBuilder(eBldr, boundary, Option.BOUNDARY);
        eBldr = Option.BOUNDARYSTR.updateEntryBuilder(eBldr, boundary, Option.BOUNDARYSTR);
        eBldr = Option.BOUNDARYCOLOR.updateEntryBuilder(eBldr, boundary, Option.BOUNDARYCOLOR);

        eBldr = Option.NAMESTR.updateEntryBuilder(eBldr, other, Option.NAMESTR);
        eBldr = Option.MAXMSGS.updateEntryBuilder(eBldr, other, Option.MAXMSGS);


        bldr.setSavingRunnable(() -> {
            logDiffs();
            write(cfg);
            lg.info("Saved config data from the Mod Menu interface using Cloth Config!");
        });

        return bldr.build();
    }
}