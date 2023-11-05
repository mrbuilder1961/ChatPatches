package obro1961.chatpatches.mixin.gui;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.client.gui.navigation.GuiNavigationPath;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import obro1961.chatpatches.accessor.ChatScreenAccessor;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static obro1961.chatpatches.ChatPatches.config;

@Mixin(Screen.class)
public class ScreenMixin {
    /** Blocks switching focus between widget elements if the chat screen is open and the button pressed was UP or DOWN */
    @WrapWithCondition(method = "keyPressed", at = @At(value = "INVOKE",target = "Lnet/minecraft/client/gui/screen/Screen;switchFocus(Lnet/minecraft/client/gui/navigation/GuiNavigationPath;)V"))
    private boolean cancelChatSwitchFocus(Screen screen, GuiNavigationPath path, int keyCode, int scanCode, int modifiers) {
        return !(screen instanceof ChatScreen && (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN));
    }

    /**
     * Clears the message draft when the chat screen is closed manually by the user pressing ESC
     */
    @Inject(method = "keyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;close()V"))
    private void clearMessageDraft(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if(!config.onlyInvasiveDrafting) return;
        if (((Screen) (Object) this) instanceof ChatScreen chatScreen) {
            ChatScreenAccessor.from(chatScreen).chatPatches$clearMessageDraft();
        }
    }
}
