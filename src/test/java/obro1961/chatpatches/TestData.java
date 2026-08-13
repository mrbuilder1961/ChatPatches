package obro1961.chatpatches;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.net.URI;

import static net.minecraft.ChatFormatting.*;
import static net.minecraft.network.chat.CommonComponents.space;
import static net.minecraft.network.chat.Component.empty;
import static net.minecraft.network.chat.Component.literal;
import static net.minecraft.network.chat.Style.EMPTY;
import static obro1961.chatpatches.util.TextUtil.text;

// prepub: re-enable this - it's just so i know which texts i haven't used yet /!\
//@SuppressWarnings("unused")
public final class TestData {
	//region [Strings]
	public static final String STR_HELLO = "Hello World!",
							   STR_CHAT_LONG = "heyyyy how's it going this is more like a real message i would send in chat :P",
							   STR_NON_ASCII = "this is also un poco reál pero tiene charactares únicos que no existen en ASCII",
							   STR_UNICODE = "this one is giving UTF-16 🍕🍖📸🔁✅🥺🤪😻😭💥📝❌ woahh!",
							   STR_LINES = "STR_LINES references itself\n\nm\ne\nt\na\n-ly\n(aka by having line breaks!)",
							   STR_SLC = "§",
							   STR_SLC_RESET = "§r", // ....................................................v<- last char
							   STR_SLC_EX_RAW = "STR_SLC_EX is a regular string but with official formatting codes!",
							   STR_SLC_EX = "STR_SLC_EX is a §oregular§r string §5but with §l§nofficial§r §eformatting §ccodes§7!",
							   STR_SLC_EX_AMP = "STR_SLC_EX is a &oregular &rstring &5but with &l&nofficial &r&eformatting &ccodes&7!",
							   STR_AMP = "&",
							   STR_AMP_RESET = "&r", // todo move to regular TextUtil?
							   STR_AMP_EX = "STR_AMP_EX is a &oregular&r string &5but with &l&nampersand&r &eformatting &ccodes&7!",
							   STR_MULTILINE = "THIS is a longer version of STR_CHAT_LONG: heyyyy how's it going this is more like a real message i would send in chat :P" +
								   " but this one would be multiline ooooohhhhh!! (:",
							   STR_ID = "valid_identifier",
							   STR_INVALID_ID = "0invalid identif1er!-",
							   STR_BOUNDARY_ID = "boundary_line[level=MyWorld11,side=CLIENT]",
							   STR_BOUNDARY_ID_FULL = "chatpatches:boundary_line[level=MyWorld12,side=CLIENT]",
							   STR_WORD = "resonance",
							   STR_WORD2 = "vocabulary",
							   STR_PLAYER = "OBro1961",
							   STR_GIVE_CMD = "/give @s enchanted_golden_apple",
							   STR_LINK_EX = "www.example.com",
							   STR_LINK_INDEX = "www.example.com/dir/index.htm",
							   STR_LINK_HTTP = "http://www.example.com/dir/index.htm",
							   STR_LINK_FULL = "https://www.google.com/search?q=cherry+picking",
							   STR_LINK_FULL2 = "https://minecraft.wiki/w/Formatting_codes",
							   STR_LINK_INVALID_FTP = "ftp://network/home/.root/pwd/secrets.csv",
							   STR_LINK_INVALID_FILE = "file:///N:/.root/homework/innocent_video.mov",
							   STR_REDUNDANT_SLC = "§4§2STR17 has some §lredundant§r §4codes§e §9§nthat can be §6§dfiltered out§8.§r",
							   STR_REDUNDANT_AMP = "&4&2STR17 has some &lredundant&r &4codes&e &9&nthat can be &6&dfiltered out&8.&r",
							   STR_NO_REDUNDANT_SLC = "§2STR18 has §c§lNO §r§lredundant§r §4codes §9§nthat can be §dfiltered out§8.",
							   STR_NO_REDUNDANT_AMP = "&2STR18 has &c&lNO &r&lredundant&r &4codes &9&nthat can be &dfiltered out&8.",
							   STR_NEW_BUFF_SLC = "§eNew buff§f: Gain §a+5% §2∮ Sweep§f.",
							   STR_NEW_BUFF_AMP = "&eNew buff&f: Gain &a+5% &2∮ Sweep&f.",
							   STR_WATCHDOG_SLC = "Watchdog has banned §c§l5,565 §rplayers in the last 7 days.",
							   STR_WATCHDOG_AMP = "Watchdog has banned &c&l5,565 &rplayers in the last 7 days.",
							   STR_PRESSURE_SLC = " §c☠ §aNOT_LEGEND_ §7fainted from pressure.",
							   STR_PRESSURE_AMP = " &c☠ &aNOT_LEGEND_ &7fainted from pressure.",
							   STR_SPACER = "       ",
							   STR_SPACER_SLC = "§8  §1 §3  §7 §8 ",
							   STR_SPACER_AMP = "&8  &1 &3  &7 &8 ",
							   STR_TRAILER_SLC = "hey b§4AKA.§f IM GONNA §lKILL §dYOUUU§e    ",
							   STR_TRAILER_AMP = "hey b&4AKA. &fIM GONNA &lKILL &dYOUUU    "
	;//endregion

	//region [Text Actionables]
	public static final ClickEvent CLICK_SUGGEST_GIVE = new /*$ suggest_command 'STR_GIVE_CMD' >> '),'*/ClickEvent.SuggestCommand(STR_GIVE_CMD),
								   CLICK_OPEN_HELP = new /*$ open_url 'STR_LINK_FULL2' >> '),'*/ClickEvent.OpenUrl(URI.create(STR_LINK_FULL2)),
								 /*CLICK_RUN_TELLRAW = new *//*-$ run_command 'STR_TELLRAW_CMD' >> '),'*//*ClickEvent.OpenUrl(URI.create(STR_LINK_FULL2))*/
	CLICK_NULL = null
	;
	public static final HoverEvent HOVER_SHOW_HELLO = new /*$ show_text 'literal(STR_HELLO)' >> '),'*/HoverEvent.ShowText(literal(STR_HELLO)),
								   HOVER_SHOW_WORD2 = new /*$ show_text 'literal(STR_HELLO)' >> '),'*/HoverEvent.ShowText(literal(STR_WORD2)),
								   HOVER_SHOW_LINES = new /*$ show_text 'literal(STR_HELLO)' >> '),'*/HoverEvent.ShowText(literal(STR_LINES)),
								   /*HOVER_SHOW_ITEM = new *//*$ show_text 'literal(STR_HELLO)' >> '),'*//*HoverEvent.ShowItem(literal(STR_LINES)),
								   HOVER_SHOW_ENTITY = new *//*$ show_text 'literal(STR_HELLO)' >> '),'*//*HoverEvent.ShowEntity(literal(STR_LINES)),*/
	HOVER_NULL = null
	;//endregion

	//region [Styles]
	public static final Style STYLE_BLANK = EMPTY.withBold(false).withItalic(false).withUnderlined(false).withObfuscated(false).withStrikethrough(false),
							  STYLE_FULL = EMPTY.withBold(true).withItalic(true).withUnderlined(true).withObfuscated(true).withStrikethrough(true),
							  STYLE_LIGHT_PURPLE = EMPTY.applyFormat(LIGHT_PURPLE),
							  STYLE_DARK_AQUA = EMPTY.applyFormat(DARK_AQUA),
							  STYLE_LUSH_GREEN = EMPTY.withColor(0x0D8815),
							  STYLE_LAVENDER = EMPTY.withColor(0xB783E8),
							  STYLE_WHITE = EMPTY.applyFormat(WHITE),
							  STYLE_BOLD_DARK_RED = EMPTY.applyFormats(BOLD, DARK_RED),
							  STYLE_BOLD_GREEN = EMPTY.applyFormats(BOLD, GREEN),
							  STYLE_BOLD_DARK_PURPLE = EMPTY.applyFormats(BOLD, DARK_PURPLE),
							  STYLE_BOLD_ITALIC_GOLD = EMPTY.applyFormats(BOLD, ITALIC, GOLD),
							  STYLE_BOLD_BLUE_HOVER = EMPTY.applyFormats(BOLD, BLUE).withHoverEvent(HOVER_SHOW_HELLO),
							  STYLE_BOLD_UNDERLINE_LAVENDER = STYLE_LAVENDER.applyFormats(BOLD, UNDERLINE).withColor(0xB783E8),
							  STYLE_ITALIC = EMPTY.applyFormat(ITALIC),
							  STYLE_ITALIC_GRAY = EMPTY.applyFormats(ITALIC, GRAY),
							  STYLE_UNDERLINE_RED = EMPTY.applyFormats(UNDERLINE, RED),
							  STYLE_YELLOW_INSERTION = EMPTY.applyFormat(YELLOW).withInsertion(STR_PLAYER),
							  STYLE_PURPLE_CLICK = EMPTY.applyFormat(DARK_PURPLE).withClickEvent(CLICK_SUGGEST_GIVE),
							  STYLE_INVISIBLE_ACTIONS = EMPTY.withInsertion(STR_HELLO).withClickEvent(CLICK_OPEN_HELP).withHoverEvent(HOVER_SHOW_LINES)
	;//endregion

	//region [Components]
	public static final Component TEXT_HELLO = literal(STR_HELLO),
								  TEXT_HELLO_STYLED = literal(STR_HELLO).withStyle(GREEN, BOLD),
								  TEXT_CHAT_LONG = literal(STR_CHAT_LONG),
								  TEXT_NON_ASCII = literal(STR_NON_ASCII),
								  TEXT_UNICODE = literal(STR_UNICODE),
								  TEXT_LINES = literal(STR_LINES),
								  TEXT_SLC_EX = literal(STR_SLC_EX),
								  TEXT_SLC_EX_EXPLICIT = literal("STR_SLC_EX is a ")
									.append(literal("regular").withStyle(ITALIC))
									.append(literal(" string "))
									.append(literal("but with ").withStyle(DARK_PURPLE))
									.append(literal("official").withStyle(DARK_PURPLE, BOLD, UNDERLINE))
									.append(space())
									.append(literal("formatting ").withStyle(YELLOW))
									.append(literal("codes").withStyle(RED))
									.append(literal("!").withStyle(GRAY)),
								  TEXT_AMP = text(STR_AMP_EX),
								  TEXT_MULTILINE = literal(STR_MULTILINE),
								  TEXT_ID = literal(STR_ID),
								  TEXT_INVALID_ID = literal(STR_INVALID_ID),
								  TEXT_BOUNDARY_ID = literal(STR_BOUNDARY_ID),
								  TEXT_BOUNDARY_ID_FULL = literal(STR_BOUNDARY_ID_FULL),
								  TEXT_WORD = literal(STR_WORD),
								  TEXT_WORD2 = literal(STR_WORD2),
								  TEXT_PLAYER = literal(STR_PLAYER),
								  TEXT_GIVE_CMD = literal(STR_GIVE_CMD),
								  TEXT_REDUNDANT_AMP = text(STR_REDUNDANT_AMP),
								  TEXT_NO_REDUNDANT_AMP = text(STR_NO_REDUNDANT_AMP),
								  TEXT_NO_REDUNDANT_AMP_EXPLICIT = literal("STR18 has ").withStyle(DARK_GREEN)
									.append(literal("NO ").withStyle(RED, BOLD))
									.append(literal("redundant ").withStyle(RESET, BOLD))//todo lol double check this guy /!\
									.append(literal("codes ").withStyle(DARK_RED))
									.append(literal("that can be ").withStyle(BLUE, UNDERLINE))
									.append(literal("filtered out").withStyle(STYLE_LIGHT_PURPLE))
									.append(literal(".").withStyle(DARK_GRAY)),
								  TEXT_NEW_BUFF = literal(STR_NEW_BUFF_SLC),
								  TEXT_NEW_BUFF_EXPLICIT = empty().withStyle(style -> style.withItalic(false))
									  .append(literal("New buff").withStyle(YELLOW))
									  .append(empty())
									  .append(empty().append(literal(": ").withStyle(STYLE_BLANK)))
									  .append(literal("Gain ").withStyle(WHITE))
									  .append(literal("+5% ").withStyle(GREEN))
									  .append(literal("∮ Sweep").withStyle(DARK_GREEN))
									  .append(literal(".").withStyle(WHITE)),
								  TEXT_WATCHDOG = literal(STR_WATCHDOG_SLC),
								  TEXT_WATCHDOG_EXPLICIT = literal("Watchdog has banned ").withStyle(WHITE)
									  .append(literal("5,565").withStyle(RED, BOLD))
									  .append(literal(" players in the last 7 days.").withStyle(style -> style.withColor(WHITE).withBold(false))),
								  TEXT_PRESSURE = literal(STR_PRESSURE_SLC),
								  TEXT_PRESSURE_EXPLICIT = empty().withStyle(style -> style.withItalic(false))
									  .append(literal(" ☠ ").withStyle(RED))
									  .append(empty().withStyle(GRAY))
									  .append(literal("NOT_LEGEND_").withStyle(GREEN))
									  .append(literal(" fainted from pressure").withStyle(GRAY))
									  .append(literal(".").withStyle(GRAY)),
								  TEXT_SPACER = literal(STR_SPACER_SLC),
								  TEXT_SPACER_EXPLICIT = empty().withStyle(style -> style.withItalic(false))
									.append(space().withStyle(DARK_GRAY))
									.append(space().withStyle(DARK_GRAY))
									.append(space().withStyle(DARK_BLUE))
									.append(space().withStyle(DARK_AQUA))
									.append(space().withStyle(DARK_AQUA))
									.append(space().withStyle(GRAY))
									.append(space().withStyle(DARK_GRAY)),
								  TEXT_SPACER_OPTIMIZED = empty().withStyle(style -> style.withItalic(false))
									  .append(literal("  ").withStyle(DARK_GRAY))
									  .append(space().withStyle(DARK_BLUE))
									  .append(literal("  ").withStyle(DARK_AQUA))
									  .append(space().withStyle(GRAY))
									  .append(space().withStyle(DARK_GRAY)),
								  TEXT_TRAILING = literal("hey b§4AKA.§f IM GONNA §lKILL §dYOUUU§e    "),
								  TEXT_TRAILING_EXPLICIT = empty()
									  .append(literal("hey b"))
									  .append(literal("AKA.").withStyle(DARK_RED))
									  .append(literal(" IM GONNA ").withStyle(WHITE))
									  .append(literal("KILL ").withStyle(BOLD))
									  .append(literal("YOUUU").withStyle(LIGHT_PURPLE, BOLD))
									  .append(literal("    ").withStyle(YELLOW))
	;//endregion
}