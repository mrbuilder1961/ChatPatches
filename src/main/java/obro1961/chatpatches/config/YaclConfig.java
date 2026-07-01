//~ yarnification
package obro1961.chatpatches.config;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.*;
import dev.isxander.yacl3.gui.YACLScreen;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.UUIDUtil;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
//? if >1.20.1 {
import net.minecraft.util.NullOps;
//?} else {
//import com.mojang.serialization.JsonOps;
//?}
import net.minecraft.util.Util;
import obro1961.chatpatches.ChatLog;
import obro1961.chatpatches.ChatPatches;
import obro1961.chatpatches.util.RenderUtil;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static obro1961.chatpatches.ChatPatches.LOGGER;
import static obro1961.chatpatches.ChatPatches.config;

/**
 * @apiNote This is the second edition of a config menu using external
 * libraries; the first was with Cloth Config.
 */
public class YaclConfig extends Config {
    public static final String LANG_PREFIX = "text.chatpatches.";
    public static final String DESCRIPTION_KEY = "desc.";
    public static final String DESCRIPTION_PREFIX = LANG_PREFIX + DESCRIPTION_KEY;
    public static final String CATEGORY_PREFIX = LANG_PREFIX + "category.";
    public static final String CATEGORY_DESC_PREFIX = CATEGORY_PREFIX + DESCRIPTION_KEY;
    public static final String HELP_PREFIX = LANG_PREFIX + "help.";
    public static final String SEARCH_PREFIX = LANG_PREFIX + "search.";

	protected static Language I18n; // lazily initialized to ensure proper registration

    /**
     * Matches if the compared string ends with {@code Str}, {@code Date}, or {@code Format}.
     */
    private static final Matcher STRING_DATE_FORMAT_OPTION = Pattern.compile(".*(?:Str|Date|Format)$").matcher("");


    @Override
    public Screen getConfigScreen(Screen parent) {
		if(I18n == null) I18n = Language.getInstance();

        ObjectList<Option<?>> timeOpts = new ObjectArrayList<>(),
                        hoverOpts = new ObjectArrayList<>(),
                        counterOpts = new ObjectArrayList<>(),
                        compactOpts = new ObjectArrayList<>(),
                        boundaryOpts = new ObjectArrayList<>(),
                        chatlogOpts = new ObjectArrayList<>(),
                        chatlogActions = new ObjectArrayList<>(),
                        chatOpts = new ObjectArrayList<>(),
                        nameOpts = new ObjectArrayList<>(),
                        contextMenuOpts = new ObjectArrayList<>(),
                        searchOpts = new ObjectArrayList<>(),
                        helpOpts = new ObjectArrayList<>();

        config.getOptions().forEach(opt -> {
            String key = opt.key; // effectively final
            String cat = key.split("[A-Z]")[0];

            if(I18n.has(SEARCH_PREFIX + key)) {
				cat = "_"; // chat search filters are configurable in the chat screen, not here, where they won't render nicely
			} else if(key.equals("logMessageStructures")) {
				cat = "help";
			} else if(!I18n.has(CATEGORY_PREFIX + cat)) {
				cat = "chat"; // default to chat if the category is invalid
			}

			// yacl only supports java.awt.Color so we have to make a manual adapter here via Setting -_-
            if(key.endsWith("Color")) {
                opt = new Setting<>(new Color( (int)opt.val ), new Color( (int)opt.def ), key) {
                    @Override
                    public Color get() {
                        return new Color( (int)getOption(key).get() );
                    }

                    @Override
                    public void set(Object value) {
						super.set(RenderUtil.smartOpaque( ((Color)value).getRGB() ));
                    }
                };
            }

            Option<?> yaclOpt =
                Option.createBuilder()
                    .name(Component.translatable(LANG_PREFIX + key))
                    .description(desc(opt))
                    .controller(me -> getController(me, key))
                    .binding(getBinding(opt))
                    .flag(
                        // todo: tryCondenseDupes doesn't do anything here bc modifyMessage doesn't run on refresh=true. to get around this we'd
                        //  need to like make a whole new method or something that only updates the message components on refresh, which is plausible
                        //  but is not an effortless change. (ex. take timestamp and regenerate time text, take player regen name, etc) not on chatlog#restore
                        cat.equals("counter") || cat.equals("compact")
                            ? new OptionFlag[] { client -> client.gui.hud.getChat().rescaleChat() }
                            : new OptionFlag[0]
                    )
                    /*? if >=1.21.9 {*/
                    .available( !key.equals("onlyInvasiveDrafting") ) /*?}*/
                    .build();


            switch(cat) {
				case "_" -> {}

                case "time" -> timeOpts.add(yaclOpt);
                case "hover" -> hoverOpts.add(yaclOpt);
                case "counter" -> counterOpts.add(yaclOpt);
                case "compact" -> compactOpts.add(yaclOpt);
                case "boundary" -> boundaryOpts.add(yaclOpt);

                case "chatlog" -> chatlogOpts.add(yaclOpt);

                case "name" -> nameOpts.add(yaclOpt);
                case "context" -> contextMenuOpts.add(yaclOpt);
                case "search" -> searchOpts.add(yaclOpt);
                case "help" -> helpOpts.add(yaclOpt);
                default -> chatOpts.add(yaclOpt);
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


        YetAnotherConfigLib.Builder builder = YetAnotherConfigLib.createBuilder().title(Component.translatable(LANG_PREFIX + "title"))
            .category( tabCat("message", ObjectList.of(),
                subGroup("time", timeOpts, null),
                subGroup("hover", hoverOpts, null),
                subGroup("counter", counterOpts, null),
                subGroup("compact", compactOpts,
					Style.EMPTY.withClickEvent(new /*$ open_url '"https://modrinth.com/mod/compact-chat"' {*/ClickEvent.OpenUrl(URI.create("https://modrinth.com/mod/compact-chat"))/*$}*/)
				)
            ))
            .category( tabCat("boundary", boundaryOpts) )
            .category( tabCat("chatlog", chatlogOpts,
                subGroup("chatlog.actions", chatlogActions, null)
            ))
            .category( tabCat("chat", chatOpts,
                subGroup("name", nameOpts, null),
                subGroup("context", contextMenuOpts, null),
                subGroup("search", searchOpts, null)
            ))

            .category(
                tabCat(
                "help",
                    ObjectList.of(
                        action("help.reloadConfig", -1),
                        helpOpts.getFirst(),
                        label( Component.translatable(HELP_PREFIX + "dateFormat"), "https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html" ),
                        label( Component.translatable(HELP_PREFIX + "formatCodes"), "https://minecraft.wiki/w/Formatting_codes" ),
                        label( Component.translatable(HELP_PREFIX + "faq"), "https://github.com/mrbuilder1961/ChatPatches#faq" ),
                        label( Component.translatable(HELP_PREFIX + "regex"), "https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html"),
                        label( Component.translatable(HELP_PREFIX + "regexTester"), "https://regex101.com/" )
                    )
                )
            )
            .save(Config::serialize);

        // region [Debug options]
        if(FabricLoader.getInstance().isDevelopmentEnvironment()) {
            builder.category(
                tabCat(
                    "debug",
                    ObjectList.of(
                        ButtonOption.createBuilder()
                            .name(Component.nullToEmpty("Convert id arrays to strings"))
                            .action((screen, option) -> Arrays.stream(
								FabricLoader.getInstance().getGameDir()
								.resolve("logs")
								.toFile()
								.listFiles(f -> f.getName().endsWith(".json")) // get all log files
							)
								.sorted(Comparator.comparingLong(File::lastModified)) // most recent
								.skip(1) // current chat log is always the most recent
								.findFirst() // most recent
								.ifPresentOrElse(
									f -> {
										// all the "\\s*" substrings allow matching prettified chat logs
										// without whitespace matches: `"id":[(-?\\d+),(-?\\d+),(-?\\d+),(-?\\d+)]`
										Pattern regex = Pattern.compile("\"id\"\\s*:\\s*\\[\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*]");
										try {
											int n = 0;
											String content = Files.readString(f.toPath());
											Matcher m = regex.matcher(content);

											while(m.find()) {
												n++;
												// map each of the 4 groups to an int, then to a uuid array
												int[] bits = Stream.of(m.group(1), m.group(2), m.group(3), m.group(4)).mapToInt(Integer::parseInt).toArray();

												// actually replace the dashed array with the dashed uuid
												content = content.replace( m.group(), "\"id\":\"" + UUIDUtil.uuidFromIntArray(bits) + "\"" );
											}
											Files.writeString(f.toPath(), content);

											LOGGER.info("Reverted {} id arrays in '{}'", n, f.getAbsolutePath());
										} catch(IOException e) {
											LOGGER.warn("An error occurred reading '{}'.. good luck with this guy:", f.getAbsolutePath(), e);
										}
									},
									() -> LOGGER.warn("No log files found")
								))
                            .build()
                    )
                )
            );
        }
		// endregion

        return builder.build().generateScreen(parent);
    }


    @SuppressWarnings("unchecked")
    private static <T> ControllerBuilder<T> getController(Option<T> opt, String key) {
        ControllerBuilder<?> builder;

        if( STRING_DATE_FORMAT_OPTION.reset(key).matches() ) {
            builder = StringControllerBuilder.create((Option<String>) opt);
        } else if( key.contains("Color") ) {
            builder = ColorControllerBuilder.create((Option<Color>) opt);
        } else if( config.getOption(key).get() instanceof Integer ) { // key is int but not color
            builder = IntegerSliderControllerBuilder.create((Option<Integer>) opt).range(getMin(key), getMax(key)).step(getInterval(key));
        } else {
            builder = BooleanControllerBuilder.create((Option<Boolean>) opt).coloured(true);
        }

        return (ControllerBuilder<T>) builder;
    }

    private static BiConsumer<YACLScreen, ButtonOption> getAction(String key) {
        return (screen, option) -> {
            if(key.contains("Clear")) {
                if(key.endsWith("History")) {
					ChatLog.clearHistory(); // if key is "ClearHistory" or "Clear"
				}
                // no else, regular clear should do BOTH
                if(key.endsWith("Messages")) {
					ChatLog.clearMessages(); // if key is "ClearMessages" or "Clear"
				}
            } else {
                switch(key) {
                    case "chatlogLoad" -> ChatLog.load(true); // queues the deserialization and restoration tasks together
                    case "chatlogSave" -> ChatLog.serialize();
                    case "chatlogBackup" -> ChatLog.backup();
                    case "chatlogOpenFolder" -> Util.getPlatform().openFile(ChatLog.PATH.getParent().toFile());
                    case "help.reloadConfig" -> deserialize();
                }
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> Binding<T> getBinding(Setting<?> option) {
        Setting<T> o = (Setting<T>) option;

		// because yacl only uses java.awt.Color and doesn't let you transform it, it's much easier to simply
		// carve out an exception than bulk up the codec and risk future headaches / undesired side effects
		if(o.key.contains("Color")) {
			// note that the setter is the only difference - it doesn't have to validate anything,
			// only convert - which is handled in the anonymous Setting class in getConfigScreen()
			return Binding.generic(o.def, o::get, o::set);
		}

		// because all parsing is handled in Config.Setting#getTypeCodec(), we just
		// need to ensure that encoding the incoming value against it is successful!
		return Binding.generic(
			o.def,
			o::get,
			val -> {
				// NullOps is perfect: it fails when there are errors, and it always returns Unit on success!
				var result = o.getTypeCodec()
					.codec()
					.encodeStart(/*? if >1.20.1 {*/NullOps/*?} else {*//*JsonOps*//*?}*/.INSTANCE, val);

				// take a wild fuckin' guess to which version doesn't have ifSuccess().
				if(result./*? if >1.20.1 {*/isSuccess/*?} else {*//*result().isPresent*//*?}*/()) {
					o.set(val);
					// toast if we should
				} else {
					// FIXME DOESNT prevent 'value mismatch after applying!' log spam // i think this is because its expecting opt.value == val - but obv thats not true
					//noinspection OptionalGetWithoutIsPresent: what else could there be?
					LOGGER.warn("'{}' constraints ignored, discarding value - {}", o.key, result.error().get().message());
					// toast if we should
				}
			}
		);
    }

	private static int getMin(String key) {
		try {
			return (Object)config.getRange(key) instanceof IntConstraints constraints
				? constraints.min()
				: (int) IntConstraints.class.getDeclaredMethod("min").getDefaultValue();
		} catch(NoSuchMethodException e) {
			ChatPatches.logReportMsg(e);
			return 0;
		}
	}

	private static int getMax(String key) {
		try {
			return (Object)config.getRange(key) instanceof IntConstraints constraints
				? constraints.max()
				: (int) IntConstraints.class.getDeclaredMethod("max").getDefaultValue();
		} catch(NoSuchMethodException e) {
			ChatPatches.logReportMsg(e);
			return Integer.MAX_VALUE;
		}
	}

    private static int getInterval(String key) {
		try {
			return (Object)config.getRange(key) instanceof IntConstraints constraints
				? constraints.interval()
				: (int) IntConstraints.class.getDeclaredMethod("interval").getDefaultValue();
		} catch(NoSuchMethodException e) {
			ChatPatches.logReportMsg(e);
			return 1;
		}
	}


    /**
     * Creates a tab-category with the passed parameters.
     *
     * @apiNote Puts groups before ungrouped options
     */
    private static ConfigCategory tabCat(String key, ObjectList<Option<?>> options, OptionGroup... groups) {
        ConfigCategory.Builder builder = ConfigCategory.createBuilder().name( Component.translatable(CATEGORY_PREFIX + key) );

        Component tooltip = Component.translatable(CATEGORY_DESC_PREFIX + key);
        // use the tooltip if it translated properly
        if( !tooltip.getString().equals(CATEGORY_DESC_PREFIX + key) ) {
			builder.tooltip(tooltip);
		}
        if( groups.length > 0 ) {
			builder.groups( List.of(groups) );
		}
        if( !options.isEmpty() ) {
			builder.options( options );
		}

        return builder.build();
    }

    /**
     * Creates a subgroup (inside a tab-category) with
     * the passed parameters.
     */
    private static OptionGroup subGroup(String key, ObjectList<Option<?>> options, Style descStyle) {
        MutableComponent desc = Component.translatable(CATEGORY_DESC_PREFIX + key);
        return OptionGroup.createBuilder()
            .name( Component.translatable(CATEGORY_PREFIX + key) )
            .description(
                // does this subgroup actually have a description?
                desc.getString().equals(CATEGORY_DESC_PREFIX + key)
                    ? OptionDescription.EMPTY // if no don't use one
                    : OptionDescription.of(desc.withStyle(descStyle != null ? descStyle : Style.EMPTY))
            )
            .options( options )
            .build();
    }

    private static OptionDescription desc(Setting<?> opt) {
        OptionDescription.Builder builder = OptionDescription.createBuilder().text( Component.translatable(DESCRIPTION_PREFIX + opt.key) );

        // using Locale.ROOT fixes turkish locale causing file mismatch (https://discord.com/channels/1077285607375638529/1260175475708399616)
        String image = "textures/preview/" + opt.key.replaceAll("([A-Z])", "_$1").toLowerCase(Locale.ROOT) + ".webp";
        Identifier id = ChatPatches.id(image);

        try {
            if(mc().getResourceManager().getResource(id).isPresent()) {
                builder.webpImage(id);
            }
        } catch(Throwable e) {
            LOGGER.error("An error occurred while trying to use '{}:{}' :", ChatPatches.MOD_ID, image, e);
        }

        return builder.build();
    }

    private static Option<Component> label(MutableComponent labelText, String urlTooltip) {
        return LabelOption.create(labelText.withStyle(style -> style.withClickEvent(new /*$ open_url urlTooltip {*/ClickEvent.OpenUrl(URI.create(urlTooltip))/*$}*/) ));
    }

    private static ButtonOption action(String key, Object... args) {
        Object o = new Object();
        return ButtonOption.createBuilder()
            .name(Component.translatable( LANG_PREFIX + key, (args[0].equals(-1) ? new Object[0] : args) )) // args or nothing
            .description(desc( new Setting<>(o, o, key) ))
            .action(getAction(key))
            .build();
    }
}