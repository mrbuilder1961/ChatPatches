package obro1961.chatpatches.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ImageButton;
//? if >=1.20.2 {
import net.minecraft.client.gui.components.WidgetSprites;
//?} else {
//import net.minecraft.resources.ResourceLocation;
//?}
//? if >=1.21.9 {
import net.minecraft.client.input.MouseButtonEvent;
//?}
import org.lwjgl.glfw.GLFW;

import static obro1961.chatpatches.ChatPatches.id;

public class SearchButton extends ImageButton {
    //? if >=1.20.2 {
    public static final WidgetSprites TEXTURES = new WidgetSprites(id("search_button_unfocused"), id("search_button_focused"));
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

        this.onRightClick = rightAction;
    }

    @Override
    public boolean mouseClicked(/*$ mouse_event {*/MouseButtonEvent mouse, boolean bl/*$}*/) {
        //? if >=1.21.9 {
        double mX = mouse.x(), mY = mouse.y();
        int button = mouse.button();
        //?}

        if(active && visible && /*? if <1.21.2 {*//*clicked*//*?} else {*/isMouseOver/*?}*/(mX, mY)) {
            if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                onPress.onPress(this);
                return true;
            } else if(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                onRightClick.onPress(this);
                return true;
            }
        }
        return false;
    }

    //? if <=1.20.1 {
    /*@Override
    public void renderWidget(net.minecraft.client.gui.GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        ResourceLocation sprite = isHoveredOrFocused() ? FOCUSED_TEXTURE : UNFOCUSED_TEXTURE;
        guiGraphics.blit(sprite, getX(), getY(), 0, 0, 0, getWidth(), getHeight(), getWidth(), getHeight()); // might need to be the really long method call to specify the texture size
    }
    *///?}
}