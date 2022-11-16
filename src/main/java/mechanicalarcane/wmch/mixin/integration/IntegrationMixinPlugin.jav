package mechanicalarcane.wmch.mixin.integration;

import static mechanicalarcane.wmch.WMCH.LOGGER;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import mechanicalarcane.wmch.WMCH;
import mechanicalarcane.wmch.config.Option;
import net.fabricmc.loader.api.ModContainer;

/**
 * The Mixin plugin for allowing
 * compatibility with some other
 * mods that also inject into the
 * same Minecraft classes as this
 * one; configurable.
 *
 * <p>In a nutshell, this class will
 * check loaded mods against a list
 * of partially-breaking mods, and
 * deactivate some mixins to allow
 * compatibility. Unfortunately, this
 * will disable/limit specific
 * features, which will have a
 * generated tooltip explaining
 * what mods disabled what.
 *
 * <p>If no breaking mods are installed,
 * all compat mixins will load as
 * normal, completely enabling the mod.
 *
 * <p>Due to the way this has to be
 * implemented (because of Mixin
 * restrictions), many of the old
 * Mixins have been split into
 * separate files to work properly.
 *
 * <p>Template class from
 * https://github.com/enjarai/shared-resources/blob/master/src/main/java/nl/enjarai/shared_resources/common/compat/CompatMixinPlugin.java
 */
public class IntegrationMixinPlugin implements IMixinConfigPlugin {
    public static final List<String> increaseMaxMessages = List.of("essential-loader", "essential-container");

    public static boolean essential = false;


    @Override
    public void onLoad(String mixinPackage) {
        for(ModContainer installed : WMCH.FABRICLOADER.getAllMods()) {
            essential |= increaseMaxMessages.stream().anyMatch( modId -> installed.getMetadata().getId().equals(modId) );

            //mod |/&= mixinMethodName.stream().any/allMatch( regexId -> Pattern.matches(regexId, mod.getMetadata().getId()) )
        };
    }

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        if(essential) {
            List<Option<?>> disabled = Option.OPTIONS.stream().filter(opt -> opt.getBreakingMods().equals( increaseMaxMessages )).toList();
            disabled.forEach(opt -> opt.disable());

            LOGGER.warn("[IntegrationMixinPlugin] essential is installed, disabling {} with {}.", MaxMessagesMixin.class.getSimpleName(), disabled.stream().map(opt -> opt.getKey()).toList());

            if( target.endsWith("ChatHud") && (MaxMessagesMixin.class.getName().equals(mixin)) )
                return false;
        }

        return true; // defaults to loading all
    }


    @Override
    public String getRefMapperConfig() { return null; }
    @Override
    public List<String> getMixins() { return null; }

    @Override
    public void preApply(String targetClass, ClassNode targetClassNode, String mixinClass, IMixinInfo mixinInfo) {}
    @Override
    public void postApply(String targetClass, ClassNode targetClassNode, String mixinClass, IMixinInfo mixinInfo) {}
    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
}