package obro1961;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.network.MessageType;
import net.minecraft.text.Text;
import obro1961.mixins.IChatHudAccessorMixin;

@Environment(EnvType.CLIENT)
public class WheresMyChatHistory implements ClientModInitializer {
	public static final Logger log = LogManager.getLogger("Where's My Chat History");

	@Override
	public void onInitializeClient() {
		ClientPlayConnectionEvents.JOIN.register((nh, ps, mc) -> {
			try {
				String text = "<]===---{ SESSION BOUNDARY LINE }---===[>";
				
				// adds the boundary line if there is more than one message and the last message isn't also a boundary line
				if( mc.inGameHud.getChatHud().getMessageHistory().size()>0 && ((IChatHudAccessorMixin)mc.inGameHud.getChatHud()).getMessages().get(0).getText().asString()!=text ) {
					mc.inGameHud.addChatMessage(MessageType.CHAT, Text.of(text), null);
				}
			} catch (Throwable t) {log.error("Something happened while joining a world/server; caused by '{}':\t{}", t.getCause(), t.getLocalizedMessage());}
		});

		log.info("Finished loading!");
	}
}