package obro1961.chatpatches.accessor;

import net.minecraft.client.gui.screen.ChatScreen;

public interface ChatScreenAccessor {
    /** {@link ChatScreen#chatField}{@code .setText(str)} */
    void chatpatches$overrideChatText(String str);

    /** {@code ChatScreen.setFocused(ChatScreen.chatField)} */
    void chatpatches$focusChatField();
}