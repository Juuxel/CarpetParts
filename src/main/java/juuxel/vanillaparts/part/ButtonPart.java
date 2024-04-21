/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package juuxel.vanillaparts.part;

import alexiil.mc.lib.multipart.api.MultipartEventBus;
import alexiil.mc.lib.multipart.api.MultipartHolder;
import alexiil.mc.lib.multipart.api.PartDefinition;
import alexiil.mc.lib.multipart.api.event.PartEventEntityCollide;
import alexiil.mc.lib.multipart.api.event.PartTickEvent;
import alexiil.mc.lib.multipart.api.property.MultipartProperties;
import alexiil.mc.lib.multipart.api.property.MultipartPropertyContainer;
import alexiil.mc.lib.net.IMsgReadCtx;
import alexiil.mc.lib.net.IMsgWriteCtx;
import alexiil.mc.lib.net.InvalidInputDataException;
import alexiil.mc.lib.net.NetByteBuf;
import com.mojang.datafixers.DataFixUtils;
import juuxel.blockstoparts.api.category.CategorySet;
import juuxel.vanillaparts.mixin.AbstractButtonBlockAccessor;
import juuxel.vanillaparts.util.NbtKeys;
import juuxel.vanillaparts.util.NbtUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.enums.WallMountLocation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Direction;

import java.util.List;

// TODO: Buttons power blocks through other parts
public class ButtonPart extends WallMountedRedstonePart {
    private final Block block;
    private final AbstractButtonBlockAccessor buttonBlock;
    private int timer = 0;

    public ButtonPart(PartDefinition definition, MultipartHolder holder, Block buttonBlock, WallMountLocation face, Direction facing) {
        super(definition, holder, face, facing, false);
        this.block = buttonBlock;
        this.buttonBlock = (AbstractButtonBlockAccessor) buttonBlock;
    }

    public static ButtonPart fromNbt(PartDefinition definition, MultipartHolder holder, NbtCompound nbt) {
        Block block = NbtUtil.getRegistryEntry(nbt, NbtKeys.BLOCK_ID, Registries.BLOCK);
        var face = NbtUtil.getEnum(nbt, NbtKeys.FACE, WallMountLocation.class);
        var facing = NbtUtil.getEnum(nbt, NbtKeys.FACING, Direction.class);
        ButtonPart part = new ButtonPart(definition, holder, block, face, facing);
        part.powered = nbt.getBoolean(NbtKeys.POWERED);
        part.timer = nbt.getInt(NbtKeys.TIMER);
        return part;
    }

    public static ButtonPart fromBuf(PartDefinition definition, MultipartHolder holder, NetByteBuf buf, IMsgReadCtx ctx) throws InvalidInputDataException {
        Block block = Registries.BLOCK.get(buf.readIdentifierSafe());
        var face = buf.readEnumConstant(WallMountLocation.class);
        var facing = buf.readEnumConstant(Direction.class);
        return new ButtonPart(definition, holder, block, face, facing);
    }

    @Override
    public NbtCompound toTag() {
        return DataFixUtils.make(super.toTag(), tag -> {
            NbtUtil.putRegistryEntry(tag, NbtKeys.BLOCK_ID, Registries.BLOCK, block);
            NbtUtil.putEnum(tag, NbtKeys.FACE, face);
            NbtUtil.putEnum(tag, NbtKeys.FACING, facing);
            tag.putBoolean(NbtKeys.POWERED, powered);
            tag.putInt(NbtKeys.TIMER, timer);
        });
    }

    @Override
    public void writeCreationData(NetByteBuf buffer, IMsgWriteCtx ctx) {
        super.writeCreationData(buffer, ctx);
        buffer.writeIdentifier(Registries.BLOCK.getId(block));
        buffer.writeEnumConstant(face);
        buffer.writeEnumConstant(facing);
    }

    @Override
    public BlockState getBlockState() {
        return block.getDefaultState()
            .with(ButtonBlock.FACE, face)
            .with(ButtonBlock.FACING, facing)
            .with(ButtonBlock.POWERED, powered);
    }

    private void tick() {
        if (timer > 0) {
            timer--;
        }

        if (powered && timer <= 0) {
            if (buttonBlock.isWooden()) {
                tickWooden();
            } else {
                powered = false;
                if (!getWorld().isClient) {
                    updateRedstoneLevels();
                    buttonBlock.callPlayClickSound(null, getWorld(), getPos(), false);
                }
                updateListeners();
            }
        }
    }

    private void tickWooden() {
        List<? extends Entity> entities = getWorld().getNonSpectatingEntities(ProjectileEntity.class, getShape().getBoundingBox().offset(getPos()));
        boolean hasEntities = !entities.isEmpty();
        if (hasEntities != powered) {
            powered = hasEntities;
            if (!getWorld().isClient) {
                updateRedstoneLevels();
                buttonBlock.callPlayClickSound(null, getWorld(), getPos(), hasEntities);
            }
            updateListeners();
            if (powered) {
                timer = buttonBlock.getPressTicks();
            }
        }
    }

    @Override
    public ActionResult onUse(PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!powered) {
            timer = buttonBlock.getPressTicks();
            powered = true;
            if (!player.getWorld().isClient) {
                updateRedstoneLevels();
            }
            buttonBlock.callPlayClickSound(player, player.getWorld(), hit.getBlockPos(), true);
            updateListeners();
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void onAdded(MultipartEventBus bus) {
        super.onAdded(bus);
        updateRedstoneLevels();
        MultipartPropertyContainer props = this.holder.getContainer().getProperties();
        props.setValue(this, MultipartProperties.CAN_EMIT_REDSTONE, true);
        bus.addContextlessListener(this, PartTickEvent.class, this::tick);
        bus.addListener(this, PartEventEntityCollide.class, event -> tickWooden());
    }

    @Override
    protected void addCategories(CategorySet.Builder builder) {
        builder.add(VpCategories.BUTTONS);
        builder.add(VpCategories.REDSTONE_COMPONENTS);
    }
}
