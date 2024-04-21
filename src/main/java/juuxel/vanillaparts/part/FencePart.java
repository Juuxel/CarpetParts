/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package juuxel.vanillaparts.part;

import alexiil.mc.lib.multipart.api.MultipartHolder;
import alexiil.mc.lib.multipart.api.PartDefinition;
import alexiil.mc.lib.net.IMsgReadCtx;
import alexiil.mc.lib.net.InvalidInputDataException;
import alexiil.mc.lib.net.NetByteBuf;
import juuxel.blockstoparts.api.category.CategorySet;
import juuxel.vanillaparts.util.FenceExtensions;
import juuxel.vanillaparts.util.NbtKeys;
import juuxel.vanillaparts.util.NbtUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FenceBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;

public class FencePart extends HorizontallyConnectedPart {
    private static final VoxelShape POST_SHAPE = Block.createCuboidShape(6, 0, 6, 10, 16, 10);

    // Automatically calculates connections
    public FencePart(PartDefinition definition, MultipartHolder holder, Block fence) {
        super(definition, holder, fence);
    }

    public FencePart(PartDefinition definition, MultipartHolder holder, Block fence, boolean north, boolean east, boolean south, boolean west) {
        super(definition, holder, fence, north, east, south, west);
    }

    public static FencePart fromNbt(PartDefinition definition, MultipartHolder holder, NbtCompound nbt) {
        Block fence = NbtUtil.getRegistryEntry(nbt, NbtKeys.BLOCK_ID, Registries.BLOCK);
        boolean north = nbt.getBoolean(NbtKeys.NORTH);
        boolean east = nbt.getBoolean(NbtKeys.EAST);
        boolean south = nbt.getBoolean(NbtKeys.SOUTH);
        boolean west = nbt.getBoolean(NbtKeys.WEST);

        return new FencePart(definition, holder, fence, north, east, south, west);
    }

    public static FencePart fromBuf(PartDefinition definition, MultipartHolder holder, NetByteBuf buf, IMsgReadCtx ctx) throws InvalidInputDataException {
        Block fence = Registries.BLOCK.get(buf.readIdentifierSafe());
        boolean north = buf.readBoolean();
        boolean east = buf.readBoolean();
        boolean south = buf.readBoolean();
        boolean west = buf.readBoolean();

        return new FencePart(definition, holder, fence, north, east, south, west);
    }

    @Override
    public VoxelShape getShape() {
        return POST_SHAPE;
    }

    @Override
    protected boolean canConnectTo(BlockPos neighborPos, Direction d) {
        BlockState state = getWorld().getBlockState(neighborPos);
        Direction sideOfOther = d.getOpposite();
        boolean isSideSolidFullSquare = state.isSideSolidFullSquare(getWorld(), neighborPos, sideOfOther);
        return ((FenceBlock) block).canConnect(state, isSideSolidFullSquare, sideOfOther) || ((FenceExtensions) block).vanillaParts_canConnectToMultiparts(getWorld(), neighborPos, sideOfOther);
    }

    @Override
    protected void addCategories(CategorySet.Builder builder) {
        builder.add(VpCategories.FENCES);
        builder.overlap(VpCategories.CARPETS);
    }
}
