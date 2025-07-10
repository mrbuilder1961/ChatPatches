package obro1961.chatpatches.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import static obro1961.chatpatches.ChatPatches.id;

public class SearchButton extends ImageButton {
    //? if >=1.20.2 {
    public static final net.minecraft.client.gui.components.WidgetSprites TEXTURES = new net.minecraft.client.gui.components.WidgetSprites(id("search_button_unfocused"), id("search_button_focused"));
    //?} else {
    /*public static final ResourceLocation FOCUSED_TEXTURE = id("textures/gui/sprites/search_button_focused.png");
    public static final ResourceLocation UNFOCUSED_TEXTURE = id("textures/gui/sprites/search_button_unfocused.png");
    *///?}

    private final OnPress onRightClick;

    public SearchButton(int x, int y, OnPress leftAction, OnPress rightAction) {
        //? if >=1.20.2 {
        super(x, y, 16, 16, TEXTURES, leftAction);
        //?} else {
        /*super(x, y, 16, 16, 0, 0, null, leftAction);
        *///?}

        ResourceLocation ignored = null; // stonecutter: remove qualifier when import optimizer fix is available

        this.onRightClick = rightAction;
    }

    @Override
    public boolean mouseClicked(double x, double y, int buttonType) {
        if(active && visible && /*? if <1.21.2 {*/clicked/*?} else {*//*isMouseOver*//*?}*/(x, y)) {
            if(buttonType == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                onPress.onPress(this);
                return true;
            } else if(buttonType == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                onRightClick.onPress(this);
                return true;
            }
        }
        return false;
    }

    //? if <=1.20.1 {
    /*@Override // stonecutter: remove qualifier when import optimizer fix is available
    public void renderWidget(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        ResourceLocation sprite = isHoveredOrFocused() ? FOCUSED_TEXTURE : UNFOCUSED_TEXTURE;
        guiGraphics.blit(sprite, getX(), getY(), 0, 0, 0, getWidth(), getHeight(), getWidth(), getHeight()); // might need to be the really long method call to specify the texture size
    }
    *///?}
}