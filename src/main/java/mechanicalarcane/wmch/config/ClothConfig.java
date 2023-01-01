package mechanicalarcane.wmch.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import mechanicalarcane.wmch.WMCH;
import mechanicalarcane.wmch.util.Util;
import mechanicalarcane.wmch.util.Util.Flags;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static mechanicalarcane.wmch.WMCH.LOGGER;

/**
 * The extended config menu, supported by Cloth Config and Mod Menu.
 * @see Config
 */
public class ClothConfig extends Config {

    public Screen getWMCHConfigScreen(Screen prevScreen) {
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(prevScreen)
            .setDoesConfirmSave(true)
            .setTitle(Text.translatable("text.wmch.title"))
            .transparentBackground();

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        ConfigCategory time = builder.getOrCreateCategory(Text.translatable("text.wmch.category.time"));
        ConfigCategory hover = builder.getOrCreateCategory(Text.translatable("text.wmch.category.hover"));
        ConfigCategory counter = builder.getOrCreateCategory(Text.translatable("text.wmch.category.counter"));
        ConfigCategory boundary = builder.getOrCreateCategory(Text.translatable("text.wmch.category.boundary"));
        ConfigCategory hud = builder.getOrCreateCategory(Text.translatable("text.wmch.category.hud"));
        final List<ConfigCategory> categories = new ArrayList<>(Arrays.asList( time, hover, counter, boundary, hud ));


        /*entryBuilder = */ updateBuilder(builder, entryBuilder); // adds all Options to ClothConfig


        // adds info description links to formatting codes and date format Options
        for(int i = 0; i < 4; ++i) {
            ConfigCategory cat = categories.get(i);

            if(i < 2)
                cat.addEntry(
                    entryBuilder.startTextDescription(
                        Util.formatString(I18n.translate("text.wmch.dateFormat"))
                        .fillStyle( Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html")) )
                    )
                    .build()
                );

            cat.addEntry(
                entryBuilder.startTextDescription(
                    Util.formatString(I18n.translate("text.wmch.formatCodes"))
                    .fillStyle( Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://minecraft.gamepedia.com/Formatting_codes")) )
                )
                .build()
            );
        }


        // debug options
        if(WMCH.FABRICLOADER.isDevelopmentEnvironment()) {
            ConfigCategory debug = builder.getOrCreateCategory(Text.of("Debug things"));

            debug.addEntry(
                entryBuilder.startBooleanToggle(Util.formatString("&cPrint GitHub Tables"), false)
                    .setDefaultValue(false)
                    .setTooltip(Text.of("puts them into the logs at warn level"))
                    .setSaveConsumer(inc -> {if(inc) {
                        StringBuilder str = new StringBuilder();

                        Config.getOptions().forEach(opt -> {
                            Object def = opt.def;
                            String key = opt.key;

                            str.append("\n| %s | %s | %s | `text.wmch.%s` |".formatted(
                                I18n.translate("text.wmch." + key),
                                opt.get().getClass().equals( Integer.class ) && key.contains("Color")
                                    ? "`0x" + Integer.toHexString((int) def).toUpperCase() + "` (`" + def + "`)"
                                    : "`" + def + "`",
                                I18n.translate("text.wmch.desc." + key),
                                key
                            ));
                        });

                        LOGGER.warn("[ClothConfig.printGithubTables]" + str);
                    }})
                .build()
            );
            debug.addEntry(
                entryBuilder.startIntSlider(
                    Util.formatString("&dEdit Flag.flags = (%d^10 / %s^2)".formatted(Flags.flags, Flags.binary()) ),
                    Flags.flags, 0, 15
                )
                    .setDefaultValue(0)
                    .setTooltip(Text.of("manually change bit flags"))
                    .setSaveConsumer(n -> Flags.flags = n)
                .build()
            );

        }


        Config.read();

        builder.setSavingRunnable(() -> {
            write();
            WMCH.LOGGER.info("[ClothConfig.save] Finished validating the Mod Menu/Cloth Config config!");
        });

        return builder.build();
    }

    /** Adds all Config options to the Cloth Config menu. */
    private void updateBuilder(ConfigBuilder catBuilder, ConfigEntryBuilder builder) {
        for(Option<?> option : Config.getOptions()) {
            String key = option.key;
            Text name = Text.translatable("text.wmch." + key);
            Text desc = Text.translatable("text.wmch.desc." + key);

            String cat = key.split("[A-Z]")[0];
            if( !I18n.hasTranslation("text.wmch.category." + cat) )
                cat = "hud";
            ConfigCategory category = catBuilder.getOrCreateCategory(Text.translatable("text.wmch.category." + cat));


            switch (option.get().getClass().getSimpleName()) {
                case "String" ->
                    category.addEntry(
                        builder.startStrField( name, option.get().toString() )
                            .setDefaultValue( option.def.toString() )
                            .setTooltip(desc)
                            .setSaveConsumer(
                                key.endsWith("Date")
                                    ? str -> {
                                        try {
                                            new SimpleDateFormat(str);
                                            option.set(str);
                                        } catch(IllegalArgumentException e) {
                                            LOGGER.error("[ClothConfig.save_DATE] An invalid or illegal character was provided in a date format string:", e);
                                        }
                                    }
                                    : key.endsWith("Str") || key.endsWith("Format") // $ and §->&
                                        ? str -> option.set(str.replaceAll("§", "&").replaceAll("\n", "\\\\n"), str.contains("$"))
                                        : option::set // not sure when this would happen, but hopefully this avoids any accidental ignoring of new values
                            )
                            .build()
                    );
                case "Integer" -> {
                    boolean maxMsgs = key.equals("maxMsgs");
                    category.addEntry(
                        List.of("maxMsgs", "shiftChat").contains(key)
                            ? builder.startIntSlider(name, (int)option.get(), maxMsgs ? 100 : 0, maxMsgs ? Short.MAX_VALUE : MinecraftClient.getInstance().getWindow().getHeight() / 2)
                                .setDefaultValue( (int)option.def )
                                .setTooltip(desc)
                                .setSaveConsumer(num -> {
                                    if(maxMsgs)
                                        option.set( num, Short.MAX_VALUE >= num && num >= 100 );
                                    else
                                        option.set( num, 1080 >= num && num >= 0 );
                                })
                                .build()
                            : builder.startColorField(name, (int)option.get())
                                .setDefaultValue( (int)option.def )
                                .setTooltip(desc)
                                .setSaveConsumer(color -> option.set( color, 0xFFFFFF >= color && color >= 0 ))
                                .build()
                    );
                }
                case "Boolean" ->
                    category.addEntry(
                        builder.startBooleanToggle( name, (boolean)option.get() )
                            .setDefaultValue( (boolean)option.def )
                            .setTooltip(desc)
                            .setSaveConsumer(option::set)
                            .build()
                    );
                default ->
                    throw new IllegalArgumentException("[ClothConfig.updateBuilder] Invalid type '" + option.get().getClass().getSimpleName() + "', expected String, Integer, or Boolean: ");
            }
        }
    }
}