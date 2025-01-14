package mcjty.lib.varia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class InventoryTools {
    /**
     * Get the size of the inventory
     */
    public static int getInventorySize(BlockEntity tileEntity) {
        if (tileEntity == null) {
            return 0;
        }

        return tileEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).map(IItemHandler::getSlots).orElse(0);
    }

    public static boolean isInventory(BlockEntity te) {
        return te != null && te.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent();
    }

    /**
     * Return a stream of items in an inventory matching the predicate
     */
    public static Stream<ItemStack> getItems(BlockEntity tileEntity, Predicate<ItemStack> predicate) {
        Stream.Builder<ItemStack> builder = Stream.builder();

        if (tileEntity != null) {
            tileEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack itemStack = handler.getStackInSlot(i);
                    if (!itemStack.isEmpty() && predicate.test(itemStack)) {
                        builder.add(itemStack);
                    }
                }
            });
        }
        return builder.build();
    }

    /**
     * Return the first item in an inventory matching the predicate
     */
    @Nonnull
    public static ItemStack getFirstMatchingItem(BlockEntity tileEntity, Predicate<ItemStack> predicate) {
        if (tileEntity != null) {
            return tileEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).map(handler -> {
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack itemStack = handler.getStackInSlot(i);
                    if (!itemStack.isEmpty() && predicate.test(itemStack)) {
                        return itemStack;
                    }
                }
                return ItemStack.EMPTY;
            }).orElse(ItemStack.EMPTY);
        }
        return ItemStack.EMPTY;
    }

    /**
     * Insert an item into an inventory at the given direction. Supports IItemHandler as
     * well as IInventory. Returns an itemstack with whatever could not be inserted or empty item
     * on succcess.
     */
    @Nonnull
    public static ItemStack insertItem(Level world, BlockPos pos, Direction direction, @Nonnull ItemStack s) {
        BlockEntity te = world.getBlockEntity(direction == null ? pos : pos.relative(direction));
        if (te != null) {
            Direction opposite = direction == null ? null : direction.getOpposite();
            return te.getCapability(ForgeCapabilities.ITEM_HANDLER, opposite)
                    .map(handler -> ItemHandlerHelper.insertItem(handler, s, false))
                    .orElse(ItemStack.EMPTY);
        }
        return s;
    }

    public static boolean isItemStackConsideredEqual(ItemStack result, ItemStack itemstack1) {
        // @todo 1.20 isSameItemSameTags?
        return !itemstack1.isEmpty() && itemstack1.getItem() == result.getItem() && (result.getDamageValue() == itemstack1.getDamageValue()) && ItemStack.isSameItemSameTags(result, itemstack1);
    }

    @Nonnull
    public static ItemStack insertItemRanged(IItemHandler dest, @Nonnull ItemStack stack, int start, int stop, boolean simulate) {
        if (dest == null || stack.isEmpty())
            return stack;

        for (int i = start; i < stop; i++) {
            stack = dest.insertItem(i, stack, simulate);
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }

        return stack;
    }
}
