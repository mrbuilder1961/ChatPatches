package obro1961.chatpatches.accessor;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;

public interface ChatScreenAccess {
    /** {@link ChatScreen#chatField} */
    EditBox chatpatches$getChatField();
}