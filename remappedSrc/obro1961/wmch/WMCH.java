package obro1961.wmch;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.minecraft.network.MessageType;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Util;
import obro1961.wmch.config.ClothConfig;
import obro1961.wmch.config.Config;
import obro1961.wmch.mixins.IChatHudAccessorMixin;

@Environment(EnvType.CLIENT)
public class WMCH implements ClientModInitializer {
	public static final String ID = "wmch";
	public static final String NAME = "Where's My Chat History";
	public static final Logger log = LogManager.getLogger(NAME);
	public static final FabricLoader fbl = FabricLoader.getInstance();
	public static boolean isConfigModded;
	public static Config config;
	public static Object[] lastMDat = {null, null}; // last message data

	@Override
	public void onInitializeClient() {
		// updates the config status, if the versions are >= the minimum and are installed
		try {
			isConfigModded = fbl.getModContainer("cloth-config").get().getMetadata().getVersion().compareTo(Version.parse("6.1.48"))>=0 && fbl.getModContainer("modmenu").get().getMetadata().getVersion().compareTo(Version.parse("3.0.0"))>=0;
		} catch (Exception e) { isConfigModded = false; }

		if(isConfigModded) {
			config = new ClothConfig(true);
			ClothConfig.validateConfig();
		} else {
			config = new Config(true);
			Config.validateConfig();
		}
		// register reload event: non-cloth config re-read
		if(!config.boundary) return;
		ClientPlayConnectionEvents.JOIN.register((nh, ps, mc) -> {
			try {
				// adds the boundary line if there is more than one message and the last message isn't also a boundary line
				if( mc.inGameHud.getChatHud().getMessageHistory().size()>0 && ((IChatHudAccessorMixin)mc.inGameHud.getChatHud()).getMessages().get(0).getText().asString()!=config.boundaryStr ) {
					mc.inGameHud.addChatMessage(MessageType.CHAT, new LiteralText(config.boundaryStr).formatted(config.boundaryFormatting), Util.NIL_UUID);
				}
			} catch (Exception e) {log.error("An error occurred while joining a new session; caused by '{}':\n{}", e.getCause(), e.getLocalizedMessage());}
		});

		log.info("Finished loading!");
	}
}