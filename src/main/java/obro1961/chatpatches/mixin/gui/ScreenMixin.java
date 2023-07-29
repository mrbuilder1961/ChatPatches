package obro1961.chatpatches.mixin.gui;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.client.gui.navigation.GuiNavigationPath;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Screen.class)
public class ScreenMixin {
    /** Blocks switching focus between widget elements if the chat screen is open and the button pressed was UP or DOWN */
    @WrapWithCondition(method = "keyPressed", at = @At(value = "INVOKE",target = "Lnet/minecraft/client/gui/screen/Screen;switchFocus(Lnet/minecraft/client/gui/navigation/GuiNavigationPath;)V"))
    private boolean cancelChatSwitchFocus(Screen screen, GuiNavigationPath path, int keyCode, int scanCode, int modifiers) {
        return !(screen instanceof ChatScreen && (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN));
    }
}
