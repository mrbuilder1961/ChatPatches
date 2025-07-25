package obro1961.chatpatches.accessor;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;

import java.util.List;

public interface ChatScreenAccess {
    /** {@link ChatScreen#input} */
    EditBox chatpatches$getChatField();


    List<GuiMessage> getSearchResults();
}