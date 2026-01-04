package obro1961.chatpatches.integration;

import com.mojang.authlib.GameProfile;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static obro1961.chatpatches.ChatPatches.LOGGER;

public final class ChatHeadsIntegration {
//? if >=1.21.9 {
	private static final String CLASS_COMPONENT_PROCESSOR = "dzwdz.chat_heads.ComponentProcessor";
	private static final String METHOD_CREATE_CHAT_HEAD_COMPONENT = "createChatHeadComponent";
	/**
	 * (MutableComponent) ComponentProcessor.createChatHeadComponent(PlayerInfo)
	 */
	private static final Method createChatHeadComponent;

	private static final String CLASS_CHAT_HEADS = "dzwdz.chat_heads.ChatHeads";
	private static final String FIELD_CONFIG = "CONFIG";
	/**
	 * (ChatHeadsConfig) ChatHeads.CONFIG
	 */
	private static final Field CONFIG;

	private static final String CLASS_CHAT_HEADS_CONFIG = "dzwdz.chat_heads.config.ChatHeadsConfig";
	private static final String METHOD_RENDER_POSITION = "renderPosition";
	/**
	 * (RenderPosition) ChatHeadsConfig.renderPosition()
	 */
	private static final Method renderPosition;

	static {
		enabled = true;

		Method m1 = null, m2 = null; Field f1 = null; // java sometimes i really hate you.
		if(installed()) {
			try {
				m1 = Class.forName(CLASS_COMPONENT_PROCESSOR).getDeclaredMethod(METHOD_CREATE_CHAT_HEAD_COMPONENT, PlayerInfo.class);
				f1 = Class.forName(CLASS_CHAT_HEADS).getDeclaredField(FIELD_CONFIG);
				m2 = Class.forName(CLASS_CHAT_HEADS_CONFIG).getDeclaredMethod(METHOD_RENDER_POSITION);
			} catch(ClassNotFoundException | NoSuchMethodException | NoSuchFieldException e) {
				LOGGER.error("Failed to reflectively access Chat Heads' data, disabling further interoperability:", e);
				enabled = false;
			}
		}

		createChatHeadComponent = m1;
		CONFIG = f1;
		renderPosition = m2;
	}

	private static boolean enabled;


	public static boolean installed() {
		boolean installed = FabricLoader.getInstance().isModLoaded("chat_heads");
		if(enabled && !installed) {
			LOGGER.info("Chat Heads not installed, disabling interoperability");
			enabled = false;
		}
		return installed;
	}

	public static MutableComponent createChatHeadComponent(GameProfile player) {
		MutableComponent result = Component.empty();

		if(enabled && usingBeforeName()) {
			try {
				PlayerInfo playerInfo = new PlayerInfo(player, false);
				result = (MutableComponent) createChatHeadComponent.invoke(null, playerInfo);
			} catch(IllegalAccessException | InvocationTargetException e) {
				LOGGER.error("Failed to create a chat head component:", e);
				result = Component.empty();
			}
		}

		return result;
	}

	public static boolean usingBeforeName() {
		if(enabled) {
			try {
				// ChatHeads.CONFIG is static, but ConfigData.renderPosition is not
				return renderPosition.invoke( CONFIG.get(null) ).toString().equalsIgnoreCase("BEFORE_NAME");
			} catch(IllegalAccessException | InvocationTargetException e) {
				LOGGER.error("Failed to access Chat Heads' renderPosition config field:", e);
			}
		}

		return false; // assumes not installed or broken - aka do nothing
	}
//?}
}