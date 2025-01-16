package obro1961.chatpatches.mixin.accessor;

import net.minecraft.client.gui.widget.GridWidget;
import net.minecraft.client.gui.widget.Widget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(GridWidget.class)
public interface GridWidgetAccessor {
	@Accessor
	List<Widget> getChildren();
}