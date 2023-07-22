package obro1961.chatpatches.mixin.gui;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.client.gui.navigation.GuiNavigationPath;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Screen.class)
public class ScreenMixin {
    // Block focus switches if chat screen is opened
    @WrapWithCondition(method = "keyPressed", at = @At(value = "INVOKE",target = "Lnet/minecraft/client/gui/screen/Screen;switchFocus(Lnet/minecraft/client/gui/navigation/GuiNavigationPath;)V"))
    private boolean cps$onSwitchFocus(Screen screen, GuiNavigationPath path) {
        return !(screen instanceof ChatScreen);
    }
}
