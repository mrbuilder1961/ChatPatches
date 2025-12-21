package obro1961.chatpatches.mixin.gui;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.9 {
import net.minecraft.client.input.KeyEvent;
//?}
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    /**
     * Blocks switching focus between widget elements if the chat screen is open
     * and the button pressed was UP or DOWN
     */
    @WrapWithCondition(method = "keyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;changeFocus(Lnet/minecraft/client/gui/ComponentPath;)V"))
    private boolean cancelChatSwitchFocus(Screen screen, ComponentPath path, /*$ key_event {*/ KeyEvent key/*$}*/) {
        /*? if >=1.21.9 {*/ int keyCode = key.key(); /*?}*/
        return !(screen instanceof ChatScreen && (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN));
    }
}