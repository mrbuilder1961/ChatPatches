package obro1961.chatpatches;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import obro1961.chatpatches.chatlog.ChatLog;
import obro1961.chatpatches.config.Config;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class ChatPatches implements ClientModInitializer {
	public static final String MOD_ID = "chatpatches";
	public static final Logger LOGGER = LoggerFactory.getLogger("Chat Patches");

	public static Config config = Config.initialize();

	public static Identifier id(String path) {
		// unfortunately this method in 1.20.6 is method_43902
		// but in 1.21 it's method_60655, making it incompatible
		// this is grinding my gears bc the code is identical ToT
		return Identifier.of(MOD_ID, path);
	}


	@Override
	public void onInitializeClient() {
		//stonecutter: arch api - put all these callbacks in another class for splitting by loader.. unless i can just use arch callbacks

		// -- chat log saving events --
		// according to my testing, this event works as needed when the game disconnects and on crashes if the game is functional at that point
		// testing details (server=hypixel): normal disconnects work on both world and server, manual F3+C crash works on world but NOT server
		// honestly I don't care if it fails on crashes, its fixable A) through the save interval or B) by fixing the crash's source
		ClientPlayConnectionEvents.DISCONNECT.register((network, client) -> ChatLog.serialize());
		ScreenEvents.AFTER_INIT.register((client, screen, sW, sH) -> ChatLog.saveIfPaused(screen));
		ClientTickEvents.END_WORLD_TICK.register(world -> ChatLog.tickSaveCounter());

		// -- chat log loader and boundary sender --
		ClientPlayConnectionEvents.JOIN.register((network, packetSender, client) -> {
			ChatLog.load(false);
			config.sendBoundaryLine();
		});

		LOGGER.info("[ChatPatches()] Finished setup!");
	}


	/**
	 * Logs an error-level message telling the user to report the given error. The
	 * class and method of the caller is acquired from a {@link StackWalker}.
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
		//prepub: test out walking back further if the error is thrown from a lambda/anon class, and keep going but then add a (lambda$12/33) or wtv it says to the class instead of just jargon
		//walker.walk(frames -> frames.dropWhile(s -> s.getDeclaringClass().getSimpleName().startsWith("lambda$"))).toList();
		if(method.isBlank())
			method = error.getStackTrace()[0].getMethodName();

		String message = String.format("[%s.%s] /!\\ Please report this error on GitHub or Discord with the full log file attached! /!\\", clazz, method);
		LOGGER.error(message, error);
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
		//prepub: test out walking back further if the error is thrown from a lambda/anon class, and keep going but then add a (lambda$12/33) or wtv it says to the class instead of just jargon
		//walker.walk(frames -> frames.dropWhile(s -> s.getDeclaringClass().getSimpleName().startsWith("lambda$"))).toList();
		String method = walker.walk(frames -> frames.skip(1).findFirst().orElseThrow().getMethodName());

		if(duration >= max) {
			LOGGER.warn("[{}.{}] Took {} seconds, but should've taken less than {}s. Consider reporting this to the GitHub or Discord", clazz, method, duration, max);
		} else {
			LOGGER.info("[{}.{}] Took {} seconds", clazz, method, duration);
		}
	}

	/**
	 * Submits the given task to an {@linkplain Util#getIoWorkerExecutor() I/O worker
	 * thread} for execution, and returns a {@link CompletableFuture} for managing
	 * the results. If the task's results are not needed, perhaps in cases where
	 * exceptions are managed by the task itself or for saving actions, the returned
	 * {@link CompletableFuture} may be safely ignored.
	 *
	 * @apiNote Methods such as {@link CompletableFuture#orTimeout(long, TimeUnit)},
	 * by nature, <b>will block</b>, and as such should <b>probably not be used</b>.
	 */
	@SuppressWarnings("UnusedReturnValue")
	public static CompletableFuture<Void> executeIoTask(Runnable task) {
		final ExecutorService IO_POOL = Util.getIoWorkerExecutor();
		return CompletableFuture.runAsync(task, IO_POOL).exceptionallyAsync(e -> {
			logReportMsg(e);
			return null;
		}, IO_POOL);
	}

	// 1.20.5+ needs the RegistryOps instance
	public static DynamicOps<JsonElement> jsonOps() {
		return JsonOps.INSTANCE;
	}
}