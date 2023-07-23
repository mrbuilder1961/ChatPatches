package obro1961.chatpatches;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.gui.hud.ChatHudLine;
import obro1961.chatpatches.accessor.ChatHudAccessor;
import obro1961.chatpatches.chatlog.ChatLog;
import obro1961.chatpatches.config.Config;
import obro1961.chatpatches.util.Flags;
import obro1961.chatpatches.util.MiscUtils;

public class ChatPatches implements ClientModInitializer {
	public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Chat Patches");
	public static final String MOD_ID = "chatpatches";

	public static Config config = Config.newConfig(false);
	private static String lastWorld = "";


	/**
	 * <ol>
	 * 	<li> Registers a callback on {@link ClientLifecycleEvents#CLIENT_STOPPING} for {@link ChatLog#serialize(boolean)} on a normal game exit
	 * 	<li> Registers a callback on {@link ClientPlayConnectionEvents#JOIN} for loading the {@link ChatLog} and adding boundary lines
	 * </ol>
	 */
	@Override
	public void onInitializeClient() {
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ChatLog.serialize(false));
		// registers the cached message file importer and boundary sender
		ClientPlayConnectionEvents.JOIN.register((network, packetSender, client) -> {

			// Loads the chat log if SAVE_CHAT enabled and the ChatLog hasn't been loaded yet
			if( config.chatLog && !ChatLog.loaded ) {
				ChatLog.deserialize();
				ChatLog.restore(client);
			}

			ChatHudAccessor chatHud = ChatHudAccessor.from(client);
			String current = MiscUtils.currentWorldName(client);
			// continues if the boundary line is enabled, >0 messages sent, and if the last and current worlds were servers, that they aren't the same
			if( config.boundary && !chatHud.getMessages().isEmpty() && (!current.startsWith("S_") || !lastWorld.startsWith("S_") || !current.equals(lastWorld)) ) {

				try {
					String levelName = (lastWorld = current).substring(2); // makes a variable to update lastWorld in a cleaner way

					Flags.BOUNDARY_LINE.raise();
					client.inGameHud.getChatHud().addMessage( config.makeBoundaryLine(levelName) );
					Flags.BOUNDARY_LINE.lower();

				} catch(Exception e) {
					LOGGER.warn("[ChatPatches.boundary] An error occurred while adding the boundary line:", e);
				}
			}

			// sets all messages (restored and boundary line) to a addedTime of 0 to prevent instant rendering (#42)
			if(ChatLog.loaded && Flags.INIT.isRaised()) {
				chatHud.getVisibleMessages().replaceAll(ln -> new ChatHudLine.Visible(0, ln.content(), ln.indicator(), ln.endOfEntry()));
				Flags.INIT.lower();
			}
		});

		LOGGER.info("[ChatPatches()] Finished setting up!");
	}
}