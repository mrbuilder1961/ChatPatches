/**
 * TODO List (highest priority is top):
 **- add diff logs when updating config [DONE]
 **- custom hex formatting colors (0x1A2B3C, TextColor) [DONE]
 **- remove converters [DONE]
 * - duplicate counter (xN, ex. x3)
 * - selectable and copyable messages (built-in w/ regex searching?)
 * - regex +normal search functionality (ChatHudMixin) (based on Vazkii's ChatFlow mod)
 * - smooth message receive (slides up or in)
 * - simplify formattings with a parsable string (ex. "&l&#ff55cc")
 *
 * Changelog:
 * - added diff logs, prints changed features on config save
 * - added custom hex colors for the timestamp and boundary line
 * - removed converter objects; from now on if you update from a really old version just delete the config and re-input the values
 * - shortened and refactored lots of field names
 * - removed useless Config constructors
 * - lots of other miniscule optimizations
 * - fixed config warning showing the wrong dependency requirement version
 * - added more logging
 * - added an extra folder for the "wmch" namespace for modding consistency
 *
 * Notes:
 * - unless needed for null testing, primitives are preferred to wrappers as they should take up slightly less memory
 *
 * Planning:
 *  Regex GUI needs toMatch, toReplace, '&' char for color formatting,
 *  Small explanation, credit to Vazkii, save, delete, preview i/o,
 *  Possible new "highlight" Formatting, Ctrl+F function
 *  For adding dupe counter, make a hard-coded built-in regex replacer for it
 */
package obro1961.wmch;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.minecraft.network.MessageType;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.util.Util;
import obro1961.wmch.config.ClothConfig;
import obro1961.wmch.config.Config;
import obro1961.wmch.mixins.IChatHudAccessorMixin;

public class WMCH implements ModInitializer {
	//private static WMCH instance;
	public static final String[] DATA = {"wmch", "Where's My Chat History"};
	public static final Logger log = LogManager.getLogger(DATA[1]);
	public static final FabricLoader fbl = FabricLoader.getInstance();

	public static boolean moddedConfig;
	public static Config config;
	public static Object[] lastMsgData = new Object[3]; // instances of String(playername), MessageType, and <ContentType>

	@Override
	public void onInitialize() {
		//instance = this;
		//if(fbl.isDevelopmentEnvironment()) log.warn("Hey Dev, remember classes can change in the REMAPPED output file and that can cause errors!");
		if(fbl.getEnvironmentType() == EnvType.SERVER) {
			log.info("{} installed server-side, since this mod is client-side only it does nothing.", DATA[1]);
			return;
		}

		try {
			moddedConfig = fbl.getModContainer("cloth-config").get().getMetadata().getVersion().compareTo(Version.parse("6.1.48"))>=0 && fbl.getModContainer("modmenu").get().getMetadata().getVersion().compareTo(Version.parse("3.0.0"))>=0;
		} catch (Exception e) { moddedConfig = false; }
		if(moddedConfig) config = new ClothConfig(true); else config = new Config(true);
		Config.validate();

		ClientPlayConnectionEvents.JOIN.register((nh, ps, mc) -> {
			try {
				// adds the boundary line if there is more than one message and the last message isn't also a boundary line
				if( mc.inGameHud.getChatHud().getMessageHistory().size()>0 && ((IChatHudAccessorMixin)mc.inGameHud.getChatHud()).getMessages().get(0).getText().asString()!=config.boundaryStr && config.boundary )
					mc.inGameHud.addChatMessage(MessageType.CHAT, new LiteralText(config.boundaryStr).setStyle(Style.EMPTY.withFormatting(config.boundaryFormatting).withColor(config.boundaryColor)), Util.NIL_UUID);
			} catch (Exception e) {log.error("An error occurred while joining a new session; caused by {}:\n{}", e.getCause()!=null ? e.getCause() : "unknown", e.getLocalizedMessage());}
		});

		log.info("Finished setting up client-side!");
	}

	//public static WMCH get() { return instance; }
}