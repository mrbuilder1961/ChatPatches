package obro1961.chatpatches.util;

import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.Text;

public class RenderUtils {
	public static final ChatHudLine NIL_HUD_LINE = new ChatHudLine(0, Text.empty(), null, null);


	public static class MousePos {
		public int x, y;

		private MousePos(int x, int y) {
			this.x = x;
			this.y = y;
		}

		public static MousePos of(int x, int y) {
			return new MousePos(x, y);
		}
	}
}
