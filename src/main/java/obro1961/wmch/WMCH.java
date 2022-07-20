/**
 * Planned Features (highest priority is top):
 **- fix dupe counter and flag error [DONE]
 **- /copymessage clientside command using an index [DONE]
 * - regex +normal search functionality (ChatHudMixin) (base on ChatFlow by Vazkii)
 *
 * Completed Features (this version):
 * - added "savechat" option which saves your current conversations to a log file and later re-adds them, up to 1024 messages. WARNING: too many can cause large file/memory consumption, periodacally delete the file / F3+D / disable this option to avoid this
 * - replaced hovering and Ctrl+C over messages with a /copymessage command to fix a bug and for easier use
 * 		- index <int> -> copies the <int>th message, with suggestions that show the message represented by the index on hover
 * - added a bit flag field to fix a few errors that could occur in odd situations
 * - (hopefully) ironed out some issues with the dupe counter
 * - fixed timestamp pattern option not formatting escaped characters correctly
 * - nameStr method now finds the name to modify with more accuracy
 * - updated dependencies
 * - updated README
 * - removed unused methods
 * - tweaked the format used for the language files
 *
 * Planning:
 *  Regex GUI needs toMatch, toReplace, '&' char for color formatting,
 *  Small explanation, credit to Vazkii, save, delete, preview i/o
 */
package obro1961.wmch;

import static net.minecraft.util.Util.NIL_UUID;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.mojang.authlib.GameProfile;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import obro1961.wmch.config.ClothConfig;
import obro1961.wmch.config.Config;
import obro1961.wmch.config.Option;
import obro1961.wmch.mixins.ChatHudAccessorMixin;
import obro1961.wmch.util.CopyMessageCommand;
import obro1961.wmch.util.Util;

public class WMCH implements ModInitializer {
	public static final String[] NAMES = { "wmch", "Where's My Chat History" };
	public static final String[] DEPENDENTS = { "6.2.62", "3.2.3" };
	public static final Logger log = LogManager.getLogger(NAMES[1]);
	public static final FabricLoader fbl = FabricLoader.getInstance();
	public static final File chatLogFile = new File( fbl.getGameDir().toFile().getAbsolutePath() + "/logs/chatlog.json" );

	public static Config config;
	public static boolean inGameConfig;
	public static List<JsonElement> cachedMsgs = new ArrayList<>(100); // messages loaded from and to be loaded to chatlog.json
	public static GameProfile msgSender = new GameProfile(NIL_UUID, ""); // instantiated to avoid inital errors
	/**
	 * Flags explained (bitwise):
	 * |  #  | modify message? |          meaning          |
	 * |-----|-----------------|---------------------------|
	 * | -1  |       yes       | nothing done yet          |
	 * |  0  |       yes       | done loading chatlog.json |
	 * |  1  |       no        | loading chatlog.json      |
	 * |2/4/6|       no        | changing chat settings    |
	 *
	 * These flags are checked when applying message modifications
	 * and when injecting a message counter so no weird issues occur.
	 * Whole numbers mean no modifications.
	 */
	public static int flags = -1;

	private Gson json = new Gson();

	@Override
	public void onInitialize() {
		if(fbl.getEnvironmentType() == EnvType.SERVER) {
			log.info("{} installed server-side; disabling.", NAMES[1]);
			return;
		}

		try {
			inGameConfig = fbl.getModContainer("cloth-config").get().getMetadata().getVersion().compareTo(Version.parse(DEPENDENTS[0])) >= 0 &&
			fbl.getModContainer("modmenu").get().getMetadata().getVersion().compareTo(Version.parse(DEPENDENTS[1])) >= 0;
		} catch (NoSuchElementException | VersionParsingException e) {
			inGameConfig = false;
		}
		if (inGameConfig)
			config = new ClothConfig();
		else
			config = new Config();
		Config.validate();

		CopyMessageCommand.register(ClientCommandManager.DISPATCHER);

		// registers the cached message file importer and boundary sender
		ClientPlayConnectionEvents.JOIN.register((nh, ps, mc) -> {
			if (flags < 0 && Option.SAVECHAT.get()) { // only runs on first world join
				try (FileReader fr = new FileReader(chatLogFile)) {
					List<JsonElement> saved = json.fromJson(fr, new TypeToken<List<JsonElement>>(){}.getType());

					flags = 1;
					if(saved != null && !saved.isEmpty()) {
						cachedMsgs.addAll(saved);
						saved.forEach(msg -> mc.inGameHud.getChatHud().addMessage( Text.Serializer.fromJson(msg) ));
					}

					log.info("chatlog.json currently using {} bytes of data", java.nio.file.Files.size(chatLogFile.toPath()));
					log.debug("Loaded chat log containing {} messages from logs/chatlog.json", cachedMsgs.size());
				} catch (JsonIOException | IOException e) {
					log.error("An error occurred while trying to load the chat log:");
					e.printStackTrace();
				} finally {
					flags = 0;
				}
			} else if (Option.BOUNDARY.get()) {
				try {
					List<ChatHudLine<Text>> chatMsgs = ((ChatHudAccessorMixin) mc.inGameHud.getChatHud()).getMessages();

					// IF there is more than one message AND the last message isn't also a boundary line THEN adds the boundary line
					if (!chatMsgs.isEmpty() && !chatMsgs.get(0).getText().getString() .equals( Util.delAll(Option.BOUNDARYSTR.get(), "(&[0-9a-fA-Fk-orK-OR])+"))) {
						mc.inGameHud.addChatMessage(
							net.minecraft.network.MessageType.CHAT,
							((LiteralText) Util.getStrTextF(Option.BOUNDARYSTR.get())).setStyle(Style.EMPTY.withColor(Option.BOUNDARYCOLOR.get())),
							NIL_UUID
						);
					}
				}
				catch (IndexOutOfBoundsException e) {/* Thrown when there are no messages to check */}
				catch (Exception e) {
					log.warn("An error occurred while joining a new session:");
					e.printStackTrace();
				}
			}
		});

		// registers the cached message file writer
		ClientPlayConnectionEvents.DISCONNECT.register((nh, mc) -> {
			try (FileWriter fw = new FileWriter(chatLogFile)) {
				while(cachedMsgs.size() > 1024)
					cachedMsgs.remove(0);

				json.toJson(cachedMsgs, List.class, fw);

				log.debug("Saved chat log containing {} messages to logs/chatlog.json", cachedMsgs.size());
			} catch (JsonIOException | IOException e) {
				log.error("An error occurred while trying to save the chat log:");
				e.printStackTrace();
			}
		});

		log.info("Finished setting up client-side!");
	}
}