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


            dev.isxander.yacl.api.Option<?> yaclOpt;
            if(opt.key.contains("Color"))
                yaclOpt = color(cat);

            else yaclOpt =
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

        return YetAnotherConfigLib.createBuilder()
            .title(Text.translatable("text.wmch.title"))
                .category(category("time")
                    .options(timeOpts)
                    .build()
                )
                .category(category("hover")
                    .options(hoverOpts)
                    .build()
                )
                .category(category("counter")
                    .options(counterOpts)
                    .build()
                )
                .category(category("boundary")
                    .options(boundaryOpts)
                    .build()
                )
                .category(category("hud")
                    .options(hudOpts)
                    .build()
                )
                .category(category("help")//HELP/INFO
                    // hardcoded text labels
                    .option(dev.isxander.yacl.api.Option.createBuilder(Text.class)
                        .name(Text.empty())//del
                        .tooltip( Text.literal("§9https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html") )
                        .controller(LabelController::new)
                        .binding(Binding.immutable(
                            Text.translatable("text.wmch.dateFormat")
                                .fillStyle( Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html")) )
                        ))
                        .build()
                    )
                    .option(dev.isxander.yacl.api.Option.createBuilder(Text.class)
                        .name(Text.empty())//del
                        .tooltip( Text.literal("§9https://minecraft.gamepedia.com/Formatting_codes") )
                        .controller(LabelController::new)
                        .binding(Binding.immutable(
                            Text.translatable("text.wmch.formatCodes")
                                .fillStyle( Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://minecraft.gamepedia.com/Formatting_codes")) )
                        ))
                        .build()
                    )
                    .build()
                )
                .category(category("debug")
                    .option(dev.isxander.yacl.api.Option.createBuilder(Integer.class)
                        .available(WMCH.FABRICLOADER.isDevelopmentEnvironment())
                        .name( Text.literal("Edit Flags") )
                        .controller(opt -> new IntegerSliderController(opt, 0, 0b1111, 1))
                        .binding( Util.Flags.flags, () -> Util.Flags.flags, inc -> Util.Flags.flags = inc )
                        .build()
                    )
                    .option(ButtonOption.createBuilder()
                        .available(WMCH.FABRICLOADER.isDevelopmentEnvironment())
                        .name( Text.literal("Print GitHub Option table") )
                        .controller(ActionController::new)
                        .action((yaclScreen, buttonOption) -> {
                            StringBuilder str = new StringBuilder();

                            Config.getOptions().forEach(opt -> {
                                str.append("\n| %s | %s | %s | `text.wmch.%s` |".formatted(
                                    I18n.translate("text.wmch." + opt.key),
                                    opt.get().getClass().equals( Integer.class ) && opt.key.contains("Color")
                                        ? "`0x" + Integer.toHexString( (int)opt.def ).toUpperCase() + "` (`" + opt.def + "`)"
                                        : "`" + opt.def + "`",
                                    I18n.translate("text.wmch.desc." + opt.key),
                                    opt.key
                                ));
                            });

                            WMCH.LOGGER.warn("[ClothConfig.printGithubTables]" + str);
                        })
                        .build()
                    )
                    .build()
                )
            .save(() -> {
                write();
                WMCH.LOGGER.info("[YACLConfig.save] Updated the config file at '{}'!", CONFIG_PATH);
            })
            .build()
        .generateScreen(parent);
    }


    @SuppressWarnings("unchecked")
    private static <T> Function<dev.isxander.yacl.api.Option<T>, Controller<T>> getController(String key) {

        if( key.contains("Date") || key.contains("Format") || key.contains("Str") )
            return opt -> (Controller<T>) new StringController((dev.isxander.yacl.api.Option<String>) opt);

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

    private static <T> Binding<T> getBinding(Option<?> option) {
        @SuppressWarnings("unchecked") Option<T> o = (Option<T>) option;

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
            // this applies to all settings not containing 'Date' or 'Format'
            // (options containing 'Str' and all boolean, int, and color options)
            return Binding.generic(o.def, o::get, o::set);
    }

    private static ConfigCategory.Builder category(String key) {
        return ConfigCategory.createBuilder()
            .name( Text.translatable("text.wmch.category." + key) );
    }

    private static dev.isxander.yacl.api.Option<Color> color(String cat) {
        Option<Integer> opt = getOption(cat + "Color");

        return dev.isxander.yacl.api.Option.createBuilder(Color.class)
            .name( Text.translatable("text.wmch." + cat + "Color") )
            .tooltip( Text.translatable("text.wmch.desc." + cat + "Color") )
            .controller(ColorController::new)
            .binding(Binding.generic( new Color(opt.def), () -> new Color(opt.get()), inc -> opt.set( inc.getRGB() ) ))
            .build();
    }
}