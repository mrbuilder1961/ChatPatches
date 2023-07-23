package obro1961.chatpatches.util;

import net.fabricmc.loader.api.FabricLoader;

public class SharedVariables {
    public static final FabricLoader FABRIC_LOADER = FabricLoader.getInstance();

    /** Contains the sender and timestamp data of the last received chat message. */
    public static ChatUtils.MessageData lastMsg = ChatUtils.NIL_MSG_DATA;
}
