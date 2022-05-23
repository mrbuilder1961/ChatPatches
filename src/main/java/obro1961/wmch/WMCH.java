/**
 * TODO List (highest priority is top):
 * - highlighted and copyable important messages or smart messages (not configurable, ex. port from LAN screen)
 *!- duplicate counter (xN, ex. x3) [NOT WORKING, BORROW FROM OTHER MOD]
 * - regex +normal search functionality (ChatHudMixin) (based on Vazkii's ChatFlow mod)
 *
 * Planning:
 *  Regex GUI needs toMatch, toReplace, '&' char for color formatting,
 *  Small explanation, credit to Vazkii, save, delete, preview i/o
 */
package obro1961.wmch;

import java.util.List;

import com.mojang.authlib.GameProfile;

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
import obro1961.wmch.config.ClothConfig;
import obro1961.wmch.config.Config;
import obro1961.wmch.config.Option;
import obro1961.wmch.mixins.IChatHudAccessorMixin;

public class WMCH implements ModInitializer {
	public static final String[] NAMES = {"wmch", "Where's My Chat History"};
	public static final String[] VERSION_REQS = {"6.2.57", "3.1.0"};
	public static final Logger log = LogManager.getLogger(NAMES[1]);
	private static final FabricLoader fbl = FabricLoader.getInstance();

	public static boolean inGameConfig;
	public static Config config;
	public static GameProfile msgSender = new GameProfile(net.minecraft.util.Util.NIL_UUID, ""); // instances of GameProfile(message sender) and Style (hovered chat message); instantiated to avoid inital startup errors

	@Override
	public void onInitialize() {
		if(fbl.getEnvironmentType() == EnvType.SERVER) {
			log.info("{} installed server-side; disabling.", NAMES[1]);
			return;
		}

		try { inGameConfig =
			fbl.getModContainer("cloth-config").get().getMetadata().getVersion().compareTo(Version.parse( VERSION_REQS[0] )) >=0 &&
			fbl.getModContainer("modmenu").get().getMetadata().getVersion().compareTo(Version.parse( VERSION_REQS[1] )) >=0 ;
		} catch (Exception e) { inGameConfig = false; }
		if(inGameConfig) config = new ClothConfig(); else config = new Config();
		Config.validate();

		ClientPlayConnectionEvents.JOIN.register((nh, ps, mc) -> {
			try {
				// adds the boundary line if there is more than one message and the last message isn't also a boundary line
				List<ChatHudLine<Text>> chatMsgs = ((IChatHudAccessorMixin)mc.inGameHud.getChatHud()).getMessages();
				if(!chatMsgs.isEmpty() && Option.BOUNDARY.get()) {
					if( !chatMsgs.get(0).getText().getString().equals( Util.delAll(Option.BOUNDARYSTR.get(), "(&[0-9a-fA-Fk-orK-OR])+") ) )
						mc.inGameHud.addChatMessage(
							MessageType.CHAT,
							((LiteralText)Util.getStrTextF( Option.BOUNDARYSTR.get() )).setStyle( Style.EMPTY.withColor(Option.BOUNDARYCOLOR.get()) ),
							net.minecraft.util.Util.NIL_UUID
						);
				}
			}
			catch(IndexOutOfBoundsException e){/* Thrown when there are no messages to check */}
			catch(Exception e){log.warn("An error occurred while joining a new session; caused by {}:\t{}",e.getCause()!=null ? e.getCause() : "unknown",e.getLocalizedMessage());}
		});

		log.info("Finished setting up client-side!");
	}
}