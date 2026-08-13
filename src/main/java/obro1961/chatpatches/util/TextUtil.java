package obro1961.chatpatches.util;

import com.mojang.brigadier.Message;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.util.StringRepresentable;
import obro1961.chatpatches.ChatLog;
import obro1961.chatpatches.mixin.security.ClickEvent$ActionMixin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.minecraft.network.chat.Style.EMPTY;
import static obro1961.chatpatches.ChatPatches.LOGGER;

/**
 * A class containing various string and {@link Component} related utilities.
 */
public class TextUtil {
	/** @see #isBlank(Style) */
	public static final Style BLANK = EMPTY.withBold(false).withItalic(false).withUnderlined(false).withObfuscated(false).withStrikethrough(false);
	/**
	 * @see ChatFormatting#PREFIX_CODE
	 */
	public static final Matcher AMPERSAND_REGEX = Pattern.compile("(?m)&([\\da-fk-or])").matcher("");
	/**
	 * {@link #AMPERSAND_REGEX} that explicitly does not match any
	 * formatting codes following backslashes
	 */
	public static final Matcher NO_BACKSLASH_AMPERSAND_REGEX = Pattern.compile("(?m)(?<!\\\\)&([\\da-fk-or])").matcher("");
	/**
	 * Matches two ampersand color codes that directly follow one another. Will
	 * also match if there's any whitespace in between them. This is used to
	 * simplify output strings, as in these cases the first color code is entirely
	 * useless. When replacing, {@code $1} becomes the captured whitespace, if any,
	 * and {@code $2} becomes the effective color code (not including the ampersand).
	 *
	 * @see <a href="https://regex101.com/r/D9x2yv/latest">Examples</a>
	 */
	public static final Matcher REDUNDANT_COLOR_REGEX = Pattern.compile("(?m)&(?:#[\\da-f]{6}|[\\da-f])(\\s*)&(#[\\da-f]{6}|[\\da-f])").matcher("");

	/**
	 * Matches any ampersand formatting codes, whether regular (legacy) or hex,
	 * for use with the pretty-printer in {@link #toLegacyString(Component, boolean)}.
	 * Captures all formatting code(s) present in a string. Note that if any are
	 * back-to-back, they will be captured together as one string.
	 *
	 * @see <a href="https://regex101.com/r/m0AM81/latest">Examples</a>
	 */
	public static final Matcher PRETTY_PRINT_TARGETS_REGEX = Pattern.compile("(?im)(?<!\\\\)(&(?:#[\\da-f]{6}|[\\da-fk-or]))+").matcher("");


	/**
	 * A wrapped {@link Codec} for {@link Component} objects that will not
	 * throw an exception when serializing click events with unsafe
	 * ({@link ClickEvent.Action#OPEN_FILE}) actions. Done by disabling the
	 * {@linkplain #safeCodec safety serialization check} that is used by
	 * {@link ClickEvent$ActionMixin} to temporarily allow all
	 * messages to be serialized.
	 *
	 * @see ClickEvent$ActionMixin#allowConditionalSerialization(boolean)
	 * @see ChatLog#CODEC
	 */
	public static final Codec<Component> UNSAFE_CODEC = new Codec<>() {
		/*~ if <=1.20.2 'ComponentSerialization.CODEC' -> 'net.minecraft.util.ExtraCodecs.COMPONENT' {*/
		@Override
		public <T> DataResult<T> encode(Component input, DynamicOps<T> ops, T prefix) {
			safeCodec.set(false);
			var result = ComponentSerialization.CODEC.encode(input, ops, prefix);
			safeCodec.set(true);
			return result;
		}

		@Override
		public <T> DataResult<Pair<Component, T>> decode(DynamicOps<T> ops, T input) {
			safeCodec.set(false);
			var result = ComponentSerialization.CODEC.decode(ops, input);
			safeCodec.set(true);
			return result;
		}

		@Override
		public String toString() {
			return "UnsafeTextCodec[safe=" + safeCodec.get() + ", codec=" + ComponentSerialization.CODEC + "]";
		}
		/*~}*/
	};

	/**
	 * A boolean-wrapped holder that is {@code true} when safety mechanisms
	 * <b>should</b> be used when serializing text components, and {@code false}
	 * otherwise. Allows for the serialization of potentially unsafe objects
	 * like {@link ClickEvent}s.
	 *
	 * @see ClickEvent$ActionMixin#allowConditionalSerialization(boolean)
	 *
	 * @implNote Thread-local because
	 * <a href="https://discord.com/channels/507304429255393322/721100785936760876/1387226885867704401">
	 * TheWhyEvenHow</a> suggested this and its (successful) implementation.
	 */
	public static final ThreadLocal<Boolean> safeCodec = ThreadLocal.withInitial(() -> true);


	/**
	 * Replaces all {@code $} characters in {@code str} with {@code variable}.
	 * Also replaces intended newline characters with {@code \n} to fix (#36).
	 */
	public static String fillVars(String str, String variable) {
		return str.replace("$", variable).replace("\\n", "\n");
	}

	/**
	 * Parses the given object into a {@link MutableComponent}.
	 */
	public static MutableComponent asText(Object o) {
		if(o == null) {
			return Component.empty();
		} else if(o instanceof Component) {
			return (MutableComponent) o;
		} else {
			/*? if java: >= 21 {*/
			String value = switch(o) {
				case StringRepresentable sr -> sr.getSerializedName();
				case FormattedText ft -> ft.getString();
				case Message m -> m.getString();
				default -> String.valueOf(o);
			};
			/*?} else {*/
			/*String value;
			if(o instanceof StringRepresentable sr) {
				value = sr.getSerializedName();
			} else if(o instanceof FormattedText ft) {
				value = ft.getString();
			} else if(o instanceof Message m) {
				value = m.getString();
			} else {
				value = String.valueOf(o);
			}*/
			/*?}*/

			return Component.literal(value);
		}
	}

	public static MutableComponent truncate(Component text, int max) {
		if(ChatFormatting.stripFormatting(text.getString()).length() <= max) {
			return (MutableComponent) text;
		}

		var truncated = Component.empty();
		int[] len = {0}; // prepub: try text.toFlatList() then iterate over each entry in an enhanced-for to avoid len[0] (/!\)

		text.visit((style, str) -> {
			if(str.length() + len[0] > max) {
				if(len[0] >= max) {
					return Optional.empty(); // if the text is the perfect length, don't add anything
				}

				str = str.substring(0, max - len[0]); // truncate the string to the max length
			}

			truncated.append(Component.literal(str).setStyle(style));

			len[0] += str.length();

			return Optional.empty();
		}, EMPTY);

		return truncated;
	}

	/**
	 * Returns a copy of {@code text} with the specified {@code siblings}
	 * parameter replacing the original siblings. The passed {@code text}'s
	 * content and style are preserved.
	 */
	public static MutableComponent newSiblings(Component text, List<Component> siblings) {
		return new MutableComponent(text.getContents(), siblings, text.getStyle());
	}

	/**
	 * Formats a String with {@code &} formatting codes into a {@link Component}.
	 * First replaces all {@code &<?>} codes with a section symbol ({@code §}),
	 * then deletes the backslash from all {@code \&<?>} instances. Doesn't
	 * support hex colors.
	 */
	public static MutableComponent text(String unformatted) {
		String s = NO_BACKSLASH_AMPERSAND_REGEX.reset(unformatted).replaceAll("§$1");
		s = AMPERSAND_REGEX.reset(s).replaceAll("&$2");

		return Component.literal(s);
	}

	/**
	 * @return true if style is blank - that is, it doesn't have a <b>visible</b> impact
	 * on text. It may contain complex data (click/hover events, insertions) but if
	 * it affects anything visually (bold, *colored, font-ed), returns false.
	 * *color may be white.
	 */
	public static boolean isBlank(Style style) {
		if(style.equals(EMPTY)) return true;
		if(style.equals(BLANK)) return true;

		if(style.isBold()) return false;
		if(style.isItalic()) return false;
		if(style.isUnderlined()) return false;
		if(style.isStrikethrough()) return false;
		if(style.isObfuscated()) return false;

		if((Object)style.getColor() instanceof TextColor c && c.getValue() != Colors.WHITE) return false;

		// shadowColor is ignored as it's only in newer versions

		//noinspection RedundantIfStatement: i got a pattern going here. shutup
		if(!style.getFont().equals( /*? if >1.21.1 {*/FontDescription.DEFAULT/*?} else {*//*Style.DEFAULT_FONT*//*?}*/ )) return false;

		return true;
	}


	/**
	 * @return {@code true} if `a` and `b` are virtually equal; that is, a
	 * styled color visitor produces the same sequence for both components.
	 * This is intended to compare the string and style data together, although
	 * this is a convoluted task that will always have frustrating edge cases.
	 *
	 * todo rewrite this javadoc and note any unfixables (if any)
	 */
	public static boolean virtuallyEqual(Component a, Component b) { // FIXME: finish this method (/!\)
		boolean param_caseSensitive = true;

		if(Objects.equals(a, b)) return true;

		String aStripped = ChatFormatting.stripFormatting(a.getString()), bStripped = ChatFormatting.stripFormatting(b.getString());
		if(param_caseSensitive ? !aStripped.equals(bStripped) : !aStripped.equalsIgnoreCase(bStripped)) return false;

		List<Component> aFlatList = uniformFlatList(a);
		List<Component> bFlatList = uniformFlatList(b);

		for(int i = 0; i < aFlatList.size() && i < bFlatList.size(); i++) {
			Component aPart = aFlatList.get(i), bPart = bFlatList.get(i);
			String aStr = aPart.getString(), bStr = bPart.getString();
			Style aStyle = aPart.getStyle(), bStyle = bPart.getStyle();

			if(param_caseSensitive ? !aStr.equals(bStr) : !aStr.equalsIgnoreCase(bStr))
				return false;

			if(!Objects.equals(aStyle, bStyle) || isBlank(aStyle) != isBlank(bStyle))
				return false;
		}

		if(aFlatList.size() != bFlatList.size()) {
			LOGGER.warn("PROBABLY NOT EQUAL (diff sizes)");// debug:wub
			if(aFlatList.size() > bFlatList.size()) {
				LOGGER.warn("Skipped {} entries of aFlatList: {}", aFlatList.size() - bFlatList.size(), aFlatList.subList(bFlatList.size(), aFlatList.size()));
			} else {
				LOGGER.warn("Skipped {} entries of bFlatList: {}", bFlatList.size() - aFlatList.size(), bFlatList.subList(aFlatList.size(), bFlatList.size()));
			}
		}

		return true;
	}

	// transforms legacy strings into component lists. does not support hex codes (as they are not supported w/ section signs) nor complex data
	/**
	 * If {@code text} is a literal text component containing a single string
	 * with legacy formatting codes, manually splits it into a list of individual
	 * components, each with their own appropriate style. Otherwise, returns the
	 * result of {@link Component#toFlatList()}.
	 */
	private static List<Component> uniformFlatList(Component text) {
		ComponentContents contents = text.getContents();
		String str;

		List<Component> flatList = text.toFlatList();
		if(flatList.size() == 1 && contents instanceof PlainTextContents plain && plain.text().contains("§")) {
			str = plain.text();
		} else {
			return flatList;
		}

		String consumable = str;

		String[] split = str.split("(?:§(.))+"); // contains the strings making up `text`
		List<Component> result = new ObjectArrayList<>(split.length);
		String prevCodes = ""; // formatting codes from the previous string - ensures they're propagated forward
		int i = 0; // index relative to split
		for(String s : split) {
			if(!s.isEmpty()) {
				int strI = consumable.indexOf(s); // index relative to the mid-consumption entire message

				String whitespace;
				while(i + 1 < split.length && (whitespace = split[i + 1]).isBlank() && !whitespace.isEmpty()) {
					s += whitespace;
					split[i + 1] = "";
					consumable = consumable.replaceFirst(whitespace, ""); // /!\ (source of current error)
					//TODO could help if replaced w non-regex version - but might still cause issues...?!?!
					//^ stopped here, last thing i added. stops NPE but now removes the necessary &r code..?
					//i++;
				}

				MutableComponent component = Component.literal(s);
				if(strI != 0) {
					String block = consumable.substring(1, strI).replace("§", "");
					if(!block.contains("r")) {
						// if there's no reset code, make sure to inherit any previous formattings first.
						// they will automatically be overridden as they should be
						block = prevCodes + block;
					}

					char[] codes = block.toCharArray();
					for(char c : codes) {
						component.withStyle(ChatFormatting.getByCode(c));
					}
					prevCodes = block;
				}

				consumable = consumable.substring(strI + s.length());

				result.add(component);
			}
			i++;
		}
		return result;
	}

	/**
	 * Converts a {@link Component} into a {@link String} with {@code &<?>} codes.
	 * Strips any complex style data, including hover events, fonts, insertions,
	 * etc. Hex colors are represented in the format {@code &#RRGGBB}.
	 */
	public static String toLegacyString(Component text, boolean prettyPrint) {
		StringBuilder builder = new StringBuilder();
		AtomicReference<Style> lastStyle = new AtomicReference<>(null); // ensures that the first equality check returns false

		// TEST:this with complex components (ex. root w/ hex color or custom style so &r applies that..?)
		for(Component part : uniformFlatList(text)) {
			part.visit((style, str) -> {
				if(str.isBlank()) {
					// add the whitespace but don't format anything
					builder.append(str);
					return Optional.empty();
				}
				//elif str.contains("&*") {str.replaceAll("&*", "\\\\&*")} //todo

				String leadingWhitespace = str.substring(0, str.indexOf(str.stripLeading()));

				// if style is different from last, add any formatting codes
				if(!style.equals(lastStyle.get())) {
					if(!leadingWhitespace.isEmpty()) {
						// adds the whitespace BEFORE the formatting codes and regular text to provide
						// better readability (hugs the right-most characters instead of left-most)
						builder.append(leadingWhitespace);
					}

					// if lastStyle == null, we pass it as empty here
					// the only reason it's initially null is that some messages are constructed with legacy codes,
					// and those messages have one part with a root empty style (skipping this block)
					builder.append(getFormattingCodes(style, Objects.requireNonNullElse(lastStyle.get(), EMPTY)));

					lastStyle.set(style);
				}

				// sometimes section signs leak
				// this has to be done here or else the stylish color codes added above will be erased
				builder.append(str.stripLeading().replace(ChatFormatting.PREFIX_CODE, '&'));

				return Optional.empty();
			}, EMPTY);
		}

		// === Output fixes and optimizations ===

		// TODO: SEE HOW MANY OF THESE ARE ACTUALLY TRIGGERED, AND IF NONE ARE COMMENT OUT - ESP. REGEXES! (/!\)

		// removes any leading reset codes - all styles begin naturally reset
		while(builder.indexOf("&r") == 0) {
			builder.delete(0, 2);
		}

		// removes redundant white color codes directly succeeding reset codes (white is the default color already)
		for(int i = builder.indexOf("&r&f"); i != -1; i = builder.indexOf("&r&f")) {
			builder.delete(i + 2, i + 4);
		}

		String result = builder.toString();
		Matcher m;
		while((m = REDUNDANT_COLOR_REGEX.reset(result)).find()) {
			result = m.replaceFirst("$1&$2");
		}

		// finally, makes all formatting codes aqua so they're easily distinguishable
		if(prettyPrint && (m = PRETTY_PRINT_TARGETS_REGEX.reset(result)).find()) {
			result = m.replaceAll(ChatFormatting.AQUA + "$0" + ChatFormatting.RESET);
			// prepub: replace &<?> codes that were in the original message with \\\\$1 ? this regex alr ignores them (/!\)
		}

		return result;
	}

	/**
	 * Returns the formatting codes of the {@link Style} provided, excluding any already
	 * applied ones according to {@code last}. Returns an empty string if the style is
	 * empty or blank. If any hex colors are specified, they will be returned in the
	 * format {@code &#RRGGBB}. Additionally, any color that exists as a formatting code
	 * (ex. {@code #55FF55} for {@link ChatFormatting#GREEN}) will return as the formatting
	 * code (ex. {@code &a}).
	 *
	 * @see TextColor#formatValue()
	 */
	public static String getFormattingCodes(@NotNull Style style, @NotNull Style last) {
		StringJoiner joiner = new StringJoiner("&", "&", "").setEmptyValue(""); // adds the & at the start of the string

		if(style.equals(last) || (isBlank(style) && isBlank(last))) {
			// nothing has changed
			return "";
		} else if(style.equals(EMPTY) /*&& !fillOutBooleans(last).equals(BLANK)*/) {
			// here we know last isn't empty, so we must reset
			return "&r";
		}
		// todo two more conditions: one where style has all false and last has all false and vice versa (null -> false) (/!\)

		// if the color is named, it will have a name
		// makes the fallback white so changes to colorless but not empty styles don't ignore colors
		TextColor thisColor = Colors.simplify(style.getColor() != null ? style.getColor().getValue() : Colors.WHITE);
		int thisValue = thisColor.getValue();
		int lastValue = last.getColor() != null ? last.getColor().getValue() : Colors.WHITE;

		// ensures reset codes are not treated as white codes by forcing necessary reset codes
		// only marked as changed if a code is in the last style but not the current one
		boolean modifierChanged = (last.isBold() && !style.isBold()) || (last.isItalic() && !style.isItalic()) || (last.isUnderlined() && !style.isUnderlined()) ||
			(last.isStrikethrough() && !style.isStrikethrough()) || (last.isObfuscated() && !style.isObfuscated());

		if(modifierChanged) {
			// this is necessary because, unlike with colors, you cannot overwrite one modifier with another
			joiner.add("r");
		}

		// adds the color code if we reset, and it's not white (no "&r&f"); or if we didn't reset and the colors changed
		if(modifierChanged ? (thisValue != Colors.WHITE) : (thisValue != lastValue))
		{
			Optional<String> code = Colors.getCode(thisColor);
			// if thisColor is named, add its formatting code, else add its hex color
			joiner.add( code.orElse(thisColor.serialize()) ); // at this point we know thisColor isn't named, so it will call formatValue() for us
		}
		else if(style.equals(EMPTY) && !last.equals(EMPTY)) // todo move this check up earlier, we dont need to do all that logic if current is empty /!\
		{
			return "&r"; // if the current style is empty but the last style wasn't, we've reset!
		}

		if(style.isBold() && (!last.isBold() || modifierChanged))
			joiner.add("l");
		if(style.isItalic() && (!last.isItalic() || modifierChanged))
			joiner.add("o");
		if(style.isUnderlined() && (!last.isUnderlined() || modifierChanged))
			joiner.add("n");
		if(style.isStrikethrough() && (!last.isStrikethrough() || modifierChanged))
			joiner.add("m");
		if(style.isObfuscated() && (!last.isObfuscated() || modifierChanged))
			joiner.add("k");

		return joiner.toString();
	}
}