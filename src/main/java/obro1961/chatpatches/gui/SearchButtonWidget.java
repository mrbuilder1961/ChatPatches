package obro1961.chatpatches.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import net.minecraft.util.Identifier;
import obro1961.chatpatches.ChatPatches;

import static obro1961.chatpatches.util.SharedVariables.*;

public class SearchButtonWidget extends TexturedButtonWidget {
    public SearchButtonWidget(int x, int y) {
        super(x, y, 16, 16, 0, 0, 16, Identifier.of(ChatPatches.MOD_ID, "textures/gui/search_buttons.png"),
                16, 32, button -> showSearch = !showSearch);
    }

    @Override
    public boolean mouseClicked(double x, double y, int i) {
        if(active && visible) {
            if(clicked(x, y) && i == 1){
                this.playDownSound(MinecraftClient.getInstance().getSoundManager());
                showSettingsMenu = !showSettingsMenu;
                return true;
            } else {
                return super.mouseClicked(x, y, i);
            }
        } else {
            return false;
        }
    }
}
