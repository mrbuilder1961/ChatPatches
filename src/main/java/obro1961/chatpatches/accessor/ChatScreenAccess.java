package obro1961.chatpatches.accessor;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;

public interface ChatScreenAccess {
    /** {@link ChatScreen#chatField} */
    TextFieldWidget chatpatches$getChatField();
}