package obro1961.chatpatches.config;

import dev.isxander.yacl.api.*;
import dev.isxander.yacl.gui.controllers.ActionController;
import dev.isxander.yacl.gui.controllers.BooleanController;
import dev.isxander.yacl.gui.controllers.ColorController;
import dev.isxander.yacl.gui.controllers.LabelController;
import dev.isxander.yacl.gui.controllers.slider.IntegerSliderController;
import dev.isxander.yacl.gui.controllers.string.StringController;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import obro1961.chatpatches.ChatPatches;
import obro1961.chatpatches.util.Util;
import org.apache.commons.compress.utils.Lists;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.function.Function;

/**
 * The YetAnotherConfigLib config class.
 * @see Config
 * @apiNote This is the 2nd edition of a config menu using external libraries.
 */
public class YACLConfig extends Config {

    @Override
    public Screen getConfigScreen(Screen parent) {
        List<Option<?>> timeOpts = Lists.newArrayList();
        List<Option<?>> hoverOpts = Lists.newArrayList();
        List<Option<?>> counterOpts = Lists.newArrayList();
        List<Option<?>> boundaryOpts = Lists.newArrayList();
        List<Option<?>> hudOpts = Lists.newArrayList();

        Config.getOptions().forEach(opt -> {
            String cat = opt.key.split("[A-Z]")[0];
            if( !I18n.hasTranslation("text.chatpatches.category." + cat) )
                cat = "hud";

            if(opt.key.contains("Color"))
                opt = new ConfigOption<>(new Color( (int)opt.get() ), new Color( (int)opt.def ), opt.key) {
                    @Override
                    public Color get() {
                        return new Color( (int)Config.getOption(key).get() );
                    }

                    @Override
                    public void set(Object value) {
                        super.set( ((Color)value).getRGB() - 0xff000000 );
                    }
                };


            Option<?> yaclOpt =
                Option.createBuilder( opt.getType() )
                    .name( Text.translatable("text.chatpatches." + opt.key) )
                    .tooltip( Text.translatable("text.chatpatches.desc." + opt.key) )
                    .controller( getController(opt.key) )
                    .binding( getBinding(opt) )
                    .flag(
                        opt.key.equals("shiftChat") || opt.key.equals("chatWidth")
                            ? new OptionFlag[] { client -> client.inGameHud.getChatHud().reset() }
                            : new OptionFlag[0]
                    )
                    .build();


            switch(cat) {
                case "time" -> timeOpts.add(yaclOpt);
                case "hover" -> hoverOpts.add(yaclOpt);
                case "counter" -> counterOpts.add(yaclOpt);
                case "boundary" -> boundaryOpts.add(yaclOpt);
                case "hud" -> hudOpts.add(yaclOpt);
            }
        });


        YetAnotherConfigLib.Builder builder = YetAnotherConfigLib.createBuilder()
            .title(Text.translatable("text.chatpatches.title"))
                .category( category("time", timeOpts) )
                .category( category("hover", hoverOpts) )
                .category( category("counter", counterOpts) )
                .category( category("boundary", boundaryOpts) )
                .category( category("hud", hudOpts) )

                .category(
                    category(
                    "help",
                        List.of(
                            label( Text.translatable("text.chatpatches.dateFormat"), null, "https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html" ),
                            label( Text.translatable("text.chatpatches.formatCodes"), null, "https://minecraft.gamepedia.com/Formatting_codes" ),
                            label( Text.literal("README -> FAQ"), null, "https://github.com/mrbuilder1961/ChatPatches#faq" )
                        )
                    )
                )
                .save(() -> {
                    write();
                    ChatPatches.LOGGER.info("[YACLConfig.save] Updated the config file at '{}'!", CONFIG_PATH);
                });

        // debug options
        if(ChatPatches.FABRIC_LOADER.isDevelopmentEnvironment()) {
            builder.category(
                category(
                    "debug",
                    List.of(
                        Option.createBuilder(Integer.class)
                            .name( Text.literal("Edit Bit Flags (%d^10, %s^2)".formatted(Util.Flags.flags, Util.Flags.binary())) )
                            .controller(opt -> new IntegerSliderController(opt, 0, 0b1111, 1))
                            .binding( Util.Flags.flags, () -> Util.Flags.flags, inc -> Util.Flags.flags = inc )
                            .build(),

                        ButtonOption.createBuilder()
                            .name( Text.literal("Print GitHub Option table") )
                            .controller(ActionController::new)
                            .action((yaclScreen, buttonOption) -> {
                                StringBuilder str = new StringBuilder();

                                Config.getOptions().forEach(opt ->
                                    str.append("\n| %s | %s | %s | `text.chatpatches.%s` |".formatted(
                                        I18n.translate("text.chatpatches." + opt.key),
                                        opt.get().getClass().equals( Integer.class ) && opt.key.contains("Color")
                                            ? "`0x" + Integer.toHexString( (int)opt.def ).toUpperCase() + "` (`" + opt.def + "`)"
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
    private static <T> Function<Option<T>, Controller<T>> getController(String key) {

        if( key.matches("^.*(?:Str|Date|Format)$") ) // endsWith "Str" "Date" or "Format"
            return opt -> (Controller<T>) new StringController((Option<String>) opt);

        else if( key.contains("Color") )
            return opt -> (Controller<T>) new ColorController((Option<Color>) opt);

        else if( Config.getOption(key).get() instanceof Integer ) // key is int but not color (shiftChat, maxMsgs, or chatWidth)
            return opt -> (Controller<T>) new IntegerSliderController(
                (Option<Integer>) opt,
                0,
                key.equals("maxMsgs")
                    ? Short.MAX_VALUE
                    : key.equals("shiftChat")
                        ? 100
                        : 630, // chatWidth
                key.equals("maxMsgs") ? 16 : 1
            );

        else
            return opt -> (Controller<T>) new BooleanController((Option<Boolean>) opt, true);
    }

    @SuppressWarnings("unchecked")
    private static <T> Binding<T> getBinding(ConfigOption<?> option) {
        ConfigOption<T> o = (ConfigOption<T>) option;

        if( o.key.contains("Date") )
            // must be able to successfully create a SimpleDateFormat
            return Binding.generic(o.def, o::get, inc -> {
                try {
                    new SimpleDateFormat( inc.toString() );
                    o.set( inc );
                } catch (IllegalArgumentException e) {
                    ChatPatches.LOGGER.error("[YACLConfig.getBinding] Invalid date format '{}' provided for '{}'", inc, o.key);
                }
            });

        else if( o.key.contains("Format") )
            // must contain '$'
            return Binding.generic( o.def, o::get, inc -> o.set(inc, inc.toString().contains("$")) );

        else
            // every other setting either has no requirements or is already constrained with its controller
            // this applies to all options containing 'Str' and all boolean, int, and color options.
            // color options have type transformers to int overridden in the screen builder
            return Binding.generic(o.def, o::get, o::set);
    }

    private static ConfigCategory category(String key, List<Option<?>> options) {
        return ConfigCategory.createBuilder()
            .name( Text.translatable("text.chatpatches.category." + key) )
            .options( options )
            .build();
    }

    private static Option<Text> label(MutableText display, String tooltip, String url) {
        return Option.createBuilder(Text.class)
            .name(display)
            .tooltip( Text.literal( tooltip == null ? "§9" + url : tooltip ) )
            .controller(LabelController::new)
            .binding( Binding.immutable(
                url == null
                    ? display
                    : display.fillStyle( Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url)) )
            ))
            .build();
    }
}
