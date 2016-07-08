package mcjty.lib.varia;

import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * Works on a ISidedInventory but just passes null to the side so only
 * one instance is needed for side == null as well as the six sides
 */
public class NullSidedInvWrapper implements IItemHandlerModifiable {
    private final ISidedInventory inv;

    // This version allows a 'null' facing
    public NullSidedInvWrapper(ISidedInventory inv) {
        this.inv = inv;
    }

    public static int getSlot(ISidedInventory inv, int slot) {
        int[] slots = inv.getSlotsForFace(null);
        if (slot < slots.length) {
            return slots[slot];
        }
        return -1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        NullSidedInvWrapper that = (NullSidedInvWrapper) o;

        if (inv != null ? !inv.equals(that.inv) : that.inv != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return inv != null ? inv.hashCode() : 0;
    }

    @Override
    public int getSlots() {
        return inv.getSlotsForFace(null).length;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        int i = getSlot(inv, slot);
        return i == -1 ? null : inv.getStackInSlot(i);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {

        if (stack == null) {
            return null;
        }

        slot = getSlot(inv, slot);

        if (slot == -1) {
            return stack;
        }

        if (!inv.isItemValidForSlot(slot, stack) || !inv.canInsertItem(slot, stack, null)) {
            return stack;
        }

        ItemStack stackInSlot = inv.getStackInSlot(slot);

        int m;
        if (stackInSlot != null) {
            if (!ItemHandlerHelper.canItemStacksStack(stack, stackInSlot)) {
                return stack;
            }

            m = Math.min(stack.getMaxStackSize(), inv.getInventoryStackLimit()) - stackInSlot.stackSize;

            if (stack.stackSize <= m) {
                if (!simulate) {
                    ItemStack copy = stack.copy();
                    copy.stackSize += stackInSlot.stackSize;
                    inv.setInventorySlotContents(slot, copy);
                }

                return null;
            } else {
                // copy the stack to not modify the original one
                stack = stack.copy();
                if (!simulate) {
                    ItemStack copy = stack.splitStack(m);
                    copy.stackSize += stackInSlot.stackSize;
                    inv.setInventorySlotContents(slot, copy);
                    return stack;
                } else {
                    stack.stackSize -= m;
                    return stack;
                }
            }
        } else {
            m = Math.min(stack.getMaxStackSize(), inv.getInventoryStackLimit());
            if (m < stack.stackSize) {
                // copy the stack to not modify the original one
                stack = stack.copy();
                if (!simulate) {
                    inv.setInventorySlotContents(slot, stack.splitStack(m));
                    return stack;
                } else {
                    stack.stackSize -= m;
                    return stack;
                }
            } else {
                if (!simulate) {
                    inv.setInventorySlotContents(slot, stack);
                }
                return null;
            }
        }

    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        int i = getSlot(inv, slot);
        inv.setInventorySlotContents(i, stack);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount == 0) {
            return null;
        }

        int slot1 = getSlot(inv, slot);

        if (slot1 == -1) {
            return null;
        }

        ItemStack stackInSlot = inv.getStackInSlot(slot1);

        if (stackInSlot == null) {
            return null;
        }

        if (!inv.canExtractItem(slot1, stackInSlot, null)) {
            return null;
        }

        if (simulate) {
            if (stackInSlot.stackSize < amount) {
                return stackInSlot.copy();
            } else {
                ItemStack copy = stackInSlot.copy();
                copy.stackSize = amount;
                return copy;
            }
        } else {
            int m = Math.min(stackInSlot.stackSize, amount);
            return inv.decrStackSize(slot1, m);
        }
    }
}