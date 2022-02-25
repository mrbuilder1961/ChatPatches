package obro1961;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.util.math.Direction;

@Mixin(LeavesBlock.class)
public class tmp extends Block {
    public tmp(Settings settings) {
        super(settings);
        //TODO Auto-generated constructor stub
    }

    @Override
    public boolean isSideInvisible(BlockState state, BlockState neighborState, Direction offset) {
        return neighborState.getBlock() instanceof LeavesBlock;
    }
}
