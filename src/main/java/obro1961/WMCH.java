package obro1961;

import java.util.Objects;

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
import obro1961.config.ClothConfig;
import obro1961.config.Config;
import obro1961.mixins.IChatHudAccessorMixin;

@Environment(EnvType.CLIENT)
public class WMCH implements ClientModInitializer {
	public static final Logger log = LogManager.getLogger("Where's My Chat History");
	public static boolean isConfigModded;
	public static Config config;

	@Override
	public void onInitializeClient() {
		FabricLoader fb = FabricLoader.getInstance();
		// updates the config status, if the versions are >= the minimum and are installed
		try {
			isConfigModded = fb.getModContainer("cloth-config").get().getMetadata().getVersion().compareTo(Version.parse("6.1.48"))>=0 && fb.getModContainer("modmenu").get().getMetadata().getVersion().compareTo(Version.parse("3.0.0"))>=0;
		} catch (Exception e) { isConfigModded = false; }

		if(isConfigModded) {
			config = new ClothConfig(true);
			ClothConfig.validateConfig();
		} else {
			config = new Config(true);
			Config.validateConfig();
		}

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

	/**
	 * Functions similarly to the JavaScript OR ({@code ||}) operator, which
	 * is a boolean operator but also returns the left side Object if truthy.
	 * If falsy, it returns the right side Object. This functions equivalently
	 * except it has a fallback to avoid {@code NullPointerExceptions}.
	 * <p>Recommended to cast to the output type if you know it, to avoid errors
	 * @param o1 An object (acts as the left Object)
	 * @param o2 Another object (acts as the right Object)
	 * @param fallback Returns if {@code o1} and {@code o2} evaluate to null. Replace with {@code o2} for the description of this method.
	 * @return {@code o1}, {@code o2}, or {@code fallback}.
	 * @see WMCH#or(Object, Object)
	 */
	public static Object or(Object o1, Object o2, Object fallback) {
		return Objects.nonNull(o1) ? o1 : Objects.nonNull(o2) ? o2 : fallback;
	}
	/** Same as {@link WMCH#or(Object, Object, Object)}, but passes o2 as both o2 and fallback. */
	public static Object or(Object o1, Object o2) { return or(o1, o2, o2); }
}