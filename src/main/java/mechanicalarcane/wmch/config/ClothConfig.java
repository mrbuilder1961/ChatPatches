package mechanicalarcane.wmch.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import mechanicalarcane.wmch.WMCH;
import mechanicalarcane.wmch.util.Util;
import mechanicalarcane.wmch.util.Util.Flag;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** The extended config menu, supported by Cloth Config and Mod Menu. @see Config */
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
        eBldr = Option.NAME_STR.updateEntryBuilder(eBldr, hud);
        eBldr = Option.MAX_MESSAGES.updateEntryBuilder(eBldr, hud);

        // debug options
        if(WMCH.FABRICLOADER.isDevelopmentEnvironment()) {
            ConfigCategory debug = bldr.getOrCreateCategory(Text.of("Debug things"));

            debug.addEntry(
                eBldr.startBooleanToggle(Util.formatString("&cPrint GitHub Tables"), false)
                    .setDefaultValue(false)
                    .setTooltip(Text.of("puts them into the logs at warn level"))
                    .setSaveConsumer(inc -> { if(inc) Option.printTableEntries(); })
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
            /* debug.addEntry(
                eBldr.startBooleanToggle( Text.of("whatever this was supposed to be"), false )
                    .setDefaultValue( false )
                    .setTooltip( Text.of("help me") )
                .build()
            ); */
        }

        bldr.setSavingRunnable(() -> {
            Option.logDiff();
            WMCH.config.writeToFile();
            WMCH.LOGGER.info("[ClothConfig.save] Finished validating the Mod Menu/Cloth Config config!");
        });

        return bldr.build();
    }
}