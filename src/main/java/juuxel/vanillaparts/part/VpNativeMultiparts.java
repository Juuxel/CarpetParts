package juuxel.vanillaparts.part;

import alexiil.mc.lib.multipart.api.MultipartContainer;
import alexiil.mc.lib.multipart.api.NativeMultipart;
import alexiil.mc.lib.multipart.api.PartDefinition;
import juuxel.vanillaparts.lib.Exclusions;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.CakeBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class VpNativeMultiparts {
    private static final NativeMultipart LEVER = create(
        (world, pos, state) -> holder -> new LeverPart(
            VpParts.LEVER, holder,
            state.get(LeverBlock.FACE),
            state.get(LeverBlock.FACING),
            state.get(LeverBlock.POWERED)
        )
    );

    private static final NativeMultipart SLAB = create((world, pos, state) -> {
        SlabType type = state.get(SlabBlock.TYPE);
        if (type == SlabType.DOUBLE) return null;
        return holder -> new SlabPart(VpParts.SLAB, holder, (SlabBlock) state.getBlock(), type == SlabType.TOP);
    });

    private static final NativeMultipart CAKE = create(
        (world, pos, state) -> holder -> new CakePart(VpParts.CAKE, holder, state.get(CakeBlock.BITES))
    );

    private static final NativeMultipart FENCE = create(
        (world, pos, state) -> holder -> new FencePart(
            VpParts.FENCE, holder, state.getBlock(),
            state.get(FenceBlock.NORTH), state.get(FenceBlock.EAST), state.get(FenceBlock.SOUTH), state.get(FenceBlock.WEST)
        )
    );

    private static final NativeMultipart BUTTON = create(
        (world, pos, state) -> holder -> new ButtonPart(
            VpParts.BUTTON, holder, state.getBlock(),
            state.get(ButtonBlock.FACE), state.get(ButtonBlock.FACING)
        )
    );

    public static void init() {
        // Register for specific blocks
        NativeMultipart.LOOKUP.registerForBlocks(constantApi(LEVER), Blocks.LEVER);
        NativeMultipart.LOOKUP.registerForBlocks(constantApi(CAKE), Blocks.CAKE);
        VpParts.CARPETS.forEach((color, block) -> {
            NativeMultipart nativeMultipart = carpet(color);
            NativeMultipart.LOOKUP.registerForBlocks(constantApi(nativeMultipart), block);
        });
        registerTorch(VpParts.TORCH, Blocks.TORCH, Blocks.WALL_TORCH);
        registerTorch(VpParts.SOUL_TORCH, Blocks.SOUL_TORCH, Blocks.SOUL_WALL_TORCH);

        // Register for block classes
        NativeMultipart.LOOKUP.registerFallback((world, pos, state, blockEntity, context) -> {
            // Excluded blocks can't become parts.
            if (Exclusions.isExcluded(state)) return null;

            Block block = state.getBlock();
            if (block instanceof SlabBlock) {
                return SLAB;
            } else if (block instanceof FenceBlock) {
                return FENCE;
            } else if (block instanceof ButtonBlock) {
                return BUTTON;
            }

            return null;
        });
    }

    private static NativeMultipart create(Conversion conversion) {
        return new SingleNativeMultipart(conversion);
    }

    private static void registerTorch(PartDefinition definition, Block groundBlock, Block wallBlock) {
        // Ground
        NativeMultipart.LOOKUP.registerForBlocks(
            constantApi(
                create(
                    (world, pos, state) -> holder -> new TorchPart(
                        definition, holder, groundBlock, wallBlock,
                        TorchPart.Facing.GROUND
                    )
                )
            ),
            groundBlock
        );

        // Wall
        NativeMultipart.LOOKUP.registerForBlocks(
            constantApi(
                create(
                    (world, pos, state) -> holder -> new TorchPart(
                        definition, holder, groundBlock, wallBlock,
                        TorchPart.Facing.of(state.get(WallTorchBlock.FACING))
                    )
                )
            ),
            wallBlock
        );
    }

    private static NativeMultipart carpet(DyeColor color) {
        return create((world, pos, state) -> holder -> new CarpetPart(VpParts.CARPET_PARTS.get(color), holder, color));
    }

    private static <A, C> BlockApiLookup.BlockApiProvider<A, C> constantApi(A api) {
        return (world, pos, state, blockEntity, context) -> {
            // Excluded blocks can't become parts.
            if (Exclusions.isExcluded(state)) return null;

            return api;
        };
    }

    private static class SingleNativeMultipart implements NativeMultipart {
        private final Conversion conversion;

        SingleNativeMultipart(Conversion conversion) {
            this.conversion = conversion;
        }

        @Nullable
        @Override
        public List<MultipartContainer.MultipartCreator> getMultipartConversion(World world, BlockPos pos, BlockState state) {
            MultipartContainer.MultipartCreator creator = conversion.getConversion(world, pos, state);
            return creator != null ? List.of(creator) : null;
        }
    }

    @FunctionalInterface
    private interface Conversion {
        @Nullable
        MultipartContainer.MultipartCreator getConversion(World world, BlockPos pos, BlockState state);
    }
}
