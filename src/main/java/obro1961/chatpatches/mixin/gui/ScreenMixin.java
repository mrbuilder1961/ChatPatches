package obro1961.chatpatches.mixin.gui;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.9 {
import net.minecraft.client.input.KeyEvent;
//?}
import obro1961.chatpatches.accessor.ChatScreenAccess;
import obro1961.chatpatches.gui.ContextMenu;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Unique // "render" -> "extract" is automatic
	private static final String FIX_CLICKTHROUGH_TARGET_METHOD = "render" + "WithTooltipAndSubtitles";
    @Unique // GuiGraphics -> Extractor is automatic, render -> extract is triggered below, argument change is explicitly specified
    //~ render_extraction
	private static final String FIX_CLICKTHROUGH_TARGET_REFERENCE = "Lnet/minecraft/client/gui/GuiGraphics;renderDeferredElements("/*? if >1.21.11 {*//*+ "IIF"*//*?}*/ + ")V"; // stonecutter: 26.1


    /**
     * Blocks switching focus between widget elements if the chat screen is open
     * and the button pressed was UP or DOWN
     */
    @WrapWithCondition(method = "keyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;changeFocus(Lnet/minecraft/client/gui/ComponentPath;)V"))
    private boolean cancelChatSwitchFocus(Screen screen, ComponentPath path, /*$ key_event {*/ KeyEvent key/*$}*/) {
        /*? if >=1.21.9 {*/ int keyCode = key.key(); /*?}*/
        return !(screen instanceof ChatScreen && (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN));
    }

    /*? if >=1.21.11 {*/
    @WrapWithCondition(method = FIX_CLICKTHROUGH_TARGET_METHOD, at = @At(value = "INVOKE", target = FIX_CLICKTHROUGH_TARGET_REFERENCE))
    private boolean fixFuckassTooltipAndClickthrough(/*? if 1.21.11 {*/GuiGraphics receiver,/*?}*/ GuiGraphics graphics, int mX, int mY, float partialTick) {
        if(((Screen) (Object) this) instanceof ChatScreen chatScreen) {
            ChatScreenAccess access = (ChatScreenAccess) chatScreen;
            ContextMenu menu = access.getContextMenu();

            boolean mouseOverMenu = access.isMouseOverSettingsMenu(mX, mY) || menu.isMouseOver(mX, mY);
            if(mouseOverMenu && graphics.deferredTooltip != null) {
                graphics.hoveredTextStyle = null; // delete the source of the chat tooltip (menu button tooltips are only deferred, as experimentally determined)
                return true; // approve rendering (now just the deferred tooltip)
            } else {
                // otherwise just prevent hovering through the menus
                return !mouseOverMenu;
            }
        }

        return true;
    }
    /*?}*/
}