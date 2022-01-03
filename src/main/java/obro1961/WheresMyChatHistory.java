package obro1961;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.network.MessageType;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Util;
import obro1961.config.Config;
import obro1961.mixins.IChatHudAccessorMixin;

@Environment(EnvType.CLIENT)
public class WheresMyChatHistory implements ClientModInitializer {
	public static final Logger log = LogManager.getLogger("Where's My Chat History");

	@Override
	public void onInitializeClient() {
		Config.load();

		if(Config.cfg.boundary_enabled) {
			ClientPlayConnectionEvents.JOIN.register((nh, ps, mc) -> {
				try {
					// adds the boundary line if there is more than one message and the last message isn't also a boundary line
					if( mc.inGameHud.getChatHud().getMessageHistory().size()>0 && ((IChatHudAccessorMixin)mc.inGameHud.getChatHud()).getMessages().get(0).getText().asString()!=Config.cfg.boundary_string ) {
						mc.inGameHud.addChatMessage(MessageType.CHAT, new LiteralText(Config.cfg.boundary_string).formatted(Config.cfg.boundary_formatting), Util.NIL_UUID);
					}
				} catch (Exception e) {log.error("An error occurred while joining a new session; caused by '{}':\n{}", e.getCause(), e.getLocalizedMessage());}
			});
		}

		log.info("Finished loading!");
	}
}