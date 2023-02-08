package obro1961.chatpatches.config;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.*;
import obro1961.chatpatches.ChatPatches;
import obro1961.chatpatches.util.Util;
import org.apache.commons.compress.utils.Lists;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.function.Consumer;

import static obro1961.chatpatches.ChatPatches.LOGGER;

/**
 * The extended config menu, supported by Cloth Config and Mod Menu. @see Config
 */
public class ClothConfig extends Config {
    public ClothConfig() {
        super();
    }

    @Override
    public Screen getConfigScreen(Screen prevScreen) {
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(prevScreen)
            .setTransparentBackground(true)
            .setShouldListSmoothScroll(true)
            .setDoesConfirmSave(true)
            .setTitle(new TranslatableText("text.chatpatches.title"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        List<AbstractConfigListEntry<?>> timeOpts = Lists.newArrayList();
        List<AbstractConfigListEntry<?>> hoverOpts = Lists.newArrayList();
        List<AbstractConfigListEntry<?>> counterOpts = Lists.newArrayList();
        List<AbstractConfigListEntry<?>> boundaryOpts = Lists.newArrayList();
        List<AbstractConfigListEntry<?>> hudOpts = Lists.newArrayList();


        Config.getOptions().forEach(opt -> {
            String cat = opt.key.split("[A-Z]")[0];
            if( !I18n.hasTranslation("text.chatpatches.category." + cat) )
                cat = "hud";


            AbstractConfigListEntry<?> ccOpt;
            if(opt.key.matches("^.*(?:Str|Date|Format)$")) // endsWith "Str" "Date" or "Format"
                ccOpt = string(entryBuilder, opt.key, null);

            else if(opt.key.contains("Color"))
                ccOpt = color(entryBuilder, opt.key, null);

            else if(opt.key.matches("^(?:maxMsgs|shiftChat|chatWidth)$")) // "maxMsgs" "shiftChat" or "chatWidth"
                ccOpt = number(
                    entryBuilder,
                    opt.key,
                    0,
                    opt.key.equals("maxMsgs") ? Short.MAX_VALUE : opt.key.equals("shiftChat") ? 100 : 630,
                    true,
                    opt.key.matches("^(?:shiftChat|chatWidth)$") ? client -> client.inGameHud.getChatHud().reset() : null
                );

            else
                ccOpt = toggle(entryBuilder, opt.key, null);


            switch(cat) {
                case "time" -> timeOpts.add(ccOpt);
                case "hover" -> hoverOpts.add(ccOpt);
                case "counter" -> counterOpts.add(ccOpt);
                case "boundary" -> boundaryOpts.add(ccOpt);
                case "hud" -> hudOpts.add(ccOpt);
            }
        });

        category(builder, "time", timeOpts);
        category(builder, "hover", hoverOpts);
        category(builder, "counter", counterOpts);
        category(builder, "boundary", boundaryOpts);
        category(builder, "hud", hudOpts);
        category(builder, "help",
            List.of(
                label( entryBuilder, new TranslatableText("text.chatpatches.dateFormat"), null, "https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html" ),
                label( entryBuilder, new TranslatableText("text.chatpatches.formatCodes"), null, "https://minecraft.gamepedia.com/Formatting_codes" ),
                label( entryBuilder, new LiteralText("README -> FAQ"), null, "https://github.com/mrbuilder1961/ChatPatches#faq" )
            )
        );

        // debug options
        if(ChatPatches.FABRIC_LOADER.isDevelopmentEnvironment()) {
            category(
                builder,
                "debug",
                List.of(
                    entryBuilder.startIntSlider(
                            Text.of("Edit Bit Flags (" + Util.Flags.flags + "^10, " + Util.Flags.binary() + "^2)"),
                            Util.Flags.flags, 0, 0b1111
                        )
                        .setDefaultValue(Util.Flags.flags)
                        .setSaveConsumer(inc -> Util.Flags.flags = inc)
                        .build(),

                    entryBuilder.startBooleanToggle(Text.of("Print GitHub Tables"), false)
                        .setDefaultValue(false)
                        .setSaveConsumer(inc -> {if(inc) {
                            StringBuilder str = new StringBuilder();

                            Config.getOptions().forEach(opt -> {
                                Object def = opt.def;
                                String key = opt.key;

                                str.append("\n| %s | %s | %s | `text.chatpatches.%s` |".formatted(
                                    I18n.translate("text.chatpatches." + key),
                                    opt.get().getClass().equals( Integer.class ) && key.contains("Color")
                                        ? "`0x" + Integer.toHexString((int) def).toUpperCase() + "` (`" + def + "`)"
                                        : "`" + def + "`",
                                    I18n.translate("text.chatpatches.desc." + key),
                                    key
                                ));
                            });

                            LOGGER.warn("[ClothConfig.printGithubTables]" + str);
                        }})
                        .build()
                )
            );
        }


        builder.setSavingRunnable(() -> {
            write();
            LOGGER.info("[ClothConfig] Saved config data from the Mod Menu interface using Cloth Config!");
        });

        return builder.build();
    }

    public static void category(ConfigBuilder builder, String key, List<AbstractConfigListEntry<?>> entries) {
        ConfigCategory category = builder.getOrCreateCategory(new TranslatableText("text.chatpatches.category." + key));
        entries.forEach(category::addEntry);
    }


    public static TextListEntry label(ConfigEntryBuilder entryBuilder, MutableText display, String tooltip, String url) {
        return
            entryBuilder.startTextDescription(
                url == null ? display : display.fillStyle( Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url)) )
            )
            .setTooltip(new LiteralText( tooltip == null ? "§9" + url : tooltip ))
            .build();
    }

    public static ColorEntry color(ConfigEntryBuilder entryBuilder, String key, @Nullable Consumer<MinecraftClient> flag) {
        ConfigOption<Integer> opt = Config.getOption(key);

        return entryBuilder.startColorField(new TranslatableText("text.chatpatches." + key), opt.get())
            .setDefaultValue(opt.def)
            .setTooltip(new TranslatableText("text.chatpatches.desc." + key))
            .setSaveConsumer(color -> {
                opt.set( color, 0xFFFFFF >= color && color >= 0 );
                if(flag != null)
                    flag.accept(MinecraftClient.getInstance());
            })
            .setAlphaMode(false)
        .build();
    }

    public static TooltipListEntry<Integer> number(ConfigEntryBuilder entryBuilder, String key, int min, int max, boolean slider, @Nullable Consumer<MinecraftClient> flag) {
        ConfigOption<Integer> opt = Config.getOption(key);
        Consumer<Integer> saver = num -> {
            opt.set(num, min <= num && num <= max);
            if(flag != null)
                flag.accept(MinecraftClient.getInstance());
        };

        return slider
            ? entryBuilder.startIntSlider(new TranslatableText("text.chatpatches." + key), opt.get(), min, max)
                .setDefaultValue(opt.def)
                .setTooltip(new TranslatableText("text.chatpatches.desc." + key))
                .setSaveConsumer(saver)
                .build()
            : entryBuilder.startIntField(new TranslatableText("text.chatpatches." + key), opt.get())
                .setDefaultValue(opt.def)
                .setTooltip(new TranslatableText("text.chatpatches.desc." + key))
                .setSaveConsumer(saver) // min, max
                .build()
            ;
    }

    public static StringListEntry string(ConfigEntryBuilder entryBuilder, String key, @Nullable Consumer<MinecraftClient> flag) {
        ConfigOption<String> opt = Config.getOption(key);

        return entryBuilder.startStrField(new TranslatableText("text.chatpatches." + key), opt.get())
            .setDefaultValue(opt.def)
            .setTooltip(new TranslatableText("text.chatpatches.desc." + key))
            .setSaveConsumer(inc -> {
                switch(opt.key) {
                    case "Format" -> opt.set(inc, inc.contains("$"));
                    case "Date" -> {
                        try {
                            new SimpleDateFormat(inc);
                            opt.set(inc);
                        } catch (IllegalArgumentException e) {
                            ChatPatches.LOGGER.error("[ClothConfig.getBinding] Invalid date format '{}' provided for '{}'", inc, opt.key);
                        }
                    }
                    default -> opt.set(inc);
                }

                if(flag != null)
                    flag.accept(MinecraftClient.getInstance());
            })
            .build();
    }

    public static BooleanListEntry toggle(ConfigEntryBuilder entryBuilder, String key, @Nullable Consumer<MinecraftClient> flag) {
        ConfigOption<Boolean> opt = Config.getOption(key);

        return entryBuilder.startBooleanToggle(new TranslatableText("text.chatpatches." + key), opt.get())
            .setDefaultValue(opt.def)
            .setTooltip(new TranslatableText("text.chatpatches.desc." + key))
            .setSaveConsumer(bool -> {
                opt.set(bool);
                if(flag != null)
                    flag.accept(MinecraftClient.getInstance());
            })
            .build();
    }
}