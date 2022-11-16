package mechanicalarcane.wmch.mixin.integration;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import mechanicalarcane.wmch.config.Option;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.hud.ChatHud;


/**
 * A compat mixin for allowing compatibility with Essential
 * Currently unnecessary, as ModifyExpressionValue from MixinExtras
 * can fix the issue in a much cleaner way.
 * @see IntegrationMixinPlugin
 */
@Environment(net.fabricmc.api.EnvType.CLIENT)
@Mixin(ChatHud.class)
public class MaxMessagesMixin {
    /** Increases the max amount of chat messages kept on the hud */
    @ModifyConstant(
        //Lnet/minecraft/client/gui/hud/ChatHud;
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;ILnet/minecraft/client/gui/hud/MessageIndicator;Z)V",
        constant = @Constant(intValue = 100)
    )
    private int increaseMaxMessages(int constant) {
        return Option.MAX_MESSAGES.get();
    }
}
