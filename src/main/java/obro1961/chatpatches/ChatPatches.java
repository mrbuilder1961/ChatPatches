package obro1961.chatpatches;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import obro1961.chatpatches.chatlog.ChatLog;
import obro1961.chatpatches.config.Config;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatPatches implements ClientModInitializer {
	public static final String MOD_ID = "chatpatches";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static boolean usingUnsafeCodec = false;
	public static Config config = Config.create();

	/**
	 * Returns a {@code chatpatches:${path}}
	 * {@link Identifier}.
	 */
	public static Identifier id(String path) {
		// unfortunately this method in 1.20.6 is method_43902
		// but in 1.21 it's method_60655, making it incompatible
		// this is grinding my gears bc the code is identical ToT
		return Identifier.of(MOD_ID, path);
	}


	@Override
	public void onInitializeClient() {
		// -- chat log saving events --
		// according to my testing, this event works as needed when the game disconnects and on crashes if the game is functional at that point
		// testing details (server=hypixel): normal disconnects work on both world and server, manual F3+C crash works on world but NOT server
		// honestly I don't care if it fails on crashes, its fixable a) through the save interval or b) by fixing the crash's source
		ClientPlayConnectionEvents.DISCONNECT.register((network, client) -> ChatLog.serialize());
		ScreenEvents.AFTER_INIT.register((client, screen, sW, sH) -> ChatLog.saveIfPaused(screen));
		ClientTickEvents.END_WORLD_TICK.register(world -> ChatLog.tickSaveCounter());

		// -- chat log loader and boundary sender --
		ClientPlayConnectionEvents.JOIN.register((network, packetSender, client) -> {
			ChatLog.load(false);
			config.sendBoundaryLine();
			ChatLog.hideRecentMessages();
		});

		// Command Event
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(ClientCommandManager.literal("chatpatches")
                        .executes(context -> {
                                    MinecraftClient client = MinecraftClient.getInstance();
                                    client.send(() -> client.setScreen(ChatPatches.config.getConfigScreen(null)));
                                    return 1;
                                }
                        )
                )
		);

		LOGGER.info("[ChatPatches()] Finished setting up!");
	}


	/**
	 * Logs an error-level message telling the user to report
	 * the given error. The class and method of the caller is
	 * provided from a {@link StackWalker}.
	 *
	 * <p>Outputs the following message:
	 * <pre>
	 * [$class.$method] /!\ Please report this error on GitHub or Discord with the full log file attached! /!\
	 * $error
	 * </pre>
	 */
	public static void logReportMsg(@NotNull Throwable error) {
		StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
		String clazz = walker.getCallerClass().getSimpleName();
		String method = walker.walk(frames -> frames.skip(1).findFirst().orElseThrow().getMethodName());

		if(method.isBlank())
			method = error.getStackTrace()[0].getMethodName();

		//noinspection StringConcatenationArgumentToLogCall: it's whining but it's totally fine
		LOGGER.error("[" + clazz + "." + method + "] /!\\ Please report this error on GitHub or Discord with the full log file attached! /!\\", error);
	}

	/**
	 * Executes {@link #logReportMsg(Throwable)}
	 * and throws the passed error.
	 */
	public static <X extends Throwable> X logAndThrowReportMsg(@NotNull X error) throws X {
		logReportMsg(error);
		throw error;
	}

	/**
	 * Logs how long the caller took to execute, according to {@code start} and the
	 * {@linkplain System#currentTimeMillis() current time}. If the duration is less
	 * than the given {@code threshold}, an info message is logged. Otherwise, logs a
	 * warning message noting the excessive duration and suggesting the user report
	 * it. In both cases the duration is converted to a {@code double} and logged in
	 * seconds.
	 *
	 * @param start The time, in milliseconds, when the caller started execution.
	 * Should be a previously recorded value from {@link System#currentTimeMillis()}.
	 * @param threshold The maximum duration, in milliseconds, that the caller should
	 * take to execute.
	 */
	public static void logDuration(long start, long threshold) {
		// store and convert to seconds
		double duration = (double) (System.currentTimeMillis() - start) / 1000;
		double max = (double) threshold / 1000;

		StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
		String clazz = walker.getCallerClass().getSimpleName();
		String method = walker.walk(frames -> frames.skip(1).findFirst().orElseThrow().getMethodName());

		if(duration >= max) {
			LOGGER.warn("[{}.{}] Took {} seconds, but should've taken less than {}s. Consider reporting this to the GitHub or Discord", clazz, method, duration, max);
		} else {
			LOGGER.info("[{}.{}] Took {} seconds", clazz, method, duration);
		}
	}

	/**
	 * Returns a {@link JsonOps#INSTANCE} wrapped by a {@link DynamicRegistryManager.Immutable}
	 * (provided by the ClientWorld) to not throw crashes when using {@link Codec}s.
	 *
	 * <p>Fixes <a href="https://github.com/mrbuilder1961/ChatPatches/issues/180">#180</a>.
	 * Thanks to
	 * <a href="https://discord.com/channels/507304429255393322/721100785936760876/1278519812628156528">arkosammy12</a>
	 * for help on the Fabric Discord!
	 *
	 * @since 1.20.5 introduced the necessity
	 * of wrapping with the {@link RegistryWrapper.WrapperLookup}
	 */
	public static RegistryOps<JsonElement> jsonOps() throws NullPointerException {
		if(MinecraftClient.getInstance().world instanceof ClientWorld world)
			return world.getRegistryManager().getOps(JsonOps.INSTANCE);
		else
			throw logAndThrowReportMsg(new NullPointerException("[ChatPatches#jsonOps] Expected existing ClientWorld"));
	}
}