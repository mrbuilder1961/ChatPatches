/**
 * TODO List (highest priority is top):
 * - duplicate counter (xN, ex. x3)
 * - selectable and copyable messages (built-in w/ regex searching?)
 * - regex +normal search functionality (ChatHudMixin) (based on Vazkii's ChatFlow mod)
 * - simplify formattings with a parsable string (ex. "&l&#ff55cc")
 * - smooth message receive (slides up or in)
 *
 * Notes:
 * - unless needed for null testing, primitives are preferred to wrappers as they should take up slightly less memory
 *
 * Planning:
 * Planning:
 *  Regex GUI needs toMatch, toReplace, '&' char for color formatting,
 *  Small explanation, credit to Vazkii, save, delete, preview i/o,
 *  Possible new "highlight" Formatting, Ctrl+F function
 *  For selecting and copying, when enabled it adds a dark purple [*] before each LINE and when clicked it puts it in the text box
 *  For adding dupe counter, make a hard-coded built-in regex replacer for it
 *  OR for dupe counter we can look over messages every second or so and if 2 messages are equal edit them there
 *  (messages, visibleMessages, messageHistory)
 */
package obro1961.wmch;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.network.MessageType;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import obro1961.wmch.config.ClothConfig;
import obro1961.wmch.config.Config;
import obro1961.wmch.mixins.IChatHudAccessorMixin;

public class WMCH implements ModInitializer {
	public static final String[] DATA = {"wmch", "Where's My Chat History"};
	public static final Logger log = LogManager.getLogger(DATA[1]);
	public static final FabricLoader fbl = FabricLoader.getInstance();

	public static boolean moddedConfig;
	public static Config config;
	public static Object[] lastMsgData = new Object[2]; // instances of UUID(playerID) and MessageType

	@Override
	public void onInitialize() {
		//if(fbl.isDevelopmentEnvironment()) log.warn("Hey Dev, remember classes can change in the REMAPPED jar file and that can cause errors!");
		if(fbl.getEnvironmentType() == EnvType.SERVER) {
			log.info("{} installed server-side, since this mod is client-side only it does nothing.", DATA[1]);
			return;
		}

		try {
			moddedConfig = fbl.getModContainer("cloth-config").get().getMetadata().getVersion().compareTo(Version.parse("6.2.57"))>=0 && fbl.getModContainer("modmenu").get().getMetadata().getVersion().compareTo(Version.parse("3.1.0"))>=0;
		} catch (Exception e) { moddedConfig = false; }
		if(moddedConfig) config = new ClothConfig(true); else config = new Config(true);
		Config.validate();

		ClientPlayConnectionEvents.JOIN.register((nh, ps, mc) -> {
			try {
				// adds the boundary line if there is more than one message and the last message isn't also a boundary line
				List<ChatHudLine<Text>> chatMsgs;
				if( (chatMsgs=((IChatHudAccessorMixin)mc.inGameHud.getChatHud()).getMessages()).size()>0 && config.boundary ) {
					if(!( chatMsgs.isEmpty() || chatMsgs.get(0).getText().getString().equals(config.boundaryStr) ))
						mc.inGameHud.addChatMessage(MessageType.CHAT, new LiteralText(config.boundaryStr).setStyle(Style.EMPTY.withFormatting(config.boundaryFormatting).withColor(config.boundaryColor)), Util.NIL_UUID);
				}
			}
			catch(IndexOutOfBoundsException e){/* Thrown when there are no messages to check */}
			catch(Exception e){log.warn("An error occurred while joining a new session; caused by {}:\t{}",e.getCause()!=null ? e.getCause() : "unknown",e.getLocalizedMessage());}
		});

		log.info("Finished setting up client-side!");
	}
}