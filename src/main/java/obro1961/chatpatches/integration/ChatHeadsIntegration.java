//? if >=1.21.9 {
package obro1961.chatpatches.integration;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.ObjectContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.contents.objects.PlayerSprite;
import obro1961.chatpatches.mixin.gui.ChatComponentMixin;
import org.spongepowered.asm.mixin.injection.points.MethodHead;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

import static obro1961.chatpatches.ChatPatches.LOGGER;

public class ChatHeadsIntegration {
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

		Method m1 = null; Field f1 = null; // java sometimes i really hate you.
		if(installed()) {
			try {
				f1 = Class.forName(CLASS_CHAT_HEADS).getDeclaredField(FIELD_CONFIG);
				m1 = Class.forName(CLASS_CHAT_HEADS_CONFIG).getDeclaredMethod(METHOD_RENDER_POSITION);
			} catch(ClassNotFoundException | NoSuchMethodException | NoSuchFieldException e) {
				LOGGER.error("Failed to reflectively access Chat Heads' data, disabling further interoperability:", e);
				enabled = false;
			}
		}

		CONFIG = f1;
		renderPosition = m1;
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

	// commented out while unused
	/*public static boolean usingBeforeName() {
		if(enabled) {
			try {
				// ChatHeads.CONFIG is static, but ConfigData.renderPosition is not
				return renderPosition.invoke( CONFIG.get(null) ).toString().equalsIgnoreCase("BEFORE_NAME");
			} catch(IllegalAccessException | InvocationTargetException e) {
				LOGGER.error("Failed to access Chat Heads' renderPosition config field:", e);
			}
		}

		return false; // assumes not installed or broken - aka do nothing
	}*/

	/**
	 * Custom method created by one of the authors of Chat Heads!
	 * @author <a href="https://github.com/Fourmisain">Fourmisain</a>!
	 *
	 * @return An {@link Optional} enclosing the component containing the head
	 * object, or an empty Optional if none was found.
	 *
	 * @param message The Component in which to look for a head component aka
	 * {@link PlayerSprite} object. At the time of writing, Chat Heads adds such
	 * a head after {@link MethodHead} of addMessage(...) but before the
	 * {@link ChatComponentMixin#modifyMessage(Component)} injector is called.
	 * Servers can add such head as they wish too - whether Chat Patches should
	 * handle that is up for debate, but if the format complies with this system,
	 * it should work fine.
	 *
	 * @see <a href="https://github.com/dzwdz/chat_heads/issues/83">ChatHeads#83</a>
	 * and <a href="https://github.com/mrbuilder1961/ChatPatches/issues/285">#285</a>.
	 */
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
}
//?}