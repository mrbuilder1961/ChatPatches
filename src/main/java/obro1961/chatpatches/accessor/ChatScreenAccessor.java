package obro1961.chatpatches.accessor;

import net.minecraft.client.gui.screen.ChatScreen;

public interface ChatScreenAccessor {
    //todo here get rid of chatpatches$ orrr whattt
    /** {@link ChatScreen#chatField}{@code .setText(str)} */
    void chatpatches$overrideChatText(String str);
}