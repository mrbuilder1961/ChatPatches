package obro1961.chatpatches.accessor;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import obro1961.chatpatches.mixin.gui.ChatHudMixin;

import java.util.List;

public interface ChatHudAccess {
    /** {@link ChatComponent#allMessages} */
    List<GuiMessage> chatpatches$getMessages();

    /** {@link ChatComponent#trimmedMessages} */
    List<GuiMessage.Line> chatpatches$getVisibleMessages();

    /** {@link ChatComponent#scrolledLines} */
    int chatpatches$getScrolledLines();

    /** {@link ChatComponent#getLineHeight()} */
    int chatpatches$getLineHeight();


    // unique methods

    /** {@link ChatHudMixin#getChatHudLineIndex(double, double)} */
    int getChatHudLineIndex(double mouseX, double mouseY);

    /** {@link ChatHudMixin#getEoEIndex(double, double)} */
    int getEoEIndex(double mouseX, double mouseY);
}