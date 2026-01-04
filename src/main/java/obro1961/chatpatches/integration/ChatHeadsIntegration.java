//? if >=1.21.9 {
package obro1961.chatpatches.integration;

import com.mojang.authlib.GameProfile;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.ObjectContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.contents.objects.PlayerSprite;
import obro1961.chatpatches.util.ChatUtil;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

import static obro1961.chatpatches.ChatPatches.LOGGER;

public class ChatHeadsIntegration {
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

	public static Optional<MutableComponent> extractHeadComponent(Component message) {
			if(message.getContents() instanceof ObjectContents(PlayerSprite ignored)) {
				return Optional.of((MutableComponent) message);
			}

			if(message.getContents() instanceof TranslatableContents translatable) {
				for(var arg : translatable.getArgs()) {
					if(arg instanceof Component c) {
						var head = extractHeadComponent(c);
						if(head.isPresent()) {
							return head;
						}
					}
				}
			}

			for(var sibling : message.getSiblings()) {
				var head = extractHeadComponent(sibling);
				if(head.isPresent()) {
					return head;
				}
			}

			return Optional.empty();
	}

	// not used atm - hopefully the above method is robust enough and works well for a while
	public static MutableComponent createChatHeadComponent(GameProfile player) {
		MutableComponent result = Component.empty();

		if(enabled && usingBeforeName() && !ChatUtil.NIL_SENDER.equals(player)) {
			try {
				PlayerInfo playerInfo = new PlayerInfo(player, false);

				var packetListener = Minecraft.getInstance().getConnection();
				// returns null if the id does not map to a real player
				if(packetListener.getPlayerInfo(player.id()) instanceof PlayerInfo p) {
					playerInfo = p;
				} else if(packetListener.getPlayerInfo(player.name()) instanceof PlayerInfo p) {
					playerInfo = p;
				}

				result = (MutableComponent) createChatHeadComponent.invoke(null, playerInfo);
			} catch(IllegalAccessException | InvocationTargetException e) {
				LOGGER.error("Failed to create a chat head component:", e);
				result = Component.empty();
			}
		}

		return result;
	}
}
//?}