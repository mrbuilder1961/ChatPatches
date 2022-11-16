/*
 * Planning:
 * Regex GUI needs toMatch, toReplace, '&' char for color formatting,
 * Small explanation, credit to Vazkii, save, delete, preview i/o
 */

package mechanicalarcane.wmch;

import java.util.List;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;

import mechanicalarcane.wmch.config.Config;
import mechanicalarcane.wmch.config.Option;
import mechanicalarcane.wmch.util.ChatLog;
import mechanicalarcane.wmch.util.CopyMessageCommand;
import mechanicalarcane.wmch.util.Util;
import mechanicalarcane.wmch.util.Util.Flag;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageMetadata;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

public class WMCH implements ClientModInitializer {
	public static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger("Where's My Chat History");
	public static final FabricLoader FABRICLOADER = FabricLoader.getInstance();

	public static Config config = Config.newConfig();
	/** Represents the sender and timestamp data of the last recieved chat message. */
	public static MessageMetadata lastMeta = Util.NIL_METADATA;


	/**
	 *?If devenv, downloads CrowdInTranslate files.
	 * Validates the newly created Config object.
	 * Initializes the ChatLog.
	 * Registers the CopyMessageCommand on client's server initialization
	 * Registers a callback to {@link WMCH#writeCachedData()} with false
	 * to save data on normal game exits.
	 * Registers a callback to World Join events which loads cached data and
	 * adds boundary lines.
	 */
	@Override
	public void onInitializeClient() {
		/* if(FABRICLOADER.isDevelopmentEnvironment()) {
			//CrowdinTranslate.downloadTranslations("projectName", NAMES[0]);
		} */
		MixinExtrasBootstrap.init();

		config.validate();
		ChatLog.initialize();

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> CopyMessageCommand.register(dispatcher) );
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> WMCH.writeCachedData(false));

		// registers the cached message file importer and boundary sender
		ClientPlayConnectionEvents.JOIN.register((nh, ps, mc) -> {
			// runs on FIRST world join ONLY
			if( Flag.INIT.isSet() ) {
				// Loads the chat log if first world join and SAVE_CHAT enabled and ChatLog hasn't been loaded yet
				if( Option.SAVE_CHAT.get() && !ChatLog.loaded ) {
					ChatLog.deserialize();

					int historySize = ChatLog.getHistory().size();
					int messagesSize = ChatLog.getMessages().size();

					Flag.LOADING_CHATLOG.set();

					if( historySize > 0 )
						for(String sent : ChatLog.getHistory().toArray( new String[historySize] ))
							mc.inGameHud.getChatHud().addToMessageHistory(sent);
					if( messagesSize > 0)
						for(Text msg : ChatLog.getMessages().toArray( new Text[messagesSize] ))
							mc.inGameHud.getChatHud().addMessage(msg, MessageSignatureData.EMPTY, new MessageIndicator(0x382fb5, null, null, "Restored"));

					LOGGER.info("[ChatLog.worldInit] Added {} messages and {} history messages from ./logs/chatlog.json into Minecraft!", messagesSize, historySize);
				}

				// resets flags
				Flag.LOADING_CHATLOG.unSet();
				Flag.INIT.unSet();
			} else {
				// Adds boundary message if not first join and messages exist and last message isn't a boundary line
				if(Option.BOUNDARY.get()) {
					try {
						List<ChatHudLine> chatMsgs = Util.accessChatHud(mc).getMessages();
						// IF there is more than one message AND the last message isn't also a boundary line THEN adds the boundary line
						if(!chatMsgs.isEmpty() && !Util.isBoundaryLine( chatMsgs.get(0).content().getString() )) {
							mc.inGameHud.getChatHud().addMessage(
								Util.formatString(Option.BOUNDARY_STR.get()) .fillStyle(Style.EMPTY.withColor(Option.BOUNDARY_COLOR.get()))
							);
						}
					} catch(Exception e) {
						LOGGER.warn("[WMCH.boundary] An error occurred while joining a new session:", e);
					}
				}
			}
		});

		LOGGER.info("[WMCH()] Finished setting up!");
	}


	/**
	 * The callback method which saves cached message data.
	 * Injected into {@link MinecraftClient#run} to ensure
	 * saving when possible.
	 * @param crashed {@code true} if a crash occurred
	*/
	public static void writeCachedData(boolean crashed) {
		try {
			ChatLog.serialize(crashed);
		} catch(Exception e) {
			LOGGER.warn("[WMCH.writeCached({})] An error occurred while trying to save the chat log{}:", crashed, crashed ? " after a crash" : "", e);
		}
	}
}