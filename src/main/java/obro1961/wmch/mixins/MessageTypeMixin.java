package obro1961.wmch.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import net.minecraft.network.message.MessageType;

@Mixin(MessageType.class)
public class MessageTypeMixin {
    /**
     * Enforces the default "<>" around playernames to make sure this
     * mod can successfully identify player messages for customization.
     */
    @ModifyConstant(method = "initialize", constant = @Constant(stringValue = "chat.type.text"))
    private static String alterTextLangString(String chat) {
        return "text.wmch.mutableChat";
    }
}
