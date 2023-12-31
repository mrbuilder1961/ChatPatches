package obro1961.chatpatches;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.util.Identifier;
import obro1961.chatpatches.accessor.ChatHudAccessor;
import obro1961.chatpatches.chatlog.ChatLog;
import obro1961.chatpatches.config.Config;
import obro1961.chatpatches.util.ChatUtils;
import obro1961.chatpatches.util.Flags;
import obro1961.chatpatches.util.MiscUtils;

public class ChatPatches implements ClientModInitializer {
	public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Chat Patches");
	public static final String MOD_ID = "chatpatches";

	public static Config config = Config.newConfig(false);
	/** Contains the sender and timestamp data of the last received chat message. */
	public static ChatUtils.MessageData msgData = ChatUtils.NIL_MSG_DATA;

	private static String lastWorld = "";

	/**
	 * Creates a new Identifier for Chat Patches.
	 * Throws an error if the path fails to .
	 */
	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}


	@Override
	public void onInitializeClient() {
		/*
		* ChatLog saving events, run if config.chatlog is true:
		* 	CLIENT_STOPPING - Always saves
		* 	SCREEN_AFTER_INIT - Saves if there is no save interval AND if the screen is the OptionsScreen (paused)
		* 	START_CLIENT_TICK - Ticks the save counter, saves if the counter is 0, resets if <0
		* 	MinecraftClientMixin#saveChatlogOnCrash - Always saves
		*/
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ChatLog.serialize(false));
		ScreenEvents.AFTER_INIT.register((client, screen, sW, sH) -> {
			// saves the chat log if [the save interval is 0] AND [the pause menu is showing OR the game isn't focused]
			if( config.chatlogSaveInterval == 0 && (screen instanceof GameMenuScreen || !client.isWindowFocused()) )
				ChatLog.serialize(false);
		});
		ClientTickEvents.START_CLIENT_TICK.register(client -> ChatLog.tickSaveCounter());

		// registers the cached message file importer and boundary sender
		ClientPlayConnectionEvents.JOIN.register((network, packetSender, client) -> {
			if(config.chatlog && !ChatLog.loaded) {
				ChatLog.deserialize();
				ChatLog.restore(client);
			}

			ChatHudAccessor chatHud = ChatHudAccessor.from(client);
			String current = MiscUtils.currentWorldName(client);
			// continues if the boundary line is enabled, >0 messages sent, and if the last and current worlds were servers, that they aren't the same
			if( config.boundary && !chatHud.chatpatches$getMessages().isEmpty() && (!current.startsWith("S_") || !lastWorld.startsWith("S_") || !current.equals(lastWorld)) ) {
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
				chatHud.chatpatches$getVisibleMessages().replaceAll(ln -> new ChatHudLine.Visible(0, ln.content(), ln.indicator(), ln.endOfEntry()));
				Flags.INIT.lower();
			}
		});

		LOGGER.info("[ChatPatches()] Finished setting up!");
	}
}