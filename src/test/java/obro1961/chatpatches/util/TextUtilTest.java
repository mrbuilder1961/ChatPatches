package obro1961.chatpatches.util;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.Message;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.crafting.CookingBookCategory;
import obro1961.chatpatches.Boundary;
import obro1961.chatpatches.RemoveTestPrefixGenerator;
import obro1961.chatpatches.TestData;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;

import static net.minecraft.network.chat.Component.literal;
import static net.minecraft.network.chat.Style.EMPTY;
import static obro1961.chatpatches.TestData.STR_AMP_RESET;
import static obro1961.chatpatches.TestData.STYLE_WHITE;
import static obro1961.chatpatches.util.TextUtil.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayNameGeneration(RemoveTestPrefixGenerator.class)
class TextUtilTest {
	/**
	 * @see TextUtil#asText(Object)
	 */
	@Test
	void testAsText() {
		// null -> empty()
		assertEquals(CommonComponents.EMPTY, TextUtil.asText(null));

		// generic Component -> MutableComponent

		// FormattedText -> literal(getString())
		FormattedText ft = FormattedText.of(TestData.STR_HELLO);
		assertEquals(literal(ft.getString()), TextUtil.asText(ft));

		// StringRepresentable -> literal(getSerializedName())
		StringRepresentable srInstance = CookingBookCategory.MISC;
		assertEquals(literal(srInstance.getSerializedName()), TextUtil.asText(srInstance));

		// Message -> literal(getString())
		Message m = new LiteralMessage(TestData.STR_WORD);
		assertEquals(literal(m.getString()), TextUtil.asText(m));

		// Object -> literal(toString())
		Boundary objectInstance = Boundary.fromInsertion(TestData.STR_BOUNDARY_ID_FULL);
		assertEquals(literal(objectInstance.toString()), TextUtil.asText(objectInstance));
	}

	/**
	 * @see TextUtil#truncate(Component, int)
	 */
	@Test //TODO: this task's results having any valid meaning entirely depends on whether testVirtuallyEqual() passed successfully - how do i specify this? /!\
	void testTruncate() {
		int max = 60;

		// truncating does not happen
		assertEquals(TestData.TEXT_SPACER, TextUtil.truncate(TestData.TEXT_SPACER, max));

		// use virtuallyEqual here on...
		var slcTrimmed = Component.literal(TestData.STR_SLC_EX.substring(0, TestData.STR_SLC_EX.indexOf("§ccodes")));
		assertTrue(virtuallyEqual(slcTrimmed, TextUtil.truncate(TestData.TEXT_SLC_EX, max)));
		assertTrue(virtuallyEqual(slcTrimmed, TextUtil.truncate(TestData.TEXT_SLC_EX_EXPLICIT, max)));
	}

	/**
	 * @see TextUtil#virtuallyEqual(Component, Component)
	 */
	@Test
	void testVirtuallyEqual() {
		// ensures the legacies equal explicits
		assertTrue(virtuallyEqual(TestData.TEXT_SLC_EX, TestData.TEXT_SLC_EX_EXPLICIT));
		assertTrue(virtuallyEqual(TestData.TEXT_NO_REDUNDANT_AMP, TestData.TEXT_NO_REDUNDANT_AMP_EXPLICIT));
		assertTrue(virtuallyEqual(TestData.TEXT_NEW_BUFF, TestData.TEXT_NEW_BUFF_EXPLICIT));
		assertTrue(virtuallyEqual(TestData.TEXT_WATCHDOG, TestData.TEXT_WATCHDOG_EXPLICIT));
		assertTrue(virtuallyEqual(TestData.TEXT_PRESSURE, TestData.TEXT_PRESSURE_EXPLICIT));
		assertTrue(virtuallyEqual(TestData.TEXT_SPACER, TestData.TEXT_SPACER_EXPLICIT));
		assertTrue(virtuallyEqual(TestData.TEXT_SPACER_OPTIMIZED, TestData.TEXT_SPACER_EXPLICIT));
		assertTrue(virtuallyEqual(TestData.TEXT_TRAILING, TestData.TEXT_TRAILING_EXPLICIT));
	}

	/**
	 * @see TextUtil#text(String)
	 */
	@Test // input2Text
	void testText() {
		// todo just test 2-3 regular testdata strings.
		//  but more importantly, make sure the backslashing actually escapes properly!
	}

	/**
	 * @see TextUtil#isBlank(Style)
	 */
	@Test
	void testIsBlank() {
		assertTrue(isBlank(EMPTY));
		assertTrue(isBlank(TextUtil.BLANK));
		assertTrue(isBlank(TestData.STYLE_INVISIBLE_ACTIONS));
		assertTrue(isBlank(STYLE_WHITE));
		assertTrue(isBlank(EMPTY.withInsertion("true").withColor(Colors.WHITE)));

		assertFalse(isBlank(TestData.STYLE_FULL));
		assertFalse(isBlank(TestData.STYLE_LIGHT_PURPLE));
		assertFalse(isBlank(TestData.STYLE_DARK_AQUA));
		assertFalse(isBlank(TestData.STYLE_LUSH_GREEN));
		assertFalse(isBlank(TestData.STYLE_LAVENDER));
		assertFalse(isBlank(TestData.STYLE_BOLD_DARK_RED));
		assertFalse(isBlank(TestData.STYLE_BOLD_GREEN));
		assertFalse(isBlank(TestData.STYLE_BOLD_DARK_PURPLE));
		assertFalse(isBlank(TestData.STYLE_BOLD_ITALIC_GOLD));
		assertFalse(isBlank(TestData.STYLE_BOLD_BLUE_HOVER));
		assertFalse(isBlank(TestData.STYLE_BOLD_UNDERLINE_LAVENDER));
		assertFalse(isBlank(TestData.STYLE_ITALIC));
		assertFalse(isBlank(TestData.STYLE_ITALIC_GRAY));
		assertFalse(isBlank(TestData.STYLE_UNDERLINE_RED));
		assertFalse(isBlank(TestData.STYLE_YELLOW_INSERTION));
		assertFalse(isBlank(TestData.STYLE_PURPLE_CLICK));
	}

	@Test
	void testUniformFlatList() {
		// todo this at some point in the (hopefully existent) future.
		//  icl i might have to resort to ethical AI :( this just takes SOOOO much time to get off the ground,
		//  and then 99% of the remaining time is rewriting whole functions for hours to fix edge cases.
		//  i'm okay with that last bit but both is just unrealistic, and i really need this framework
		//  for this mod at its current scale. /!\
	}

	// note that when pretty printing is enabled, we strip actual formatting codes
	// (the ones added to accentuate the underlying amp codes)
	private static String empiricalLegacyString(Component text, boolean prettyPrint) {
		String result = TextUtil.toLegacyString(text, prettyPrint);
		return prettyPrint ? ChatFormatting.stripFormatting(result) : result;
	}

	void toLegacyString_ensurePrettyPrintHasNoSideEffects() {
		String newBuffEmpirical = empiricalLegacyString(TestData.TEXT_NEW_BUFF, false);
		String newBuffPrettyEmpirical = empiricalLegacyString(TestData.TEXT_NEW_BUFF, true);
		// verifies that prettyPrint does not affect the core return value
		assertEquals(newBuffEmpirical, newBuffPrettyEmpirical);
		// verifies that the non-pretty converted codes are equal
		assertEquals(TestData.STR_NEW_BUFF_AMP, newBuffEmpirical);
		// if the last two assertions did not fail, we know this must also be true (transitive property: x == y, y == z; -> x == z)
		//assertEquals(TestData.STR_NEW_BUFF_AMP, newBuffPrettyEmpirical);


		String watchdogEmpirical = empiricalLegacyString(TestData.TEXT_WATCHDOG_EXPLICIT, false);
		String watchdogPrettyEmpirical = empiricalLegacyString(TestData.TEXT_WATCHDOG_EXPLICIT, true);
		assertEquals(watchdogEmpirical, watchdogPrettyEmpirical);
		assertEquals(TestData.STR_WATCHDOG_AMP, watchdogEmpirical);


		String pressureEmpirical = empiricalLegacyString(TestData.TEXT_PRESSURE, false);
		String pressurePrettyEmpirical = empiricalLegacyString(TestData.TEXT_PRESSURE, true);
		assertEquals(pressureEmpirical, pressurePrettyEmpirical);
		assertEquals(TestData.STR_PRESSURE_AMP, pressureEmpirical);


		String spacerEmpirical = empiricalLegacyString(TestData.TEXT_SPACER_OPTIMIZED, false);
		String spacerPrettyEmpirical = empiricalLegacyString(TestData.TEXT_SPACER_OPTIMIZED, true);
		assertEquals(spacerEmpirical, spacerPrettyEmpirical);
		assertNotEquals(TestData.STR_SPACER_AMP, spacerEmpirical); // sanity check: there should NOT be ANY amp codes here
		assertEquals(TestData.STR_SPACER, spacerEmpirical);


		String trailingEmpirical = empiricalLegacyString(TestData.TEXT_TRAILING_EXPLICIT, false);
		String trailingPrettyEmpirical = empiricalLegacyString(TestData.TEXT_TRAILING_EXPLICIT, true);
		assertEquals(trailingEmpirical, trailingPrettyEmpirical);
		assertEquals(TestData.STR_TRAILER_AMP, trailingEmpirical);
	}

	/**
	 * Ensures section sign strings convert to the same sequences as their
	 * explicitly-constructed counterparts.
	 */
	void toLegacyString_ensureSlcsEqExplicits() {
		String slc = empiricalLegacyString(TestData.TEXT_SLC_EX, false);
		String slcExplicit = empiricalLegacyString(TestData.TEXT_SLC_EX_EXPLICIT, false);
		assertEquals(TestData.STR_SLC_EX_AMP, slc); // URGENT: currently failing where `&r&e` should be is returning `&e` only! see #uniformFlatList()
		assertEquals(TestData.STR_SLC_EX_AMP, slcExplicit);

		String newBuff = empiricalLegacyString(TestData.TEXT_NEW_BUFF, false);
		String newBuffExplicit = empiricalLegacyString(TestData.TEXT_NEW_BUFF_EXPLICIT, false);
		assertEquals(TestData.STR_NEW_BUFF_AMP, newBuff); // verifies that the text-to-legacy conversion worked properly
		assertEquals(TestData.STR_NEW_BUFF_AMP, newBuffExplicit); // verifies that the explicit component construction was converted properly

		String watchdog = empiricalLegacyString(TestData.TEXT_WATCHDOG, false);
		String watchdogExplicit = empiricalLegacyString(TestData.TEXT_WATCHDOG_EXPLICIT, false);
		assertEquals(TestData.STR_WATCHDOG_AMP, watchdog);
		assertEquals(TestData.STR_WATCHDOG_AMP, watchdogExplicit);

		String pressure = empiricalLegacyString(TestData.TEXT_PRESSURE, false);
		String pressureExplicit = empiricalLegacyString(TestData.TEXT_PRESSURE_EXPLICIT, false);
		assertEquals(TestData.STR_PRESSURE_AMP, pressure);
		assertEquals(TestData.STR_PRESSURE_AMP, pressureExplicit);

		//!!!
		//TODO test more legacy conversions, specifically with and without prefixed substrings missing formatting codes

		String spacer = empiricalLegacyString(TestData.TEXT_SPACER, false);
		String spacerOptimized = empiricalLegacyString(TestData.TEXT_SPACER_OPTIMIZED, false);
		String spacerExplicit = empiricalLegacyString(TestData.TEXT_SPACER_EXPLICIT, false);
		// sanity checks: there should NOT be ANY amp codes here
		assertNotEquals(TestData.STR_SPACER_AMP, spacer);
		assertNotEquals(TestData.STR_SPACER_AMP, spacerOptimized);
		assertNotEquals(TestData.STR_SPACER_AMP, spacerExplicit);

		assertEquals(TestData.STR_SPACER, spacer);
		assertEquals(TestData.STR_SPACER, spacerOptimized);
		assertEquals(TestData.STR_SPACER, spacerExplicit);

		String trailing = empiricalLegacyString(TestData.TEXT_TRAILING, false);
		String trailingExplicit = empiricalLegacyString(TestData.TEXT_TRAILING_EXPLICIT, false);
		assertEquals(TestData.STR_TRAILER_AMP, trailing);
		assertEquals(TestData.STR_TRAILER_AMP, trailingExplicit);
	}

	// todo share same instances (ex. non-pretty empirical legacy strings)
		// also note that some conversions can return false but actually be equivalent or optimized (ex. ` &a ` == `  &a` but the latter is preferred)
	@Test
	void testToLegacyString() {
		toLegacyString_ensurePrettyPrintHasNoSideEffects();
		toLegacyString_ensureSlcsEqExplicits();
	}

	/**
	 * Ensures {@code getFormattingCodes(EMPTY, *)} returns an empty string or
	 * {@value TestData#STR_AMP_RESET}.
	 */
	void getFormattingCodes_ensureStyle2EmptyEqEmpty() {
		assertEquals("", getFormattingCodes(EMPTY, TestData.STYLE_BLANK)); // BLANK is effectively empty; no change so no codes needed
		assertEquals("", getFormattingCodes(EMPTY, TestData.STYLE_INVISIBLE_ACTIONS)); // nothing in this one is visible; so no code are needed

		assertEquals(STR_AMP_RESET, getFormattingCodes(EMPTY, TestData.STYLE_FULL));
		assertEquals(STR_AMP_RESET, getFormattingCodes(EMPTY, TestData.STYLE_LIGHT_PURPLE));
		assertEquals(STR_AMP_RESET, getFormattingCodes(EMPTY, TestData.STYLE_DARK_AQUA));
		assertEquals(STR_AMP_RESET, getFormattingCodes(EMPTY, TestData.STYLE_LUSH_GREEN));
		assertEquals(STR_AMP_RESET, getFormattingCodes(EMPTY, TestData.STYLE_LAVENDER));
		assertEquals(STR_AMP_RESET, getFormattingCodes(EMPTY, TestData.STYLE_BOLD_GREEN));
		assertEquals(STR_AMP_RESET, getFormattingCodes(EMPTY, TestData.STYLE_BOLD_ITALIC_GOLD));
		assertEquals(STR_AMP_RESET, getFormattingCodes(EMPTY, TestData.STYLE_BOLD_BLUE_HOVER));
		assertEquals(STR_AMP_RESET, getFormattingCodes(EMPTY, TestData.STYLE_BOLD_UNDERLINE_LAVENDER));
		assertEquals(STR_AMP_RESET, getFormattingCodes(EMPTY, TestData.STYLE_ITALIC_GRAY));
		assertEquals(STR_AMP_RESET, getFormattingCodes(EMPTY, TestData.STYLE_UNDERLINE_RED));
		assertEquals(STR_AMP_RESET, getFormattingCodes(EMPTY, TestData.STYLE_YELLOW_INSERTION));
		assertEquals(STR_AMP_RESET, getFormattingCodes(EMPTY, TestData.STYLE_PURPLE_CLICK));
	}

	/**
	 * Ensures {@code getFormattingCodes(*, EMPTY)} returns the style's codes.
	 */
	void getFormattingCodes_ensureEmpty2StyleEqStyle() {
		// empty lasts: should just return the first parameter converted
		assertEquals("", getFormattingCodes(TestData.STYLE_BLANK, EMPTY)); // BLANK is effectively empty; no change so no codes needed
		assertEquals("", getFormattingCodes(TestData.STYLE_INVISIBLE_ACTIONS, EMPTY)); // nothing in this one is visible; so no code are needed

		assertEquals("&l&o&n&m&k", getFormattingCodes(TestData.STYLE_FULL, EMPTY));
		assertEquals("&d", getFormattingCodes(TestData.STYLE_LIGHT_PURPLE, EMPTY));
		assertEquals("&3", getFormattingCodes(TestData.STYLE_DARK_AQUA, EMPTY));
		assertEquals("&#0D8815", getFormattingCodes(TestData.STYLE_LUSH_GREEN, EMPTY));
		assertEquals("&#B783E8", getFormattingCodes(TestData.STYLE_LAVENDER, EMPTY));
		assertEquals("&a&l", getFormattingCodes(TestData.STYLE_BOLD_GREEN, EMPTY));
		assertEquals("&6&l&o", getFormattingCodes(TestData.STYLE_BOLD_ITALIC_GOLD, EMPTY));
		assertEquals("&9&l", getFormattingCodes(TestData.STYLE_BOLD_BLUE_HOVER, EMPTY));
		assertEquals("&#B783E8&l&n", getFormattingCodes(TestData.STYLE_BOLD_UNDERLINE_LAVENDER, EMPTY));
		assertEquals("&7&o", getFormattingCodes(TestData.STYLE_ITALIC_GRAY, EMPTY));
		assertEquals("&c&n", getFormattingCodes(TestData.STYLE_UNDERLINE_RED, EMPTY));
		assertEquals("&e", getFormattingCodes(TestData.STYLE_YELLOW_INSERTION, EMPTY));
		assertEquals("&5", getFormattingCodes(TestData.STYLE_PURPLE_CLICK, EMPTY));
	}

	/**
	 * @see TextUtil#getFormattingCodes(Style, Style)
	 */
	@Test
	void testGetFormattingCodes() {
		assertEquals("", getFormattingCodes(EMPTY, EMPTY));

		getFormattingCodes_ensureStyle2EmptyEqEmpty();
		getFormattingCodes_ensureEmpty2StyleEqStyle();

		// ensure S <- S returns ""
		assertEquals("", getFormattingCodes(TestData.STYLE_LAVENDER, TestData.STYLE_LAVENDER));
		assertEquals("", getFormattingCodes(TestData.STYLE_ITALIC, TestData.STYLE_ITALIC));
		assertEquals("", getFormattingCodes(TestData.STYLE_BOLD_UNDERLINE_LAVENDER, TestData.STYLE_BOLD_UNDERLINE_LAVENDER));

		// ensure we don't get any &r&f
		assertEquals("", getFormattingCodes(STYLE_WHITE, EMPTY));
		assertEquals("", getFormattingCodes(EMPTY, STYLE_WHITE));
		assertEquals("&r", getFormattingCodes(STYLE_WHITE, TestData.STYLE_BOLD_DARK_RED));

		// color <- color (new code)
		assertEquals("&3", getFormattingCodes(TestData.STYLE_DARK_AQUA, TestData.STYLE_LUSH_GREEN));
		assertEquals("&#0D8815", getFormattingCodes(TestData.STYLE_LUSH_GREEN, TestData.STYLE_DARK_AQUA));
		assertEquals("&#0D8815", getFormattingCodes(TestData.STYLE_LUSH_GREEN, TestData.STYLE_LAVENDER));
		assertEquals("&#B783E8", getFormattingCodes(TestData.STYLE_LAVENDER, TestData.STYLE_LIGHT_PURPLE));
		assertEquals("&d", getFormattingCodes(TestData.STYLE_LIGHT_PURPLE, TestData.STYLE_DARK_AQUA));

		// color <- modifier (reset, new code)
		assertEquals("&r&3", getFormattingCodes(TestData.STYLE_DARK_AQUA, TestData.STYLE_BOLD_BLUE_HOVER));
		assertEquals("&r&#0D8815", getFormattingCodes(TestData.STYLE_LUSH_GREEN, TestData.STYLE_ITALIC_GRAY));
		assertEquals("&r&#0D8815", getFormattingCodes(TestData.STYLE_LUSH_GREEN, TestData.STYLE_UNDERLINE_RED));
		assertEquals("&r&#B783E8", getFormattingCodes(TestData.STYLE_LAVENDER, TestData.STYLE_BOLD_UNDERLINE_LAVENDER)); //!!! should be solved tho
		assertEquals("&r&d", getFormattingCodes(TestData.STYLE_LIGHT_PURPLE, TestData.STYLE_BOLD_ITALIC_GOLD));

		// modifier <- color (reset/white, new code)
		assertEquals("&9&l", getFormattingCodes(TestData.STYLE_BOLD_BLUE_HOVER, TestData.STYLE_LUSH_GREEN));
		assertEquals("&7&o", getFormattingCodes(TestData.STYLE_ITALIC_GRAY, TestData.STYLE_DARK_AQUA));
		assertEquals("&c&n", getFormattingCodes(TestData.STYLE_UNDERLINE_RED, TestData.STYLE_LAVENDER));
		assertEquals("&#B783E8&l&n", getFormattingCodes(TestData.STYLE_BOLD_UNDERLINE_LAVENDER, TestData.STYLE_LIGHT_PURPLE));
		assertEquals("&6&l&o", getFormattingCodes(TestData.STYLE_BOLD_ITALIC_GOLD, TestData.STYLE_DARK_AQUA));

		// modifier <- modifier (reset, new code)
		assertEquals("&r&9&l", getFormattingCodes(TestData.STYLE_BOLD_BLUE_HOVER, TestData.STYLE_BOLD_UNDERLINE_LAVENDER));
		assertEquals("&r&7&o", getFormattingCodes(TestData.STYLE_ITALIC_GRAY, TestData.STYLE_UNDERLINE_RED));
		assertEquals("&r&c&n", getFormattingCodes(TestData.STYLE_UNDERLINE_RED, TestData.STYLE_ITALIC_GRAY));
		assertEquals("&5", getFormattingCodes(TestData.STYLE_BOLD_DARK_PURPLE, TestData.STYLE_BOLD_GREEN));
		assertEquals("&r&#B783E8&l&n", getFormattingCodes(TestData.STYLE_BOLD_UNDERLINE_LAVENDER, TestData.STYLE_BOLD_ITALIC_GOLD));
	}
}