/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package juuxel.vanillaparts;

import alexiil.mc.lib.multipart.api.AbstractPart;
import alexiil.mc.lib.multipart.api.MultipartContainer;
import alexiil.mc.lib.multipart.api.MultipartHolder;
import alexiil.mc.lib.multipart.api.MultipartUtil;
import alexiil.mc.lib.multipart.api.NativeMultipart;
import alexiil.mc.lib.multipart.api.PartDefinition;
import juuxel.vanillaparts.lib.Exclusions;
import juuxel.vanillaparts.part.ButtonPart;
import juuxel.vanillaparts.part.CakePart;
import juuxel.vanillaparts.part.CarpetPart;
import juuxel.vanillaparts.part.FencePart;
import juuxel.vanillaparts.part.LeverPart;
import juuxel.vanillaparts.part.SlabPart;
import juuxel.vanillaparts.part.TorchPart;
import juuxel.vanillaparts.part.VpParts;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.CakeBlock;
import net.minecraft.block.DyedCarpetBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.enums.WallMountLocation;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;

public enum MultipartItemTweak implements UseBlockCallback {
    INSTANCE;

    private final Event<Predicate<Block>> checkers = EventFactory.createArrayBacked(Predicate.class, checkers -> block -> {
        for (Predicate<Block> checker : checkers) {
            if (checker.test(block)) return true;
        }
        return false;
    });
    private final List<Extension> extensions = new ArrayList<>();

    public void addExtension(Extension extension) {
        extensions.add(extension);
    }

    public void addCustomContainerChecker(Predicate<Block> checker) {
        checkers.register(checker);
    }

    @Override
    public ActionResult interact(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
        ItemStack stack = player.getStackInHand(hand);
        Item item = stack.getItem();
        if (item instanceof BlockItem bi) {
            Block block = bi.getBlock();
            if (Exclusions.isExcluded(block)) return ActionResult.PASS;

            BlockPos pos = hit.getBlockPos().offset(hit.getSide());
            MultipartContainer.PartOffer offer = null;

            if (isMissingContainer(world, pos) && !(block instanceof SlabBlock || block instanceof FenceBlock || checkers.invoker().test(block))) // slabs and fences do custom checking
                return ActionResult.PASS; // Revert to vanilla placement

            if (block instanceof DyedCarpetBlock) {
                offer = handleCarpets(world, pos, block);
            } else if (block == Blocks.TORCH) {
                offer = handleTorches(player, world, hand, hit, pos, VpParts.TORCH, Blocks.TORCH, Blocks.WALL_TORCH);
            } else if (block == Blocks.SOUL_TORCH) {
                offer = handleTorches(player, world, hand, hit, pos, VpParts.SOUL_TORCH, Blocks.SOUL_TORCH, Blocks.SOUL_WALL_TORCH);
            } else if (block instanceof SlabBlock) {
                offer = handleSlabs(player, world, hand, hit, pos, block);
            } else if (block == Blocks.LEVER) {
                offer = handleWallMounted(player, world, hand, hit, pos, block, (holder, face, facing) -> new LeverPart(VpParts.LEVER, holder, face, facing, false));
            } else if (block instanceof ButtonBlock) {
                offer = handleWallMounted(player, world, hand, hit, pos, block, (holder, face, facing) -> new ButtonPart(VpParts.BUTTON, holder, block, face, facing));
            } else if (block instanceof FenceBlock) {
                offer = handleFences(world, hit, pos, block);
            } else if (block == Blocks.CAKE) {
                NbtCompound blockStateTag = stack.getSubNbt("BlockStateTag");
                String bitesProperty = CakeBlock.BITES.getName();
                int bites;

                if (blockStateTag != null && blockStateTag.contains(bitesProperty, NbtElement.NUMBER_TYPE)) {
                    bites = blockStateTag.getInt(bitesProperty);
                } else {
                     bites = 0;
                }

                offer = handleSimple(world, pos, block, holder -> new CakePart(VpParts.CAKE, holder, bites));
            } else {
                for (Extension extension : extensions) {
                    offer = extension.handle(block, player, world, hand, hit, pos);
                    if (offer != null) break;
                }

                if (offer == null) {
                    return ActionResult.PASS;
                }
            }

            if (offer != null) {
                if (!world.isClient) {
                    offer.apply();
                    if (!player.getAbilities().creativeMode) {
                        stack.decrement(1);
                    }
                }

                BlockSoundGroup sounds = block.getDefaultState().getSoundGroup();
                world.playSound(player, pos, sounds.getPlaceSound(), SoundCategory.BLOCKS, (sounds.getVolume() + 1f) / 2f, sounds.getPitch() * 0.8f);

                return ActionResult.SUCCESS;
            }
        }

        return ActionResult.PASS;
    }

    public static boolean isMissingContainer(World world, BlockPos pos) {
        return !isNativeMultipart(world, pos) && MultipartUtil.get(world, pos) == null;
    }

    private static boolean isNativeMultipart(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.getBlock() instanceof NativeMultipart || NativeMultipart.LOOKUP.find(world, pos, state, null, null) != null;
    }

    private MultipartContainer.PartOffer handleSimple(World world, BlockPos pos, Block block, MultipartContainer.MultipartCreator creator) {
        if (!(block.getDefaultState().canPlaceAt(world, pos))) {
            return null;
        }

        return MultipartUtil.offerNewPart(world, pos, creator);
    }

    private MultipartContainer.PartOffer handleCarpets(World world, BlockPos pos, Block block) {
        if (!(block.getDefaultState().canPlaceAt(world, pos))) {
            return null;
        }

        DyeColor color = ((DyedCarpetBlock) block).getDyeColor();
        return MultipartUtil.offerNewPart(
            world, pos,
            holder -> new CarpetPart(VpParts.CARPET_PARTS.get(color), holder, color)
        );
    }

    private MultipartContainer.PartOffer handleTorches(PlayerEntity player, World world, Hand hand, BlockHitResult hit, BlockPos pos, PartDefinition definition, Block groundBlock, Block wallBlock) {
        if (hit.getSide().getAxis().isHorizontal()) {
            ItemPlacementContext ctx = new ItemPlacementContext(new ItemUsageContext(player, hand, hit));
            BlockState torchState = wallBlock.getPlacementState(ctx);
            if (torchState == null || !torchState.canPlaceAt(world, pos)) {
                return null;
            }
        } else if (!groundBlock.getDefaultState().canPlaceAt(world, pos)) {
            return null;
        }

        TorchPart.Facing facing = TorchPart.Facing.of(hit.getSide());
        return MultipartUtil.offerNewPart(
            world, pos, holder -> new TorchPart(definition, holder, groundBlock, wallBlock, facing)
        );
    }

    private MultipartContainer.PartOffer handleSlabs(PlayerEntity player, World world, Hand hand, BlockHitResult hit, BlockPos pos, Block block) {
        return handleAnySlabs(player, world, hand, hit, pos, block, (holder, top) -> new SlabPart(VpParts.SLAB, holder, (SlabBlock) block, top));
    }

    private MultipartContainer.PartOffer handleAnySlabs(PlayerEntity player, World world, Hand hand, BlockHitResult hit, BlockPos pos, Block block, BiFunction<MultipartHolder, Boolean, AbstractPart> factory) {
        BlockState hitState = world.getBlockState(hit.getBlockPos());
        if (block == hitState.getBlock() && hit.getSide().getAxis().isVertical()) {
            Direction side = hit.getSide();
            if (side == Direction.UP && hitState.get(SlabBlock.TYPE) == SlabType.BOTTOM) return null;
            if (side == Direction.DOWN && hitState.get(SlabBlock.TYPE) == SlabType.TOP) return null;
        }

        BlockPos originalPos = pos.offset(hit.getSide().getOpposite());
        MultipartContainer.PartOffer offer = null;
        if (!isMissingContainer(world, originalPos) && hit.getSide().getAxis().isVertical()) {
            offer = MultipartUtil.offerNewPart(
                world, originalPos, holder -> factory.apply(holder, hit.getSide() == Direction.UP)
            );
        }

        if (offer == null && !isMissingContainer(world, pos)) {
            BlockState placementState = block.getPlacementState(new ItemPlacementContext(new ItemUsageContext(player, hand, hit)));
            if (placementState == null || placementState.get(SlabBlock.TYPE) == SlabType.DOUBLE)
                return null;

            offer = MultipartUtil.offerNewPart(
                world, pos, holder -> factory.apply(holder, placementState.get(SlabBlock.TYPE) == SlabType.TOP)
            );
        }

        return offer;
    }

    private MultipartContainer.PartOffer handleWallMounted(PlayerEntity player, World world, Hand hand, BlockHitResult hit, BlockPos pos, Block block, WallMountedPartFactory factory) {
        ItemPlacementContext ctx = new ItemPlacementContext(new ItemUsageContext(player, hand, hit));
        Direction[] directions = ctx.getPlacementDirections();
        for (Direction direction : directions) {
            BlockState vanillaState;
            if (direction.getAxis().isVertical()) {
                vanillaState = block.getDefaultState()
                    .with(LeverBlock.FACE, direction == Direction.UP ? WallMountLocation.CEILING : WallMountLocation.FLOOR)
                    .with(LeverBlock.FACING, ctx.getHorizontalPlayerFacing());
            } else {
                vanillaState = block.getDefaultState()
                    .with(LeverBlock.FACE, WallMountLocation.WALL)
                    .with(LeverBlock.FACING, direction.getOpposite());
            }

            if (vanillaState.canPlaceAt(world, pos)) {
                return MultipartUtil.offerNewPart(
                    world, pos,
                    holder -> factory.create(
                        holder,
                        vanillaState.get(LeverBlock.FACE),
                        vanillaState.get(LeverBlock.FACING)
                    )
                );
            }
        }

        return null;
    }

    private MultipartContainer.PartOffer handleFences(World world, BlockHitResult hit, BlockPos pos, Block block) {
        if (!(block.getDefaultState().canPlaceAt(world, pos))) {
            return null;
        }

        MultipartContainer.PartOffer offer = null;
        MultipartContainer.MultipartCreator creator = holder -> new FencePart(VpParts.FENCE, holder, block);

        if (!isMissingContainer(world, hit.getBlockPos())) {
            offer = MultipartUtil.offerNewPart(world, hit.getBlockPos(), creator);
        }

        if (offer == null && !isMissingContainer(world, pos)) {
            offer = MultipartUtil.offerNewPart(world, pos, creator);
        }

        return offer;
    }

    @FunctionalInterface
    private interface WallMountedPartFactory {
        AbstractPart create(MultipartHolder holder, WallMountLocation face, Direction facing);
    }

    @FunctionalInterface
    public interface Extension {
        @Nullable
        MultipartContainer.PartOffer handle(Block block, PlayerEntity player, World world, Hand hand, BlockHitResult hit, BlockPos pos);
    }
}
