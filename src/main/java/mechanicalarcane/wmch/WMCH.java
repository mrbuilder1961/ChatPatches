package mechanicalarcane.wmch;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import mechanicalarcane.wmch.config.Config;
import mechanicalarcane.wmch.util.Util;
import mechanicalarcane.wmch.util.Util.Flags;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.network.message.SignedMessage;

public class WMCH implements ClientModInitializer {
	public static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger("Where's My Chat History");
	public static final FabricLoader FABRICLOADER = FabricLoader.getInstance();

	public static Config config = Config.newConfig(false);
	/** Contains the sender and timestamp data of the last received chat message. */
	public static SignedMessage lastMsg = Util.NIL_MESSAGE;
	private static String lastWorld = "";


	/**
	 * <ol>
	 * 	<li> Initializes MixinExtras for more Mixin annotations
	 *	<li> Registers a callback on {@link ClientCommandRegistrationCallback#EVENT} for the {@link CopyMessageCommand}
	 * 	<li> Registers a callback on {@link ClientLifecycleEvents#CLIENT_STOPPING} for {@link ChatLog#serialize(boolean)} on a normal game exit
	 * 	<li> Registers a callback on {@link ClientPlayConnectionEvents#JOIN} for loading the {@link ChatLog} and adding boundary lines
	 * </ol>
	 */
	@Override
	public void onInitializeClient() {
		MixinExtrasBootstrap.init();
		//CrowdinTranslate.downloadTranslations("wmch"); // use github workflow thing

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> CopyMessageCommand.register(dispatcher) );

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ChatLog.serialize(false));
		// registers the cached message file importer and boundary sender
		ClientPlayConnectionEvents.JOIN.register((network, packetSender, client) -> {

			// Loads the chat log if SAVE_CHAT enabled and the ChatLog hasn't been loaded yet
			if( config.saveChat && !ChatLog.loaded ) {
				ChatLog.deserialize();
				ChatLog.restore(client);
			}


			String current = Util.currentWorldName(client);
			// continues if the boundary line is enabled, >0 messages sent, and if the last and current worlds were servers, that they aren't the same
			if( config.boundary && !Util.chatHud(client).getMessages().isEmpty() && (!current.startsWith("S_") || !lastWorld.startsWith("S_") || !current.equals(lastWorld)) ) {

				try {
					String levelName = (lastWorld = current).substring(2); // makes a variable to update lastWorld in a cleaner way

					Flags.BOUNDARY_LINE.set();
					client.inGameHud.getChatHud().addMessage( config.getFormattedBoundary(levelName) );
					Flags.BOUNDARY_LINE.remove();

				} catch(Exception e) {
					LOGGER.warn("[WMCH.boundary] An error occurred while adding the boundary line:", e);
				}
			}

			// sets all messages (restored and boundary line) to a addedTime of 0 to prevent instant rendering (#42)
			if(ChatLog.loaded && Flags.INIT.isSet())
				Util.chatHud(client).getVisibleMessages().replaceAll( ln -> new ChatHudLine.Visible(0, ln.content(), ln.indicator(), ln.endOfEntry()) );

			Flags.INIT.remove();
		});

		LOGGER.info("[WMCH()] Finished setting up!");
	}
}