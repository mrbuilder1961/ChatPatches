/*
 * Planning:
 * Regex GUI needs toMatch, toReplace, '&' char for color formatting,
 * Small explanation, credit to Vazkii, save, delete, preview i/o
 */
package mechanicalarcane.wmch;

import static net.fabricmc.loader.api.metadata.ModDependency.Kind.BREAKS;
import static net.fabricmc.loader.api.metadata.ModDependency.Kind.DEPENDS;
import static net.fabricmc.loader.api.metadata.ModDependency.Kind.RECOMMENDS;
import static net.fabricmc.loader.api.metadata.ModDependency.Kind.SUGGESTS;

import java.io.File;
import java.util.List;
import java.util.Optional;

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
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.network.message.MessageSender;
import net.minecraft.network.message.MessageType;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

public class WMCH implements ClientModInitializer {
	public static final String[] NAMES = { "wmch", "Where's My Chat History", "WMCH" };
	public static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger(NAMES[1]);
	public static final FabricLoader FABRICLOADER = FabricLoader.getInstance();
	public static final File chatLogFile = new File( FABRICLOADER.getGameDir().toFile().getAbsolutePath() + "/logs/chatlog.json" );

	public static Config config = Config.newConfig();
	public static MessageSender msgSender = Util.NIL_SENDER;


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
							mc.inGameHud.getChatHud().addMessage(msg);

					LOGGER.info("Added {} messages and {} sent messages from ./logs/chatlog.json into Minecraft!", messagesSize, historySize);
				}

				// resets flags
				Flag.LOADING_CHATLOG.unSet();
				Flag.INIT.unSet();
			} else {
				// Adds boundary message if not first join and messages exist and last message isn't a boundary line
				if(Option.BOUNDARY.get()) {
					try {
						List<ChatHudLine<Text>> chatMsgs = Util.accessChatHud(mc).getMessages();
						// IF there is more than one message AND the last message isn't also a boundary line THEN adds the boundary line
						if(!chatMsgs.isEmpty() && !Util.isBoundaryLine( chatMsgs.get(0).getText().getString() )) {
							mc.inGameHud.onChatMessage(
								net.minecraft.util.registry.BuiltinRegistries.MESSAGE_TYPE.get(MessageType.SYSTEM),
								Util.formatString(Option.BOUNDARY_STR.get()) .setStyle(Style.EMPTY.withColor(Option.BOUNDARY_COLOR.get())),
								Util.NIL_SENDER
							);
						}
					} catch(Exception e) {
						LOGGER.warn("[boundaryAdder] An error occurred while joining a new session:", e);
					}
				}
			}
		});

		LOGGER.info("Finished setting up!");
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
			LOGGER.warn("[writeCachedData({})] An error occurred while trying to save the chat log{}:", crashed, crashed ? " after a crash" : "", e);
		}
	}


	public static class Relation {
		private boolean installed;
		private String id;
		private String name;
		private Version version; // 0.0.0 if not present
		private ModDependency.Kind type;


		private Relation(String id, String version, ModDependency.Kind type) {
			try {
				Optional<ModContainer> me = FABRICLOADER.getModContainer(id);

				this.installed = me.isPresent();
				this.id = id;
				this.name = installed ? me.get().getMetadata().getName() : id;
				this.version = me.isPresent() ? me.get().getMetadata().getVersion() : Version.parse("0.0.0");
				this.type = type;

			} catch(VersionParsingException e) {
				LOGGER.fatal("[Relation()] This shouldn't appear, it means a mod relation version was inputted incorrectly. Please report this on GitHub:", e);
			}
		}


		public boolean installed() { return installed; }
		public String getId() { return id; }
		public String getName() { return name; }
		public Version getVersion() { return version; }
		public ModDependency.Kind getType() { return type; }


		public static final Relation FABRIC_API = new Relation("fabric-api", "0.58.0", DEPENDS);
		public static final Relation CLOTH_CONFIG = new Relation("cloth-config", "7.0.73", RECOMMENDS);
		public static final Relation MODMENU = new Relation("modmenu", "4.0.4", RECOMMENDS);
		public static final Relation NOCHATREPORTS = new Relation("nochatreports", "*", SUGGESTS);
		public static final Relation MORECHATHISTORY = new Relation("morechathistory", "*", BREAKS);
	}
}