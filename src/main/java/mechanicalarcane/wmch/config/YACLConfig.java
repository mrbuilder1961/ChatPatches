package mechanicalarcane.wmch.config;

import dev.isxander.yacl.api.*;
import dev.isxander.yacl.gui.controllers.ActionController;
import dev.isxander.yacl.gui.controllers.BooleanController;
import dev.isxander.yacl.gui.controllers.ColorController;
import dev.isxander.yacl.gui.controllers.LabelController;
import dev.isxander.yacl.gui.controllers.slider.IntegerSliderController;
import dev.isxander.yacl.gui.controllers.string.StringController;
import mechanicalarcane.wmch.WMCH;
import mechanicalarcane.wmch.util.Util;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
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
        List<dev.isxander.yacl.api.Option<?>> timeOpts = Lists.newArrayList();
        List<dev.isxander.yacl.api.Option<?>> hoverOpts = Lists.newArrayList();
        List<dev.isxander.yacl.api.Option<?>> counterOpts = Lists.newArrayList();
        List<dev.isxander.yacl.api.Option<?>> boundaryOpts = Lists.newArrayList();
        List<dev.isxander.yacl.api.Option<?>> hudOpts = Lists.newArrayList();

        Config.getOptions().forEach(opt -> {
            String cat = opt.key.split("[A-Z]")[0];
            if( !I18n.hasTranslation("text.wmch.category." + cat) )
                cat = "hud";

            if(opt.key.contains("Color"))
                opt = new Option<>(new Color( (int)opt.get() ), new Color( (int)opt.def ), opt.key) {
                    @Override
                    public Color get() {
                        return new Color( (int)Config.getOption(key).get() );
                    }

                    @Override
                    public void set(Object value) {
                        super.set( ((Color)value).getRGB() - 0xff000000 );
                    }
                };


            dev.isxander.yacl.api.Option<?> yaclOpt =
                dev.isxander.yacl.api.Option.createBuilder( opt.getType() )
                    .name( Text.translatable("text.wmch." + opt.key) )
                    .tooltip( Text.translatable("text.wmch.desc." + opt.key) )
                    .controller( getController(opt.key) )
                    .binding( getBinding(opt) )
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
            .title(Text.translatable("text.wmch.title"))
                .category( category("time", timeOpts) )
                .category( category("hover", hoverOpts) )
                .category( category("counter", counterOpts) )
                .category( category("boundary", boundaryOpts) )
                .category( category("hud", hudOpts) )

                .category(
                    category(
                    "help",
                        List.of(
                            label( Text.translatable("text.wmch.dateFormat"), null, "https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html" ),
                            label( Text.translatable("text.wmch.formatCodes"), null, "https://minecraft.gamepedia.com/Formatting_codes" ),
                            label( Text.literal("README -> FAQ"), null, "https://github.com/mrbuilder1961/WheresMyChatHistory#faq" )
                        )
                    )
                )
                .save(() -> {
                    write();
                    WMCH.LOGGER.info("[YACLConfig.save] Updated the config file at '{}'!", CONFIG_PATH);
                });

        // debug options
        if(WMCH.FABRICLOADER.isDevelopmentEnvironment()) {
            builder.category(
                category(
                    "debug",
                    List.of(
                        dev.isxander.yacl.api.Option.createBuilder(Integer.class)
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
                                    str.append("\n| %s | %s | %s | `text.wmch.%s` |".formatted(
                                        I18n.translate("text.wmch." + opt.key),
                                        opt.get().getClass().equals( Integer.class ) && opt.key.contains("Color")
                                            ? "`0x" + Integer.toHexString( (int)opt.def ).toUpperCase() + "` (`" + opt.def + "`)"
                                            : "`" + opt.def + "`",
                                        I18n.translate("text.wmch.desc." + opt.key),
                                        opt.key
                                    ))
                                );

                                WMCH.LOGGER.warn("[YACLConfig.printGithubTables]" + str);
                            })
                            .build()
                    )
                )
            );
        }

        return builder.build().generateScreen(parent);
    }


    @SuppressWarnings("unchecked")
    private static <T> Function<dev.isxander.yacl.api.Option<T>, Controller<T>> getController(String key) {

        if( key.matches("^.*(?:Str|Date|Format)$") ) // endsWith "Str" "Date" or "Format"
            return opt -> (Controller<T>) new StringController((dev.isxander.yacl.api.Option<String>) opt);

        else if( key.contains("Color") )
            return opt -> (Controller<T>) new ColorController((dev.isxander.yacl.api.Option<Color>) opt);

        else if( key.equals("shiftChat") || key.equals("maxMsgs") )
            return opt -> (Controller<T>) new IntegerSliderController(
                (dev.isxander.yacl.api.Option<Integer>) opt,
                0,
                key.equals("shiftChat")
                    ? (MinecraftClient.getInstance().getWindow().getY() / 2)
                    : Short.MAX_VALUE,
                key.equals("shiftChat")
                    ? 1
                    : 16
            );
        else
            return opt -> (Controller<T>) new BooleanController((dev.isxander.yacl.api.Option<Boolean>) opt, true);
    }

    @SuppressWarnings("unchecked")
    private static <T> Binding<T> getBinding(Option<?> option) {
        Option<T> o = (Option<T>) option;

        if( o.key.contains("Date") )
            // must be able to successfully create a SimpleDateFormat
            return Binding.generic(o.def, o::get, inc -> {
                try {
                    new SimpleDateFormat( inc.toString() );
                    o.set( inc );
                } catch (IllegalArgumentException e) {
                    WMCH.LOGGER.error("[YACLConfig.getBinding] Invalid date format '{}' provided for '{}'", inc, o.key);
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

    private static ConfigCategory category(String key, List<dev.isxander.yacl.api.Option<?>> options) {
        return ConfigCategory.createBuilder()
            .name( Text.translatable("text.wmch.category." + key) )
            .options( options )
            .build();
    }

    private static dev.isxander.yacl.api.Option<Text> label(MutableText display, String tooltip, String url) {
        return dev.isxander.yacl.api.Option.createBuilder(Text.class)
            .name(display)// delete this?
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
