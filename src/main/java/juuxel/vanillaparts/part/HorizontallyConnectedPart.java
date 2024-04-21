/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package juuxel.vanillaparts.part;

import alexiil.mc.lib.multipart.api.AbstractPart;
import alexiil.mc.lib.multipart.api.MultipartEventBus;
import alexiil.mc.lib.multipart.api.MultipartHolder;
import alexiil.mc.lib.multipart.api.PartDefinition;
import alexiil.mc.lib.multipart.api.event.PartAddedEvent;
import alexiil.mc.lib.multipart.api.event.PartRemovedEvent;
import alexiil.mc.lib.multipart.api.render.PartModelKey;
import alexiil.mc.lib.net.IMsgReadCtx;
import alexiil.mc.lib.net.IMsgWriteCtx;
import alexiil.mc.lib.net.NetByteBuf;
import alexiil.mc.lib.net.NetIdDataK;
import alexiil.mc.lib.net.ParentNetIdSingle;
import com.google.common.collect.ImmutableMap;
import juuxel.blockstoparts.api.model.DynamicVanillaModelKey;
import juuxel.vanillaparts.util.NbtKeys;
import juuxel.vanillaparts.util.NbtUtil;
import juuxel.vanillaparts.util.Util;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalConnectingBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.EmptyBlockView;

public abstract class HorizontallyConnectedPart extends VanillaPart {
    public static final ParentNetIdSingle<HorizontallyConnectedPart> NET_HORIZONTALLY_CONNECTED;
    public static final NetIdDataK<HorizontallyConnectedPart> CONNECTION_DATA;
    private static final Direction[] HORIZONTAL_DIRECTIONS = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };
    private static final ImmutableMap<Direction, BooleanProperty> DIRECTION_PROPERTIES =
        ImmutableMap.of(
            Direction.NORTH, HorizontalConnectingBlock.NORTH,
            Direction.EAST, HorizontalConnectingBlock.EAST,
            Direction.SOUTH, HorizontalConnectingBlock.SOUTH,
            Direction.WEST, HorizontalConnectingBlock.WEST
        );

    static {
        NET_HORIZONTALLY_CONNECTED = NET_ID.subType(HorizontallyConnectedPart.class, "vanilla_parts:horizontally_connected");
        CONNECTION_DATA = NET_HORIZONTALLY_CONNECTED.idData("connection_data").setReceiver(HorizontallyConnectedPart::updateConnections);
    }

    private boolean north = false;
    private boolean east = false;
    private boolean south = false;
    private boolean west = false;
    private boolean calculateConnections = false;
    protected final HorizontalConnectingBlock block;

    // Automatically calculates connections
    public HorizontallyConnectedPart(PartDefinition definition, MultipartHolder holder, Block block) {
        super(definition, holder);
        this.block = (HorizontalConnectingBlock) block;
        this.calculateConnections = true;
    }

    public HorizontallyConnectedPart(PartDefinition definition, MultipartHolder holder, Block block, boolean north, boolean east, boolean south, boolean west) {
        this(definition, holder, block);
        this.north = north;
        this.east = east;
        this.south = south;
        this.west = west;
    }

    public HorizontalConnectingBlock getBlock() {
        return block;
    }

    @Override
    public BlockState getBlockState() {
        return block.getDefaultState()
            .with(HorizontalConnectingBlock.NORTH, north)
            .with(HorizontalConnectingBlock.EAST, east)
            .with(HorizontalConnectingBlock.SOUTH, south)
            .with(HorizontalConnectingBlock.WEST, west);
    }

    @Override
    public VoxelShape getOutlineShape() {
        return getBlockState().getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
    }

    @Override
    public VoxelShape getCollisionShape() {
        return getBlockState().getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
    }

    @Override
    public PartModelKey getModelKey() {
        return new DynamicVanillaModelKey(this);
    }

    protected abstract boolean canConnectTo(BlockPos neighborPos, Direction d);

    public boolean isBlocked(Direction d) {
        BooleanProperty directionProperty = DIRECTION_PROPERTIES.get(d);
        if (directionProperty == null) {
            throw new IllegalArgumentException("Trying to check vertical connections in isBlocked()!");
        }
        VoxelShape fenceShape = block.getDefaultState().with(directionProperty, true).getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
        for (AbstractPart part : holder.getContainer().getAllParts()) {
            if (part == this || part.canOverlapWith(this)) continue;
            if (VoxelShapes.matchesAnywhere(fenceShape, part.getShape(), BooleanBiFunction.AND)) {
                return true;
            }
        }

        return false;
    }

    private boolean getConnection(Direction d) {
        switch (d) {
            case NORTH:
                return north;
            case SOUTH:
                return south;
            case WEST:
                return west;
            case EAST:
            default:
                return east;
        }
    }

    private void setConnection(Direction d, boolean connected) {
        switch (d) {
            case NORTH:
                north = connected;
                break;
            case SOUTH:
                south = connected;
                break;
            case WEST:
                west = connected;
                break;
            case EAST:
                east = connected;
                break;
        }
    }

    private void updateConnections(NetByteBuf buf, IMsgReadCtx ctx) {
        north = buf.readBoolean();
        east = buf.readBoolean();
        south = buf.readBoolean();
        west = buf.readBoolean();
        holder.getContainer().redrawIfChanged();
    }

    @Override
    protected void onNeighborUpdate(BlockPos neighborPos) {
        Direction side = Util.compare(getPos(), neighborPos);
        if (side.getAxis().isHorizontal()) {
            recalculateConnection(side, neighborPos);
        }
    }

    private void recalculateConnections() {
        BlockPos pos = getPos();
        BlockPos.Mutable mut = pos.mutableCopy();
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            mut.move(direction);
            recalculateConnection(direction, mut);
            mut.set(pos);
        }
    }

    private void recalculateConnection(Direction side, BlockPos neighborPos) {
        boolean canConnect = canConnectTo(neighborPos, side) && !isBlocked(side);
        if (getConnection(side) != canConnect) {
            setConnection(side, canConnect);
            holder.getContainer().sendNetworkUpdate(this, CONNECTION_DATA, (obj, buf, ctx) -> {
                buf.writeBoolean(north).writeBoolean(east).writeBoolean(south).writeBoolean(west);
            });
        }
    }

    @Override
    public void onAdded(MultipartEventBus bus) {
        super.onAdded(bus);
        bus.addListener(this, PartAddedEvent.class, event -> {
            if (event.part != this) {
                recalculateConnections();
            } else if (calculateConnections) {
                recalculateConnections();
                calculateConnections = false; // you never know
            }
        });
        bus.addListener(this, PartRemovedEvent.class, event -> {
            holder.getContainer().recalculateShape();
            recalculateConnections();
        });
    }

    @Override
    public NbtCompound toTag() {
        return Util.with(super.toTag(), tag -> {
            NbtUtil.putRegistryEntry(tag, NbtKeys.BLOCK_ID, Registries.BLOCK, block);
            tag.putBoolean(NbtKeys.NORTH, north);
            tag.putBoolean(NbtKeys.EAST, east);
            tag.putBoolean(NbtKeys.SOUTH, south);
            tag.putBoolean(NbtKeys.WEST, west);
        });
    }

    @Override
    public void writeCreationData(NetByteBuf buf, IMsgWriteCtx ctx) {
        super.writeCreationData(buf, ctx);
        buf.writeIdentifier(Registries.BLOCK.getId(block));
        buf.writeBoolean(north).writeBoolean(east).writeBoolean(south).writeBoolean(west);
    }
}
