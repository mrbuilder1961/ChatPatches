package obro1961.chatpatches.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import org.lwjgl.glfw.GLFW;

import static obro1961.chatpatches.ChatPatches.id;

public class SearchButton extends ImageButton {
    public static final WidgetSprites TEXTURES = new WidgetSprites(id("search_button_unfocused"), id("search_button_focused"));

    private final OnPress onLeftClick;
    private final OnPress onRightClick;

    public SearchButton(int x, int y, OnPress leftAction, OnPress rightAction) {
        super(x, y, 16, 16, TEXTURES, button -> {});

        this.onLeftClick = leftAction;
        this.onRightClick = rightAction;
    }

    @Override
    public boolean mouseClicked(double x, double y, int buttonType) {
        if(active && visible && clicked(x, y)) {
            if(buttonType == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                onLeftClick.onPress(this);
                return true;
            } else if(buttonType == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                onRightClick.onPress(this);
                return true;
            }
        }
        return false;
    }
}