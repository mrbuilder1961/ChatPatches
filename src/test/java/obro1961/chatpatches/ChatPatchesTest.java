//~ yarnification
package obro1961.chatpatches;

import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static obro1961.chatpatches.ChatPatches.*;
import static org.junit.jupiter.api.Assertions.*;

public class ChatPatchesTest {
	@BeforeEach
	void setUp() {
		Bootstrap.bootStrap();
		SharedConstants.tryDetectVersion();
	}

	@Test
	void testId() {
		var valid = Identifier.tryBuild(MOD_ID, TestData.STR_ID);
		var validEmpirical = ChatPatches.id(TestData.STR_ID);
		assertEquals(valid, validEmpirical);

		var invalid = Identifier.tryBuild(MOD_ID, TestData.STR_INVALID_ID);
		var invalidEmpirical = ChatPatches.id(TestData.STR_INVALID_ID);
		assertNull(invalid);
		assertNull(invalidEmpirical);

		var wordId = Identifier.tryBuild(MOD_ID, TestData.STR_WORD);
		var wordIdEmpirical = ChatPatches.id(TestData.STR_WORD);
		assertEquals(wordId, wordIdEmpirical);
	}

	@Test
	void getBracedCaller() {
		assertEquals("[ChatPatchesTest.getBracedCaller]", ChatPatches.getBracedCaller(false));
		assertEquals("[ReflectionUtils.invokeMethod]", ChatPatches.getBracedCaller(true)); // fixme call from elsewhere? idk if invokeMethod is consistent across versions and jvm impls

		String expectedBracedLambda = "[ChatPatchesTest.getBracedCaller(->0)]"; // ensure correct index of lambda
		String[] empiricalBracedLambda = { "error" };
		// used purely for the lambda; there's probably a better way to do this
		assertDoesNotThrow(() -> empiricalBracedLambda[0] = ChatPatches.getBracedCaller(false));
		assertTrue(Math.abs(expectedBracedLambda.compareTo(empiricalBracedLambda[0])) <= 1, "String may be at most one char diff (wrong method or lambda)");
	}

	/*@Test
	void pushErrorToast() {
	}

	@Test
	void pushInfoToast() {
	}*/

	// todo requires the GameTest framework for a Minecraft instance to exist /!\
	/*@Test
	void regBack() {
		context.runOnClient(mc -> {
			// level must exist for the test to be valid
			// and if they're diff instances then we could be dealing with different levels
			// (i'm aware this shouldn't happen but the keyword there is *should* n't)
			if(mc.level != null && mc.equals(Minecraft.getInstance())) {
				var json = JsonOps.INSTANCE;
				var nbt = NbtOps.INSTANCE;

				var empiricalJson = ChatPatches.regBack(json);
				var empiricalNbt = ChatPatches.regBack(nbt);

				// invert because if they're equal it failed and nothing happened
				// type inspection is false bc fallback is casting the parameter and simply returning it
				assertNotEquals(json, empiricalJson);
				assertNotEquals(nbt, empiricalNbt);
			} else {
				LOGGER.warn("Client level doesn't exist, cannot test `ChatPatches.regBack`");
			}
		});
	}*/
}