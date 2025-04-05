package obro1961.chatpatches.config;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.*;
import dev.isxander.yacl3.gui.YACLScreen;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import obro1961.chatpatches.ChatPatches;
import obro1961.chatpatches.chatlog.ChatLog;
import org.apache.commons.lang3.StringUtils;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

import static obro1961.chatpatches.ChatPatches.config;

/**
 * The YetAnotherConfigLib config class.
 * @see Config
 * @apiNote This is the 2nd edition of a config menu using external libraries.
 */
public class YACLConfig extends Config {

    @Override
    public Screen getConfigScreen(Screen parent) {
        ObjectList<Option<?>> timeOpts = new ObjectArrayList<>(),
                        hoverOpts = new ObjectArrayList<>(),
                        counterOpts = new ObjectArrayList<>(),
                        compactChatOpts = new ObjectArrayList<>(),
                        boundaryOpts = new ObjectArrayList<>(),
                        chatlogOpts = new ObjectArrayList<>(),
                        chatlogActions = new ObjectArrayList<>(),
                        chatNameOpts = new ObjectArrayList<>(),
                        chatHudOpts = new ObjectArrayList<>(),
                        chatScreenOpts = new ObjectArrayList<>(),
                        copyMenuOpts = new ObjectArrayList<>();

        config.getOptions().forEach(opt -> {
            String key = opt.key; // effectively final
            String cat = key.split("[A-Z]")[0];

            if(key.matches("caseSensitive|formatting|regex")) // search settings are edited in the chat screen
                return;
            else if( key.contains("counterCompact") )
                cat = "compact";
            else if( !I18n.hasTranslation("text.chatpatches.category." + cat) )
                cat = "screen";
            else if( key.contains("Name") )
                cat = "name";

            if(key.contains("Color")) {
                opt = new Setting<>(new Color( (int)opt.get() ), new Color( (int)opt.def ), key) {
                    @Override
                    public Color get() {
                        return new Color( (int)getOption(key).get() );
                    }

                    @Override
                    public void set(Object value) {
                        super.set( ((Color)value).getRGB() - 0xff000000 );
                    }
                };
            }

            Option<?> yaclOpt =
                Option.createBuilder()
                    .name(Text.translatable("text.chatpatches." + key))
                    .description(desc(opt))
                    .controller(me -> getController(me, key))
                    .binding(getBinding(opt))
                    .flag(
                        StringUtils.containsIgnoreCase(key, "chat")
                            ? new OptionFlag[] { client -> client.inGameHud.getChatHud().reset() }
                            : new OptionFlag[0]
                    )
                    .build();


            switch(cat) {
                case "time" -> timeOpts.add(yaclOpt);
                case "hover" -> hoverOpts.add(yaclOpt);
                case "counter" -> counterOpts.add(yaclOpt);
                case "compact" -> compactChatOpts.add(yaclOpt);
                case "boundary" -> boundaryOpts.add(yaclOpt);
                case "chatlog" -> chatlogOpts.add(yaclOpt);
                case "name" -> chatNameOpts.add(yaclOpt);
                case "chat" -> chatHudOpts.add(yaclOpt);
                case "screen" -> chatScreenOpts.add(yaclOpt);
                case "copy" -> copyMenuOpts.add(yaclOpt);
            }
        });

        /* for action buttons */
        // see https://discord.com/channels/507304429255393322/507982478276034570/1175256182525534218
        ObjectList<String> actionKeys = ObjectList.of("chatlogClear", "chatlogClearHistory", "chatlogClearMessages", "chatlogLoad", "chatlogSave", "chatlogBackup", "chatlogOpenFolder");
        for(String key : actionKeys) {
            // creates an args array for the translatable string, which is either the message count, history count, or -1
            Object[] args = { key.equals("chatlogClearMessages") ? ChatLog.messageCount() : key.equals("chatlogClearHistory") ? ChatLog.historyCount() : -1 };
            chatlogActions.add( action(key, args) );
        }


        YetAnotherConfigLib.Builder builder = YetAnotherConfigLib.createBuilder()
            .title(Text.translatable("text.chatpatches.title"))
                .category( category("time", timeOpts) )
                .category( category("hover", hoverOpts) )
                .category( category("counter", counterOpts, group(
                    "counter.compact", compactChatOpts, Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://modrinth.com/mod/compact-chat"))
                )) )
                .category( category("boundary", boundaryOpts) )
                .category( category("chatlog", chatlogOpts, group("chatlog.actions", chatlogActions, null)) )
                .category( category("chat", ObjectList.of(),
                    group("chat.name", chatNameOpts, null), group("chat.hud", chatHudOpts, null), group("chat.screen", chatScreenOpts, null)) )
                .category( category("copy", copyMenuOpts) )

                .category(
                    category(
                    "help",
                        ObjectList.of(
                            action("help.reloadConfig", -1),
                            label( Text.translatable("text.chatpatches.help.dateFormat"), "https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html" ),
                            label( Text.translatable("text.chatpatches.help.formatCodes"), "https://minecraft.wiki/w/Formatting_codes" ),
                            label( Text.translatable("text.chatpatches.help.faq"), "https://github.com/mrbuilder1961/ChatPatches#faq" ),
                            label( Text.translatable("text.chatpatches.help.regex"), "https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html"),
                            label( Text.translatable("text.chatpatches.help.regexTester"), "https://regex101.com/" )
                        )
                    )
                )
                .save(Config::write);

        // debug options
        if(FabricLoader.getInstance().isDevelopmentEnvironment()) {
            builder.category(
                category(
                    "debug",
                    ObjectList.of(
                        ButtonOption.createBuilder()
                            .name( Text.literal("Print GitHub Option table") )
                            .action((screen, option) -> {
                                StringBuilder str = new StringBuilder();

                                config.getOptions().forEach(opt ->
                                    str.append("\n| %s | %s | %s | `text.chatpatches.%s` |".formatted(
                                        I18n.translate("text.chatpatches." + opt.key),

                                        ( opt.getType().equals(Integer.class) && opt.key.contains("Color") )
                                            ? "`0x%06x`".formatted( (int)opt.def )
                                            : (opt.getType().equals(String.class))
                                                ? "`\"" + opt.def + "\"`"
                                                : "`" + opt.def + "`",

                                        I18n.translate("text.chatpatches.desc." + opt.key),
                                        opt.key
                                    ))
                                );

                                ChatPatches.LOGGER.warn("[YACLConfig.printGithubTables]" + str);
                            })
                            .build()
                    )
                )
            );
        }

        return builder.build().generateScreen(parent);
    }


    @SuppressWarnings("unchecked")
    private static <T> ControllerBuilder<T> getController(Option<T> opt, String key) {
        if( key.matches("^.*(?:Str|Date|Format)$") ) // endsWith "Str" "Date" or "Format"
            return (ControllerBuilder<T>) StringControllerBuilder.create( (Option<String>)opt );

        else if( key.contains("Color") )
            return (ControllerBuilder<T>) ColorControllerBuilder.create( (Option<Color>)opt );

        else if( config.getOption(key).get() instanceof Integer ) // key is int but not color
            return (ControllerBuilder<T>) IntegerSliderControllerBuilder.create( (Option<Integer>)opt )
                .range( getMinOrMax(key, true), getMinOrMax(key, false) )
                .step( getInterval(key) );

        else
            return (ControllerBuilder<T>) BooleanControllerBuilder.create( (Option<Boolean>)opt ).coloured(true);
    }

    private static BiConsumer<YACLScreen, ButtonOption> getAction(String key) {
        return (screen, option) -> {
            if(key.contains("Clear")) {
                if(!key.contains("History"))
                    ChatLog.clearMessages(); // if key is "ClearMessages" or "Clear"
                if(!key.contains("Messages"))
                    ChatLog.clearHistory(); // if key is "ClearHistory" or "Clear"
            } else if(key.equals("chatlogLoad")) {
                ChatLog.deserialize();
                ChatLog.restore();
            } else if(key.equals("chatlogSave")) {
                ChatLog.serialize();
            } else if(key.equals("chatlogBackup")) {
                ChatLog.backup();
            } else if(key.equals("chatlogOpenFolder")) {
                Util.getOperatingSystem().open(ChatLog.PATH.getParent().toFile());
            } else if(key.equals("help.reloadConfig")) {
                read();
                write();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> Binding<T> getBinding(Setting<?> option) {
        Setting<T> o = (Setting<T>) option;

        if( o.key.contains("Date") )
            // must be able to successfully create a SimpleDateFormat
            return Binding.generic(o.def, o::get, inc -> {
                try {
                    new SimpleDateFormat( inc.toString() );
                    o.set( inc );
                } catch(IllegalArgumentException e) {
                    ChatPatches.LOGGER.error("[YACLConfig.getBinding] Invalid date format '{}' provided for '{}'", inc, o.key);
                }
            });

        else if( o.key.contains("Format") )
            // must contain '$'
            return Binding.generic(o.def, o::get, inc -> {
                if(inc.toString().contains("$"))
                    o.set(inc);
            });

        else
            // every other setting either has no requirements or is already constrained with its controller
            // this applies to all options containing 'Str' and all boolean, int, and color options.
            // color options have type transformers to int overridden in the screen builder
            return Binding.generic(o.def, o::get, o::set);
    }

    /**
     * Returns the appropriate minimum or maximum value for the given key.
     * Used for upholding the disorganized yet clean look to this class.
     */
    private static int getMinOrMax(String key, boolean min) {
        if(min) {
            return switch(key) {
                case "counterCompactDistance" -> -1;
                case "chatWidth", "chatMaxMessages", "shiftChat", "chatlogSaveInterval" -> 0;
                default -> {
                    ChatPatches.logReportMsg(new IllegalArgumentException("No minimum value specified for option '" + key + "'"));
                    yield 0;
                }
            };
        } else {
            return switch(key) {
                case "chatMaxMessages" -> Short.MAX_VALUE;
                case "chatWidth" -> mc.getWindow().getScaledWidth() - 12; // offset length calc'd from ChatHud#render aka magic #
                // only issue w ^^^ is if the window is resized while the config screen is open the max value will be incorrect
                case "chatlogSaveInterval" -> 180;
                case "counterCompactDistance" -> mc.inGameHud.getChatHud() instanceof ChatHud chatHud ? chatHud.getVisibleLineCount() : 50;
                case "shiftChat" -> 100;
                default -> {
                    ChatPatches.logReportMsg(new IllegalArgumentException("No maximum value specified for option '" + key + "'"));
                    yield 100;
                }
            };
        }
    }

    /** Returns the appropriate interval for the given key. */
    @SuppressWarnings("SwitchStatementWithTooFewBranches")
	private static int getInterval(String key) {
        return switch(key) {
            case "chatMaxMessages" -> 16;
            default -> 1;
        };
    }


    /** Note: puts groups before ungrouped options */
    private static ConfigCategory category(String key, ObjectList<Option<?>> options, OptionGroup... groups) {
        ConfigCategory.Builder builder = ConfigCategory.createBuilder()
            .name( Text.translatable("text.chatpatches.category." + key) );

        if( I18n.hasTranslation("text.chatpatches.category.desc." + key) )
            builder.tooltip( Text.translatable("text.chatpatches.category.desc." + key) );
        if( groups.length > 0 )
            builder.groups( List.of(groups) );
        if( !options.isEmpty() )
            builder.options( options );

        return builder.build();
    }

    private static OptionGroup group(String key, ObjectList<Option<?>> options, Style descriptionStyle) {
        return OptionGroup.createBuilder()
            .name( Text.translatable("text.chatpatches.category." + key) )
            .description(OptionDescription.of(
                Text.translatable("text.chatpatches.category.desc." + key).fillStyle(descriptionStyle != null ? descriptionStyle : Style.EMPTY)
            ))
            .options( options )
            .build();
    }

    /*@SuppressWarnings("unchecked") // currently being difficult, needs to be modularly added to the controller but sometimes it isn't a VFC, even still there are issues
    private static ValueFormatter<?> getValueFormatter(String key) {
        Class<?> type = config.getOption(key).getType();
        if(type == Integer.class) {
            return switch(key) {
                //for all the values like -1 or 0 for number guys
                case "chatlogSaveInterval" -> (val -> Text.of("" + Formatting.GREEN + val + "§f ticks"));
                case "chatWidth", "chatHeight", "chatShift" -> (val -> Text.of("" + Formatting.GREEN + val + "§f pixels"));
                case "chatMaxMessages", "counterCompactDistance" -> (val -> Text.of("" + Formatting.GREEN + val + "§f messages"));
                default -> (ValueFormatter<Integer>) IntegerSliderController.DEFAULT_FORMATTER;
            };
        } else if(type == Boolean.class) {
            return switch(key) {
                case "chatHidePacket", "hideSearchButton" -> (ValueFormatter<Boolean>) BooleanController.YES_NO_FORMATTER;
                //case "tf" -> (ValueFormatter<Boolean>) BooleanController.TRUE_FALSE_FORMATTER;
                default -> (ValueFormatter<Boolean>) BooleanController.ON_OFF_FORMATTER;
            };
        }
        return value -> Text.of(value.toString());
    }*/

    private static OptionDescription desc(Setting<?> opt) {
        OptionDescription.Builder builder = OptionDescription.createBuilder().text( Text.translatable("text.chatpatches.desc." + opt.key) );

        String ext = "webp";
        // using Locale.ROOT fixes turkish locale causing file mismatch
        String image = "textures/preview/" + opt.key.replaceAll("([A-Z])", "_$1").toLowerCase(Locale.ROOT) + "." + ext;
        Identifier id = ChatPatches.id(image);

        try {
            if( mc.getResourceManager().getResource(id).isPresent() )
                builder.webpImage(id);
            else
                ChatPatches.LOGGER.debug("[YACLConfig.desc] Couldn't find '{}'", image);
        } catch(Throwable e) {
            ChatPatches.LOGGER.error("[YACLConfig.desc] An error occurred while trying to use '{}:{}' :", ChatPatches.MOD_ID, image, e);
        }

        return builder.build();
    }

    private static Option<Text> label(MutableText labelText, String urlTooltip) {
        return LabelOption.create( labelText.styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, urlTooltip))) );
    }

    private static ButtonOption action(String key, Object... args) {
        Object o = new Object();
        return ButtonOption.createBuilder()
            .name(Text.translatable( "text.chatpatches." + key, (args[0].equals(-1) ? new Object[0] : args) )) // args or nothing
            .description(desc( new Setting<>(o, o, key) ))
            .action(getAction(key))
            .build();
    }
}