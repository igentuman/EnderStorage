package codechicken.enderstorage.item;

import codechicken.enderstorage.api.Frequency;
import codechicken.enderstorage.block.BlockEnderStorage;
import codechicken.enderstorage.manager.EnderStorageManager;
import codechicken.enderstorage.network.TankSynchroniser;
import codechicken.enderstorage.storage.EnderLiquidStorage;
import codechicken.enderstorage.tile.TileFrequencyOwner;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

import static codechicken.enderstorage.handler.ConfigurationHandler.enderTankItemFluidHandler;

public class ItemEnderStorage extends ItemBlock {

    public ItemEnderStorage(Block block) {
        super(block);
        setHasSubtypes(true);
    }

    @Override
    public int getMetadata(int stackMeta) {
        return stackMeta;
    }

    public Frequency getFreq(ItemStack stack) {
        return Frequency.readFromStack(stack);
    }

    @Override
    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, IBlockState newState) {
        if (super.placeBlockAt(stack, player, world, pos, side, hitX, hitY, hitZ, newState)) {
            TileFrequencyOwner tile = (TileFrequencyOwner) world.getTileEntity(pos);
            tile.setFreq(getFreq(stack));
            return true;
        }
        return false;
    }

    @Nonnull
    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return "tile." + BlockEnderStorage.Type.byMetadata(stack.getItemDamage()).getName();
    }

    @Override
    public void addInformation(@Nonnull ItemStack stack, @Nullable World worldIn, @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flagIn) {
        Frequency frequency = Frequency.readFromStack(stack);
        if (frequency.owner != null) {
            tooltip.add(frequency.owner);
        }
        tooltip.add(frequency.getTooltip());
        if (getMetadata(stack) == 1) {
            EnderLiquidStorage storage = getLiquidStorage(stack, false);
            FluidStack fluid = TankSynchroniser.getClientLiquid(frequency);
            if(fluid.amount > 0) {
                tooltip.add(fluid.getLocalizedName() + ": " + fluid.amount + "mB");
            }
        }
    }

    private EnderLiquidStorage getLiquidStorage(ItemStack stack, boolean server) {
        return (EnderLiquidStorage) EnderStorageManager.instance(!server).getStorage(stack, "liquid");
    }

    @Override
    public ICapabilityProvider initCapabilities(@Nonnull final ItemStack stack, NBTTagCompound nbt) {
        if (getMetadata(stack) == 1 && enderTankItemFluidHandler && stack.getCount() == 1) {
            return new ICapabilityProvider() {
                @Override
                public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
                    return capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY
                            || capability == CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY;
                }

                @Override
                public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
                    if(capability == CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY) {
                        return CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY.cast(getLiquidStorage(stack, true));
                    }
                    return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY ? getLiquidStorage(stack, true) : null);
                }
            };
        }
        return null;
    }
}
