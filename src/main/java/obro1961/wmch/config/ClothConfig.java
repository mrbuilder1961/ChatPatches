package obro1961.wmch.config;

import static obro1961.wmch.config.Option.BOUNDARY;
import static obro1961.wmch.config.Option.BOUNDARYCOLOR;
import static obro1961.wmch.config.Option.BOUNDARYSTR;
import static obro1961.wmch.config.Option.COUNTER;
import static obro1961.wmch.config.Option.COUNTERCOLOR;
import static obro1961.wmch.config.Option.COUNTERSTR;
import static obro1961.wmch.config.Option.HOVER;
import static obro1961.wmch.config.Option.HOVERSTR;
import static obro1961.wmch.config.Option.LENIANTEQUALS;
import static obro1961.wmch.config.Option.MAXMSGS;
import static obro1961.wmch.config.Option.NAMESTR;
import static obro1961.wmch.config.Option.TIME;
import static obro1961.wmch.config.Option.TIMECOLOR;
import static obro1961.wmch.config.Option.TIMEFORMAT;
import static obro1961.wmch.config.Option.TIMESTR;

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
        //ConfigCategory counter = bldr.getOrCreateCategory(new TranslatableText("text.wmch.counter_category"));
        ConfigCategory boundary = bldr.getOrCreateCategory(new TranslatableText("text.wmch.boundary_category"));
        ConfigCategory other = bldr.getOrCreateCategory(new TranslatableText("text.wmch.other_category"));

        eBldr = TIME.updateEntryBuilder(eBldr, time, TIME);
        eBldr = TIMESTR.updateEntryBuilder(eBldr, time, TIMESTR);
        eBldr = TIMEFORMAT.updateEntryBuilder(eBldr, time, TIMEFORMAT);
        eBldr = TIMECOLOR.updateEntryBuilder(eBldr, time, TIMECOLOR);

        eBldr = HOVER.updateEntryBuilder(eBldr, hover, HOVER);
        eBldr = HOVERSTR.updateEntryBuilder(eBldr, hover, HOVERSTR);

        /* eBldr = COUNTER.updateEntryBuilder(eBldr, counter, COUNTER);
        eBldr = COUNTERSTR.updateEntryBuilder(eBldr, counter, COUNTERSTR);
        eBldr = COUNTERCOLOR.updateEntryBuilder(eBldr, counter, COUNTERCOLOR);
        eBldr = LENIANTEQUALS.updateEntryBuilder(eBldr, counter, LENIANTEQUALS); */

        eBldr = BOUNDARY.updateEntryBuilder(eBldr, boundary, BOUNDARY);
        eBldr = BOUNDARYSTR.updateEntryBuilder(eBldr, boundary, BOUNDARYSTR);
        eBldr = BOUNDARYCOLOR.updateEntryBuilder(eBldr, boundary, BOUNDARYCOLOR);

        eBldr = NAMESTR.updateEntryBuilder(eBldr, other, NAMESTR);
        eBldr = MAXMSGS.updateEntryBuilder(eBldr, other, MAXMSGS);


        bldr.setSavingRunnable(() -> {
            logDiffs();
            write(cfg);
            lg.info("Saved config data from the Mod Menu interface using Cloth Config!");
        });

        return bldr.build();
    }
}