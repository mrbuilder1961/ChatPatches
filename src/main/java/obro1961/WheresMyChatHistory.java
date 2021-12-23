package obro1961;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.network.MessageType;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public class WheresMyChatHistory implements ClientModInitializer {
	public static final Logger log = LogManager.getLogger("Where's My Chat History");

	@Override
	public void onInitializeClient() {
		ClientPlayConnectionEvents.JOIN.register((nh, ps, mc) -> {
			try {
				String text = "<]===---{ SESSION BOUNDARY LINE }---===[>";
				if(mc.inGameHud.getChatHud().getMessageHistory().size()>0 && mc.inGameHud.getChatHud().getMessageHistory().get( mc.inGameHud.getChatHud().getMessageHistory().size()-1 )!=text) { // more than one message and last message isn't another boundary line
					mc.inGameHud.addChatMessage(MessageType.CHAT, Text.of(text), null);
					mc.inGameHud.getChatHud().addToMessageHistory(text);
				}
			} catch (Throwable t) {log.error("Something happened while joining a world/server; caused by '{}':\n{}", t.getCause(), t.getLocalizedMessage());}
		});

		log.info("Finished loading!");
	}
}