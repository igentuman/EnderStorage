package codechicken.enderstorage.recipe;

import codechicken.enderstorage.handler.ConfigurationHandler;
import codechicken.enderstorage.init.ModItems;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.registries.IForgeRegistryEntry;

public class RecipeWithNBT extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {
    @Override
    public boolean matches(InventoryCrafting inv, World worldIn) {
        boolean hasPouch = false;
        boolean hasNetherStar = false;

        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty()) {
                if (stack.getItem().equals(ModItems.enderPouch)) {
                    hasPouch = true;
                } else if (stack.getItem().equals(ConfigurationHandler.enableAutoCollectItem.getItem())) {
                    hasNetherStar = true;
                } else {
                    return false;
                }
            }
        }

        return hasPouch && hasNetherStar;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv) {
        ItemStack output = ItemStack.EMPTY;

        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem().getClass().equals(ModItems.enderPouch.getClass())) {
                output = stack.copy(); // Copy the original stack

                NBTTagCompound tag = new NBTTagCompound();

                if (output.getTagCompound() != null) {
                    tag = output.getTagCompound().copy();
                }

                tag.setByte("AutoPickup", (byte) 1);

                output.setTagCompound(tag);
                break;
            }
        }

        return output;
    }

    @Override
    public boolean canFit(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return ModItems.enderPouch.getDefaultInstance();
    }
}

