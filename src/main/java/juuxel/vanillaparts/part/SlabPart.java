/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package juuxel.vanillaparts.part;

import alexiil.mc.lib.multipart.api.MultipartEventBus;
import alexiil.mc.lib.multipart.api.MultipartHolder;
import alexiil.mc.lib.multipart.api.PartDefinition;
import alexiil.mc.lib.multipart.api.render.PartModelKey;
import alexiil.mc.lib.net.IMsgReadCtx;
import alexiil.mc.lib.net.IMsgWriteCtx;
import alexiil.mc.lib.net.InvalidInputDataException;
import alexiil.mc.lib.net.NetByteBuf;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.DataFixUtils;
import juuxel.blockstoparts.api.category.CategorySet;
import juuxel.blockstoparts.api.model.StaticVanillaModelKey;
import juuxel.vanillaparts.util.NbtKeys;
import juuxel.vanillaparts.util.NbtUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.shape.VoxelShape;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

public class SlabPart extends VanillaPart {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final ImmutableMap<BlockHalf, VoxelShape> SHAPES;
    private final SlabBlock block;
    private final BlockHalf half;

    static {
        SHAPES = ImmutableMap.of(
            BlockHalf.BOTTOM, Block.createCuboidShape(0, 0, 0, 16, 8, 16),
            BlockHalf.TOP, Block.createCuboidShape(0, 8, 0, 16, 16, 16)
        );
    }

    public SlabPart(PartDefinition definition, MultipartHolder holder, SlabBlock block, BlockHalf half) {
        super(definition, holder);
        this.block = block;
        this.half = half;
    }

    public SlabPart(PartDefinition definition, MultipartHolder holder, SlabBlock block, boolean top) {
        super(definition, holder);
        this.block = block;
        this.half = top ? BlockHalf.TOP : BlockHalf.BOTTOM;
    }

    public static SlabPart fromNbt(PartDefinition definition, MultipartHolder holder, NbtCompound nbt) {
        Block block = NbtUtil.getRegistryEntry(nbt, NbtKeys.BLOCK_ID, Registries.BLOCK);
        boolean top = nbt.getBoolean(NbtKeys.IS_TOP);

        if (!(block instanceof SlabBlock slab)) {
            LOGGER.warn("Block {} is not a slab, falling back to minecraft:stone_slab", block);
            return new SlabPart(definition, holder, (SlabBlock) Blocks.STONE_SLAB, top);
        }

        return new SlabPart(definition, holder, slab, top);
    }

    public static SlabPart fromBuf(PartDefinition definition, MultipartHolder holder, NetByteBuf buf, IMsgReadCtx ctx) throws InvalidInputDataException {
        Block block = Registries.BLOCK.get(buf.readIdentifierSafe());
        boolean top = buf.readBoolean();

        if (!(block instanceof SlabBlock slab)) {
            LOGGER.warn("Block {} is not a slab, falling back to minecraft:stone_slab", block);
            return new SlabPart(definition, holder, (SlabBlock) Blocks.STONE_SLAB, top);
        }

        return new SlabPart(definition, holder, slab, top);
    }

    @Override
    public VoxelShape getShape() {
        return SHAPES.get(half);
    }

    @Override
    public PartModelKey getModelKey() {
        return new StaticVanillaModelKey(getBlockState());
    }

    @Override
    public ItemStack getPickStack(@Nullable BlockHitResult hitResult) {
        return new ItemStack(block);
    }

    @Override
    public void onAdded(MultipartEventBus bus) {
    }

    @Override
    public BlockState getBlockState() {
        return block.getDefaultState().with(SlabBlock.TYPE, half == BlockHalf.TOP ? SlabType.TOP : SlabType.BOTTOM);
    }

    @Override
    public NbtCompound toTag() {
        return DataFixUtils.make(new NbtCompound(), tag -> {
            NbtUtil.putRegistryEntry(tag, NbtKeys.BLOCK_ID, Registries.BLOCK, block);
            tag.putBoolean(NbtKeys.IS_TOP, half == BlockHalf.TOP);
        });
    }

    @Override
    public void writeCreationData(NetByteBuf buffer, IMsgWriteCtx ctx) {
        super.writeCreationData(buffer, ctx);
        buffer.writeIdentifier(Registries.BLOCK.getId(block));
        buffer.writeBoolean(half == BlockHalf.TOP);
    }

    @Override
    protected void addCategories(CategorySet.Builder builder) {
        builder.add(VpCategories.SLABS);
    }
}
