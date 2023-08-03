package obro1961.chatpatches.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import net.minecraft.util.Identifier;
import obro1961.chatpatches.ChatPatches;
import org.lwjgl.glfw.GLFW;

public class SearchButtonWidget extends TexturedButtonWidget {
    private final PressAction onLeftClick;
    private final PressAction onRightClick;

    public SearchButtonWidget(int x, int y, PressAction leftAction, PressAction rightAction) {
        super(x, y, 16, 16, 0, 0, 16, Identifier.of(ChatPatches.MOD_ID, "textures/gui/search_buttons.png"), 16, 32, button -> {});
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
