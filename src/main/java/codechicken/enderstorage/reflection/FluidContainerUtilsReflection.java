package codechicken.enderstorage.reflection;

import codechicken.enderstorage.item.ItemEnderStorage;
import codechicken.enderstorage.tile.TileEnderTank;
import mekanism.common.tile.TileEntityFluidTank;
import mekanism.common.util.FluidContainerUtils;
import mekanism.common.util.StackUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.ItemHandlerHelper;

import static mekanism.common.util.FluidContainerUtils.extractFluid;
import static mekanism.common.util.FluidContainerUtils.insertFluid;

public class FluidContainerUtilsReflection {
    public static FluidStack handleContainerItemFill(TileEntity tileEntity, NonNullList<ItemStack> inventory, FluidStack stack, int inSlot, int outSlot) {
        if (stack != null) {
            ItemStack inputCopy = StackUtils.size(((ItemStack)inventory.get(inSlot)).copy(), 1);
            if(inputCopy.getItem() instanceof ItemEnderStorage && !inventory.get(outSlot).isEmpty()) {
                return stack;
            }

            IFluidHandlerItem handler = FluidUtil.getFluidHandler(inputCopy);
            int drained = 0;
            if (handler != null) {
                drained = insertFluid((FluidStack)stack, (IFluidHandler)handler);
                inputCopy = handler.getContainer();
            }

            if (!((ItemStack)inventory.get(outSlot)).isEmpty() && (!ItemHandlerHelper.canItemStacksStack((ItemStack)inventory.get(outSlot), inputCopy) || ((ItemStack)inventory.get(outSlot)).getCount() == ((ItemStack)inventory.get(outSlot)).getMaxStackSize())) {
                return stack;
            }

            stack.amount -= drained;
            if (((ItemStack)inventory.get(outSlot)).isEmpty()) {
                inventory.set(outSlot, inputCopy);
            } else if (ItemHandlerHelper.canItemStacksStack((ItemStack)inventory.get(outSlot), inputCopy)) {
                ((ItemStack)inventory.get(outSlot)).grow(1);
            }

            ((ItemStack)inventory.get(inSlot)).shrink(1);
            tileEntity.markDirty();
        }

        return stack;
    }


    public static FluidStack handleContainerItemEmpty(TileEntity tileEntity, NonNullList<ItemStack> inventory, FluidStack stored, int needed, int inSlot, int outSlot, final FluidContainerUtils.FluidChecker checker) {
        final Fluid storedFinal = stored != null ? stored.getFluid() : null;
        ItemStack input = StackUtils.size(((ItemStack)inventory.get(inSlot)).copy(), 1);
        IFluidHandlerItem handler = FluidUtil.getFluidHandler(input);
        if (handler == null) {
            return stored;
        } else {
            if(input.getItem() instanceof ItemEnderStorage && !inventory.get(outSlot).isEmpty()) {
                return stored;
            }
            FluidStack ret = extractFluid(needed, handler, new FluidContainerUtils.FluidChecker() {
                public boolean isValid(Fluid f) {
                    return (checker == null || checker.isValid(f)) && (storedFinal == null || storedFinal == f);
                }
            });
            ItemStack inputCopy = handler.getContainer();
            if (FluidUtil.getFluidContained(inputCopy) != null || inputCopy.isEmpty() || ((ItemStack)inventory.get(outSlot)).isEmpty() || ItemHandlerHelper.canItemStacksStack((ItemStack)inventory.get(outSlot), inputCopy) && ((ItemStack)inventory.get(outSlot)).getCount() != ((ItemStack)inventory.get(outSlot)).getMaxStackSize()) {
                if (ret != null) {
                    if (stored == null) {
                        stored = ret;
                    } else {
                        stored.amount += ret.amount;
                    }

                    needed -= ret.amount;
                    tileEntity.markDirty();
                }

                if (FluidUtil.getFluidContained(inputCopy) != null && needed != 0) {
                    inventory.set(inSlot, inputCopy);
                } else {
                    if (!inputCopy.isEmpty()) {
                        if (((ItemStack)inventory.get(outSlot)).isEmpty()) {
                            inventory.set(outSlot, inputCopy);
                        } else if (ItemHandlerHelper.canItemStacksStack((ItemStack)inventory.get(outSlot), inputCopy)) {
                            ((ItemStack)inventory.get(outSlot)).grow(1);
                        }
                    }

                    ((ItemStack)inventory.get(inSlot)).shrink(1);
                    tileEntity.markDirty();
                }

                return stored;
            } else {
                return stored;
            }
        }
    }
}
