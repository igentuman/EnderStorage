package codechicken.enderstorage.storage;

import codechicken.enderstorage.EnderStorage;
import codechicken.enderstorage.api.AbstractEnderStorage;
import codechicken.enderstorage.api.Frequency;
import codechicken.enderstorage.handler.ConfigurationHandler;
import codechicken.enderstorage.manager.EnderStorageManager;
import mekanism.api.gas.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.EmptyFluidHandler;
import net.minecraftforge.fml.common.Optional;

import javax.annotation.Nonnull;

import static codechicken.enderstorage.handler.ConfigurationHandler.tankSize;
import static codechicken.enderstorage.storage.EnderLiquidStorage.getFreq;

@Optional.InterfaceList({
        @Optional.Interface(iface = "mekanism.api.gas.IGasHandler", modid = "mekanism")
})
public class EnderGasStorage extends AbstractEnderStorage implements IGasHandler {

    public static final int CAPACITY = tankSize;

    private boolean canTransferGas()
    {
        return EnderStorage.hooks.MekanismLoaded && ConfigurationHandler.mekanismGasSupport;
    }

    @Override
    public void setStack(ItemStack stack) {
        this.stack = stack;
    }

    public FluidStack getFluid()
    {
        try {
            if (tank.getGas().amount == 0) {
                return null;
            }
            return new FluidStack(tank.getGas().getGas().getFluid().setGaseous(true), tank.getGas().amount);
        } catch (NullPointerException ignored) {
            return null;
        }
    }

    @Override
    public int receiveGas(EnumFacing enumFacing, GasStack gasStack, boolean b) {
        if(canReceiveGas(enumFacing, gasStack.getGas())) {
            int receive =  tank.receive(gasStack, b);
            if(receive > 0 && b) {
                setDirty();
            }
            return receive;
        }
        return 0;
    }

    @Override
    public GasStack drawGas(EnumFacing enumFacing, int amount, boolean doTransfer) {
        return  tank.draw(amount, doTransfer);
    }

    @Override
    public boolean canReceiveGas(EnumFacing enumFacing, Gas gas) {
        return canTransferGas() && tank.canReceive(gas);
    }

    @Override
    public boolean canDrawGas(EnumFacing enumFacing, Gas gas) {
        return canTransferGas() && tank.canDraw(gas);
    }

    @Nonnull
    @Override
    public GasTankInfo[] getTankInfo() {
        return IGasHandler.super.getTankInfo();
    }

    public int getGasAmount() {
        if(tank.getGas() == null) return 0;
        return tank.getGas().amount;
    }

    public int getGasId() {
        if(tank.getGas() == null) return 0;
        return tank.getGas().getGas().getID();
    }

    public void setGasId(int gasId) {
        int wasId = getGasId();
        tank.setGas(new GasStack(gasId, 1));
        if(wasId != gasId) {
            setDirty();
        }
    }

    public void setGasAmount(int amount) {
        if(tank.getGas() == null) return;
        int wasAmount = getGasAmount();
        tank.getGas().amount = amount;
        if(wasAmount != amount) {
            setDirty();
        }
    }

    private class Tank extends GasTank {
        public Tank(int capacity) {
            super(capacity);
        }
        public void fromTag(NBTTagCompound tag) {
           super.read(tag);
        }

        public NBTTagCompound toTag() {
            return super.write(new NBTTagCompound());
        }
    }

    private Tank tank;
    public ItemStack stack;

    public EnderGasStorage(EnderStorageManager manager, Frequency freq) {
        super(manager, freq);
        tank = new Tank(CAPACITY);
    }

    public EnderGasStorage(EnderStorageManager manager, ItemStack stack) {
        super(manager, getFreq(stack));
        tank = new Tank(CAPACITY);
        this.stack = stack;
    }

    @Override
    public void clearStorage() {
        tank = new Tank(CAPACITY);
        setDirty();
    }

    public void loadFromTag(NBTTagCompound tag) {
        tank.fromTag(tag.getCompoundTag("gas_tank"));
    }

    @Override
    public String type() {
        return "gas";
    }

    public NBTTagCompound saveToTag() {
        NBTTagCompound compound = new NBTTagCompound();
        compound.setTag("gas_tank", tank.toTag());

        return compound;
    }
}
