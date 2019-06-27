package mcjty.lib.blocks;

import mcjty.lib.McJtyLib;
import mcjty.lib.api.smartwrench.SmartWrench;
import mcjty.lib.base.GeneralConfig;
import mcjty.lib.builder.BlockBuilder;
import mcjty.lib.builder.InformationString;
import mcjty.lib.compat.CofhApiItemCompatibility;
import mcjty.lib.compat.theoneprobe.TOPInfoProvider;
import mcjty.lib.compat.waila.WailaInfoProvider;
import mcjty.lib.multipart.IPartBlock;
import mcjty.lib.multipart.PartSlot;
import mcjty.lib.tileentity.GenericTileEntity;
import mcjty.lib.varia.OrientationTools;
import mcjty.lib.varia.WrenchChecker;
import mcjty.lib.varia.WrenchUsage;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.state.IProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraftforge.common.ToolType;
import net.minecraftforge.fml.network.NetworkHooks;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

//@Optional.InterfaceList({
//        @Optional.Interface(iface = "mcjty.lib.compat.waila.WailaInfoProvider", modid = "waila"),
//        @Optional.Interface(iface = "mcjty.lib.compat.theoneprobe.TOPInfoProvider", modid = "theoneprobe")
//})
public class BaseBlockNew extends Block implements WailaInfoProvider, TOPInfoProvider, IPartBlock {

    private final boolean infusable;
    private final boolean hasGui;
    private final Supplier<TileEntity> tileEntitySupplier;
    private final InformationString informationString;
    private final InformationString informationStringWithShift;
    private final ToolType toolType;
    private final int harvestLevel;

    public static final IProperty<?>[] HORIZ_PROPERTIES = new IProperty[]{BlockStateProperties.HORIZONTAL_FACING};
    public static final IProperty<?>[] ROTATING_PROPERTIES = new IProperty[]{BlockStateProperties.FACING};
    public static final IProperty<?>[] NONE_PROPERTIES = new IProperty[0];

    public BaseBlockNew(String name, BlockBuilder builder) {
        super(builder.getProperties());
        setRegistryName(name);
        this.infusable = builder.isInfusable();
        this.hasGui = builder.isHasGui();
        this.tileEntitySupplier = builder.getTileEntitySupplier();
        this.informationString = builder.getInformationString();
        this.informationStringWithShift = builder.getInformationStringWithShift();
        this.toolType = builder.getToolType();
        this.harvestLevel = builder.getHarvestLevel();
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable IBlockReader world, List<ITextComponent> tooltip, ITooltipFlag advanced) {
        intAddInformation(stack, tooltip);

        InformationString i = informationString;
        // @todo 1.14: check if this actually works!
        if (McJtyLib.proxy.isShiftKeyDown()) {
            i = informationStringWithShift;
        }
//        if ((Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))) {
//            i = informationStringWithShift;
//        }
        if (i != null) {
            addLocalizedInformation(i, stack, tooltip);
        }
    }

    private void intAddInformation(ItemStack itemStack, List<ITextComponent> list) {
        CompoundNBT tagCompound = itemStack.getTag();
        if (tagCompound != null) {
            if (tagCompound.contains("Energy")) {
                long energy = tagCompound.getLong("Energy");
                list.add(new StringTextComponent(TextFormatting.GREEN + "Energy: " + energy + " rf"));
            }
            if (isInfusable()) {
                int infused = tagCompound.getInt("infused");
                int pct = infused * 100 / GeneralConfig.maxInfuse.get();
                list.add(new StringTextComponent(TextFormatting.YELLOW + "Infused: " + pct + "%"));
            }

            if (GeneralConfig.manageOwnership.get() && tagCompound.contains("owner")) {
                String owner = tagCompound.getString("owner");
                int securityChannel = -1;
                if (tagCompound.contains("secChannel")) {
                    securityChannel = tagCompound.getInt("secChannel");
                }

                if (securityChannel == -1) {
                    list.add(new StringTextComponent(TextFormatting.YELLOW + "Owned by: " + owner));
                } else {
                    list.add(new StringTextComponent(TextFormatting.YELLOW + "Owned by: " + owner + " (channel " + securityChannel + ")"));
                }

                if (!tagCompound.contains("idM")) {
                    list.add(new StringTextComponent(TextFormatting.RED + "Warning! Ownership not correctly set! Please place block again!"));
                }
            }
        }
    }


    // This if this block was activated with a wrench
    private WrenchUsage testWrenchUsage(BlockPos pos, PlayerEntity player) {
        ItemStack itemStack = player.getHeldItem(Hand.MAIN_HAND);
        WrenchUsage wrenchUsed = WrenchUsage.NOT;
        if (!itemStack.isEmpty()) {
            Item item = itemStack.getItem();
            if (item != null) {
                wrenchUsed = getWrenchUsage(pos, player, itemStack, wrenchUsed, item);
            }
        }
        if (wrenchUsed == WrenchUsage.NORMAL && player.isSneaking()) {
            wrenchUsed = WrenchUsage.SNEAKING;
        }
        return wrenchUsed;
    }

    protected WrenchUsage getWrenchUsage(BlockPos pos, PlayerEntity player, ItemStack itemStack, WrenchUsage wrenchUsed, Item item) {
        if (item instanceof SmartWrench) {
            switch(((SmartWrench)item).getMode(itemStack)) {
                case MODE_WRENCH: return WrenchUsage.NORMAL;
                case MODE_SELECT: return player.isSneaking() ? WrenchUsage.SNEAK_SELECT : WrenchUsage.SELECT;
                default:          throw new RuntimeException("SmartWrench in unknown mode!");
            }
        } else if (McJtyLib.cofhapiitem && CofhApiItemCompatibility.isToolHammer(item)) {
            return CofhApiItemCompatibility.getWrenchUsage(item, itemStack, player, pos);
        } else if (WrenchChecker.isAWrench(item)) {
            return WrenchUsage.NORMAL;
        }
        return wrenchUsed;
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean onBlockActivated(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult result) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof GenericTileEntity) {
            if (((GenericTileEntity) te).onBlockActivated(state, player, hand, result)) {
                return true;
            }
        }
        ItemStack heldItem = player.getHeldItem(hand);
        // @todo
//        if (handleModule(world, pos, state, player, hand, heldItem, result)) {
//            return true;
//        }
        WrenchUsage wrenchUsed = testWrenchUsage(pos, player);
        switch (wrenchUsed) {
            case NOT:          return openGui(world, pos.getX(), pos.getY(), pos.getZ(), player);
            case NORMAL:       return wrenchUse(world, pos, result.getFace(), player);
            case SNEAKING:     return wrenchSneak(world, pos, player);
            case DISABLED:     return wrenchDisabled(world, pos, player);
            case SELECT:       return wrenchSelect(world, pos, player);
            case SNEAK_SELECT: return wrenchSneakSelect(world, pos, player);
        }
        return false;
    }

    protected boolean wrenchUse(World world, BlockPos pos, Direction side, PlayerEntity player) {
        TileEntity tileEntity = world.getTileEntity(pos);
        if (tileEntity instanceof GenericTileEntity) {
            if (!((GenericTileEntity) tileEntity).wrenchUse(world, pos, side, player)) {
                rotate(world.getBlockState(pos), world, pos, Rotation.CLOCKWISE_90);
            }
        } else {
            rotate(world.getBlockState(pos), world, pos, Rotation.CLOCKWISE_90);
        }
        return true;
    }

    protected boolean wrenchSneak(World world, BlockPos pos, PlayerEntity player) {
        // @todo
//        breakAndRemember(world, player, pos);
        return true;
    }

    protected boolean wrenchDisabled(World world, BlockPos pos, PlayerEntity player) {
        return false;
    }

    protected boolean wrenchSelect(World world, BlockPos pos, PlayerEntity player) {
        return false;
    }

    protected boolean wrenchSneakSelect(World world, BlockPos pos, PlayerEntity player) {
        return false;
    }

    protected boolean openGui(World world, int x, int y, int z, PlayerEntity player) {
        if (hasGui) {
            if (world.isRemote) {
                return true;
            }
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (!(te instanceof INamedContainerProvider)) {
                return true;
            }
            if (checkAccess(world, player, te)) {
                return true;
            }
            NetworkHooks.openGui((ServerPlayerEntity) player, (INamedContainerProvider) te, te.getPos());
            return true;
        }
        return false;
    }


    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (!world.isRemote && GeneralConfig.manageOwnership.get()) {
            setOwner(world, pos, placer);
        }

        TileEntity te = world.getTileEntity(pos);
        if (te instanceof GenericTileEntity) {
            GenericTileEntity genericTileEntity = (GenericTileEntity) te;
            genericTileEntity.onBlockPlacedBy(world, pos, state, placer, stack);
        }

        // @todo
//        if (needsRedstoneCheck()) {
//            checkRedstone(world, pos);
//        }
    }

    protected void setOwner(World world, BlockPos pos, LivingEntity entity) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof GenericTileEntity && entity instanceof PlayerEntity) {
            GenericTileEntity genericTileEntity = (GenericTileEntity) te;
            PlayerEntity player = (PlayerEntity) entity;
            genericTileEntity.setOwner(player);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean eventReceived(BlockState state, World worldIn, BlockPos pos, int id, int param) {
        if (hasTileEntity(state)) {
            super.eventReceived(state, worldIn, pos, id, param);
            TileEntity tileentity = worldIn.getTileEntity(pos);
            return tileentity == null ? false : tileentity.receiveClientEvent(id, param);
        } else {
            return super.eventReceived(state, worldIn, pos, id, param);
        }
    }

    protected boolean checkAccess(World world, PlayerEntity player, TileEntity te) {
        if (te instanceof GenericTileEntity) {
            ((GenericTileEntity) te).checkAccess(player);
        }
        return false;
    }

    @Nullable
    @Override
    public ToolType getHarvestTool(BlockState state) {
        return toolType;
    }

    @Override
    public int getHarvestLevel(BlockState state) {
        return harvestLevel;
    }

    @Override
    public boolean hasTileEntity(BlockState state) {
        return tileEntitySupplier != null;
    }

    public RotationType getRotationType() {
        return RotationType.ROTATION;
    }

    public boolean isInfusable() {
        return infusable;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        if (tileEntitySupplier == null) {
            return null;
        } else {
            return tileEntitySupplier.get();
        }
    }

    @Nonnull
    @Override
    public PartSlot getSlotFromState(World world, BlockPos pos, BlockState newState) {
        return PartSlot.NONE;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onReplaced(BlockState state, @Nonnull World world, @Nonnull BlockPos pos, @Nonnull BlockState newstate, boolean isMoving) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof GenericTileEntity) {
            if (!world.isRemote) {
                GenericTileEntity genericTileEntity = (GenericTileEntity) te;
                genericTileEntity.onReplaced(world, pos, state);
            }
        }

        super.onReplaced(state, world, pos, newstate, isMoving);
    }

    protected IProperty<?>[] getProperties() {
        return getProperties(getRotationType());
    }

    public static IProperty<?>[] getProperties(RotationType rotationType) {
        switch (rotationType) {
            case HORIZROTATION:
                return HORIZ_PROPERTIES;
            case ROTATION:
                return ROTATING_PROPERTIES;
            case NONE:
            default:
                return NONE_PROPERTIES;
        }
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        for (IProperty<?> property : getProperties()) {
            builder.add(property);
        }
    }


    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        PlayerEntity placer = context.getPlayer();
        BlockPos pos = context.getPos();
        BlockState state = super.getStateForPlacement(context);
        switch (getRotationType()) {
            case HORIZROTATION:
                return state.with(BlockStateProperties.HORIZONTAL_FACING, placer.getHorizontalFacing().getOpposite());
            case ROTATION:
                return state.with(BlockStateProperties.FACING, OrientationTools.getFacingFromEntity(pos, placer));
            default:
                return state;
        }
    }

    protected Direction getOrientation(BlockPos pos, LivingEntity entity) {
        switch (getRotationType()) {
            case HORIZROTATION:
                return OrientationTools.determineOrientationHoriz(entity);
            case ROTATION:
                return OrientationTools.determineOrientation(pos, entity);
            default:
                return null;
        }
    }

    public Direction getFrontDirection(BlockState state) {
        switch (getRotationType()) {
            case HORIZROTATION:
                return state.get(BlockStateProperties.HORIZONTAL_FACING);
            case ROTATION:
                return state.get(BlockStateProperties.FACING);
            default:
                return Direction.NORTH;
        }
    }

    @Override
    public BlockState rotate(BlockState state, IWorld world, BlockPos pos, Rotation rot) {
        switch (getRotationType()) {
            case HORIZROTATION:
                state = state.with(BlockStateProperties.HORIZONTAL_FACING, rot.rotate(state.get(BlockStateProperties.HORIZONTAL_FACING)));
            case ROTATION:
                state = state.with(BlockStateProperties.FACING, rot.rotate(state.get(BlockStateProperties.FACING)));
            case NONE:
        }
        TileEntity tileEntity = world.getTileEntity(pos);
        if (tileEntity instanceof GenericTileEntity) {
            ((GenericTileEntity) tileEntity).rotateBlock(rot);
        }
        return state;
    }

    public Direction getRightDirection(BlockState state) {
        return getFrontDirection(state).rotateYCCW();
    }

    public Direction getLeftDirection(BlockState state) {
        return getFrontDirection(state).rotateY();
    }

    public static Direction getFrontDirection(RotationType rotationType, BlockState state) {
        switch (rotationType) {
            case HORIZROTATION:
                return OrientationTools.getOrientationHoriz(state);
            case ROTATION:
                return OrientationTools.getOrientation(state);
            default:
                return Direction.SOUTH;
        }
    }

    private static final Pattern COMPILE = Pattern.compile("@", Pattern.LITERAL);

    public static void addLocalizedInformation(InformationString informationString, ItemStack stack, List<ITextComponent> tooltip) {
        if (informationString != null) {
            Object[] parameters = new Object[informationString.getInformationStringParameters().size()];
            for (int i = 0 ; i < parameters.length ; i++) {
                parameters[i] = informationString.getInformationStringParameters().get(i).apply(stack);
            }
            String translated = I18n.format(informationString.getString(), parameters);
            translated = COMPILE.matcher(translated).replaceAll("\u00a7");
            String[] split = StringUtils.split(translated, "\n");
            for (String s : split) {
                tooltip.add(new StringTextComponent(s));
            }
        }
    }
}