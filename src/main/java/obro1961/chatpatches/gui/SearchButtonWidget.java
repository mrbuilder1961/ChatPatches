package obro1961.chatpatches.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ButtonTextures;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import org.lwjgl.glfw.GLFW;

import static obro1961.chatpatches.ChatPatches.id;

public class SearchButtonWidget extends TexturedButtonWidget {
    public static final ButtonTextures TEXTURES = new ButtonTextures(id("search_button_unfocused"), id("search_button_focused"));

    private final PressAction onLeftClick;
    private final PressAction onRightClick;

    public SearchButtonWidget(int x, int y, PressAction leftAction, PressAction rightAction) {
        super(x, y, 16, 16, TEXTURES, button -> {});

        this.onLeftClick = leftAction;
        this.onRightClick = rightAction;
    }

    @Override
    public boolean mouseClicked(double x, double y, int buttonType) {
        if(active && visible && clicked(x, y)) {
            if(buttonType == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                this.playDownSound(MinecraftClient.getInstance().getSoundManager());
                onLeftClick.onPress(this);
                return true;
            } else if(buttonType == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                this.playDownSound(MinecraftClient.getInstance().getSoundManager());
                onRightClick.onPress(this);
                return true;
            }
        }
        return false;
    }
}
