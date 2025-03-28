package obro1961.chatpatches.accessor;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;

public interface IngameHudAccessor {

    // proxy methods for accessing the originals in ChatHud
    /**
     * {@link InGameHud#renderHealthBar(DrawContext, PlayerEntity, int , int , int , int , float , int , int , int , boolean)}
     */
    void chatpatches$renderHealthBar(double x, double y);

    /**
     * {@link InGameHud#getHeartRows(int)}
     */
    int getHeartRows(int heartCount);

}
