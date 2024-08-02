package codechicken.enderstorage.tile;

import codechicken.enderstorage.EnderStorage;
import codechicken.enderstorage.api.Frequency;
import codechicken.enderstorage.fluid.FluidMana;
import codechicken.enderstorage.init.ModFluids;
import codechicken.enderstorage.manager.EnderStorageManager;
import codechicken.enderstorage.network.EnderStorageSPH;
import codechicken.enderstorage.network.TankSynchroniser;
import codechicken.enderstorage.storage.EnderGasStorage;
import codechicken.enderstorage.storage.EnderLiquidStorage;
import codechicken.lib.data.MCDataInput;
import codechicken.lib.data.MCDataOutput;
import codechicken.lib.fluid.FluidUtils;
import codechicken.lib.math.MathHelper;
import codechicken.lib.packet.PacketCustom;
import codechicken.lib.raytracer.IndexedCuboid6;
import codechicken.lib.vec.*;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IGasHandler;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentBase;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.FluidTankProperties;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fml.common.Optional;
import vazkii.botania.api.mana.IManaReceiver;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static codechicken.enderstorage.handler.ConfigurationHandler.*;
import static codechicken.lib.vec.Vector3.center;

@Optional.InterfaceList({
        @Optional.Interface(iface = "mekanism.api.gas.IGasHandler", modid = "mekanism"),
        @Optional.Interface(iface = "vazkii.botania.api.mana.IManaReceiver", modid = "botania"),

})
public class TileEnderTank extends TileFrequencyOwner implements IGasHandler, IManaReceiver {

    @Override
    @Optional.Method(modid = "mekanism")
    public int receiveGas(EnumFacing enumFacing, GasStack gasStack, boolean b) {
        if(liquid_state.s_liquid.amount > 0) {
            return 0;
        }
        return getGasStorage().receiveGas(enumFacing, gasStack, b);
    }

    @Override
    @Optional.Method(modid = "mekanism")
    public GasStack drawGas(EnumFacing enumFacing, int i, boolean b) {
        return getGasStorage().drawGas(enumFacing, i, b);
    }

    @Override
    @Optional.Method(modid = "mekanism")
    public boolean canReceiveGas(EnumFacing enumFacing, Gas gas) {
        return true;
    }

    @Override
    @Optional.Method(modid = "mekanism")
    public boolean canDrawGas(EnumFacing enumFacing, Gas gas) {
        return true;
    }

    @Override
    @Optional.Method(modid = "botania")
    public boolean isFull() {
        if(!botaniaManaSupport) return true;
        //full if contains gas
        if(EnderStorage.hooks.MekanismLoaded) {
            if(getGasStorage().getGasAmount() > 0) {
                return true;
            }
        }
        if(getStorage().getFluid() != null && getStorage().getFluid().getFluid() instanceof FluidMana) {
            return getStorage().getFluid().amount>=tankSize;
        }

        return getStorage().getFluid().amount != 0;
    }

    @Override
    @Optional.Method(modid = "botania")
    public void recieveMana(int i) {
        if(!botaniaManaSupport) return;
        FluidStack mana = new FluidStack(ModFluids.fluidMana, i*5);
       int r = fluidCap.fill(mana, false);
       if(r > 0) {
           mana.amount = r;
           fluidCap.fill(mana, true);
       }
    }

    @Override
    @Optional.Method(modid = "botania")
    public boolean canRecieveManaFromBursts() {
        return botaniaManaSupport;
    }

    @Override
    @Optional.Method(modid = "botania")
    public int getCurrentMana() {
        if(getStorage().getFluid() != null && getStorage().getFluid().getFluid() instanceof FluidMana) {
            return getStorage().getFluid().amount/5;
        }
        return 0;
    }

    public class EnderTankState extends TankSynchroniser.TankState {

        @Override
        public void sendSyncPacket() {
            PacketCustom packet = new PacketCustom(EnderStorageSPH.channel, 5);
            packet.writePos(getPos());
            packet.writeFluidStack(s_liquid);
            if(EnderStorage.hooks.MekanismLoaded) {
                packet.writeInt(getGasStorage(false).getGasId());
                packet.writeInt(getGasStorage(false).getGasAmount());
            }
            packet.sendToChunk(world, pos.getX() >> 4, pos.getZ() >> 4);
        }

        @Override
        public void onLiquidChanged() {
            world.checkLight(pos);
        }
    }

    public class PressureState {

        public boolean invert_redstone;
        public boolean a_pressure;
        public boolean b_pressure;

        public double a_rotate;
        public double b_rotate;

        public void update(boolean client) {
            if (client) {
                b_rotate = a_rotate;
                a_rotate = MathHelper.approachExp(a_rotate, approachRotate(), 0.5, 20);
            } else {
                b_pressure = a_pressure;
                a_pressure = world.isBlockPowered(getPos()) != invert_redstone;
                if (a_pressure != b_pressure) {
                    sendSyncPacket();
                    return;
                }
                if(world.getWorldTime() % 20 == 0) {
                    sendSyncPacket();
                }
            }
        }

        public double approachRotate() {
            return a_pressure ? -90 : 90;
        }

        private void sendSyncPacket() {
            PacketCustom packet = new PacketCustom(EnderStorageSPH.channel, 6);
            packet.writePos(getPos());
            packet.writeBoolean(a_pressure);
            packet.sendToChunk(world, pos.getX() >> 4, pos.getZ() >> 4);
        }

        public void invert() {
            invert_redstone = !invert_redstone;
            world.getChunkFromChunkCoords(pos.getX(), pos.getZ()).markDirty();
        }
    }

    public class TankFluidCap implements IFluidHandler {

        @Override
        public IFluidTankProperties[] getTankProperties() {
            if (world.isRemote) {
                return new IFluidTankProperties[] { new FluidTankProperties(liquid_state.s_liquid, EnderLiquidStorage.CAPACITY) };
            }
            return getStorage().getTankProperties();
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            if(liquid_state.s_gas_id != 0) {
                return 0;
            }
            return getStorage().fill(resource, doFill);
        }

        @Nullable
        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            if(botaniaManaSupport && resource.getFluid() instanceof FluidMana) {
                return null;
            }
            return getStorage().drain(resource, doDrain);
        }

        @Nullable
        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            if(botaniaManaSupport && getStorage().getFluid().getFluid() instanceof FluidMana) {
                return null;
            }
            return getStorage().drain(maxDrain, doDrain);
        }
    }

    private static Cuboid6[] selectionBoxes = new Cuboid6[4];
    public static Transformation[] buttonT = new Transformation[3];

    static {
        for (int i = 0; i < 3; i++) {
            buttonT[i] = new Scale(0.6).with(new Translation(0.35 + (2 - i) * 0.15, 0.91, 0.5));
            selectionBoxes[i] = selection_button.copy().apply(buttonT[i]);
        }
        selectionBoxes[3] = new Cuboid6(0.358, 0.268, 0.05, 0.662, 0.565, 0.15);
    }

    public int rotation;
    public EnderTankState liquid_state = new EnderTankState();
    public PressureState pressure_state = new PressureState();
    public TankFluidCap fluidCap = new TankFluidCap();


    private boolean described;

    @Override
    public void update() {
        super.update();

        pressure_state.update(world.isRemote);
        if (pressure_state.a_pressure) {
            ejectLiquid();
            if(EnderStorage.hooks.MekanismLoaded) {
                ejectGas();
            }
            if(EnderStorage.hooks.BotaniaLoaded) {
                ejectMana();
            }
        }
        liquid_state.setFrequency(frequency);
        liquid_state.update(world.isRemote, world.getWorldTime() % 20 == 0);
    }

    @Optional.Method(modid = "mekanism")
    private void ejectGas() {
        if(!mekanismGasSupport) return;
        for (EnumFacing side : EnumFacing.values()) {
            TileEntity te = world.getTileEntity(getPos().offset(side));
            if(te == null) continue;
            if(te instanceof TileEnderTank) {
                TileEnderTank sideTe = (TileEnderTank) te;
                if(sideTe.getStorage().freq.equals(frequency)) {
                    continue;
                }
            }
            if(!te.hasCapability(Capabilities.GAS_HANDLER_CAPABILITY, side.getOpposite())) continue;

            IGasHandler c = te.getCapability(Capabilities.GAS_HANDLER_CAPABILITY, side.getOpposite());
            GasStack gas = getGasStorage().drawGas(side, tankOutputRate, false);
            if (gas == null) {
                continue;
            }
            int qty = c.receiveGas(side.getOpposite(), gas, true);
            if (qty > 0) {
                getGasStorage().drawGas(side, qty, true);
            }
        }
    }

    @Optional.Method(modid = "botania")
    public void ejectMana() {
        if(!EnderStorage.hooks.BotaniaLoaded || !botaniaManaSupport) return;
        for (EnumFacing side : EnumFacing.values()) {
            TileEntity te = world.getTileEntity(getPos().offset(side));
            if(te == null) continue;
            if(te instanceof TileEnderTank) {
                TileEnderTank sideTe = (TileEnderTank) te;
                if(sideTe.getStorage().freq.equals(frequency)) {
                    continue;
                }
            }
            if(te instanceof IManaReceiver) {
                IManaReceiver manaTe = (IManaReceiver) te;
               if(manaTe.isFull()) return;
               int toSend = Math.min(tankOutputRate/5, getStorage().getFluid().amount);
               //as we don't know how much mana can be received we have to do it step by step
                int sent = 0;

                for(int i=0; i<toSend; i++) {
                    manaTe.recieveMana(1);
                    if(manaTe.isFull()) {
                        break;
                    }
                    sent++;
                }
                if(sent>0) {
                    getStorage().drain(sent*5, true);
                }
            }
        }
    }

    private void ejectLiquid() {
        if(getStorage().getFluid().getFluid() instanceof FluidMana) return;
        for (EnumFacing side : EnumFacing.values()) {
            TileEntity te = world.getTileEntity(getPos().offset(side));
            if(te == null) continue;
            if(te instanceof TileEnderTank) {
                TileEnderTank sideTe = (TileEnderTank) te;
                if(sideTe.getStorage().freq.equals(frequency)) {
                    continue;
                }
            }
            IFluidHandler c = FluidUtils.getFluidHandlerOrEmpty(world, getPos().offset(side), side.getOpposite());
            FluidStack liquid = getStorage().drain(tankOutputRate, false);
            if (liquid == null) {
                continue;
            }
            int qty = c.fill(liquid, true);
            if (qty > 0) {
                getStorage().drain(qty, true);
            }
        }
    }

    @Override
    public void setFreq(Frequency frequency) {
        super.setFreq(frequency);
        if (!world.isRemote) {
            liquid_state.setFrequency(frequency);
        }
    }

    @Override
    public EnderLiquidStorage getStorage() {
        return (EnderLiquidStorage) EnderStorageManager.instance(world.isRemote).getStorage(frequency, "liquid");
    }

    @Optional.Method(modid = "mekanism")
    public EnderGasStorage getGasStorage() {
        return (EnderGasStorage) EnderStorageManager.instance(world.isRemote).getStorage(frequency, "gas");
    }

    @Override
    public void onPlaced(EntityLivingBase entity) {
        rotation = (int) Math.floor(entity.rotationYaw * 4 / 360 + 2.5D) & 3;
        pressure_state.b_rotate = pressure_state.a_rotate = pressure_state.approachRotate();
        if(world.isRemote) {
            return;
        }
        setFreq(frequency.copy().setDimId(String.valueOf(entity.dimension)));
        liquid_state.setFrequency(frequency);
        liquid_state.forceUpdate();
        markDirty();
        if(entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            FluidUtil.interactWithFluidHandler(player, player.swingingHand, getStorage());
        }
        getStorage().manager.requestSave(getStorage());
        sendUpdatePacket();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setByte("rot", (byte) rotation);
        tag.setBoolean("ir", pressure_state.invert_redstone);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        liquid_state.setFrequency(frequency);
        rotation = tag.getByte("rot") & 3;
        pressure_state.invert_redstone = tag.getBoolean("ir");
    }

    @Override
    public void writeToPacket(MCDataOutput packet) {
        super.writeToPacket(packet);
        packet.writeByte(rotation);
        packet.writeFluidStack(liquid_state.s_liquid);
        if(EnderStorage.hooks.MekanismLoaded) {
            packet.writeInt(getGasStorage().getGasId());
            packet.writeInt(getGasStorage().getGasAmount());
        }
        packet.writeBoolean(pressure_state.a_pressure);
    }

    @Override
    public void readFromPacket(MCDataInput packet) {
        super.readFromPacket(packet);
        liquid_state.setFrequency(frequency);
        rotation = packet.readUByte() & 3;
        liquid_state.s_liquid = packet.readFluidStack();
        if(EnderStorage.hooks.MekanismLoaded) {
            getGasStorage().setGasId(packet.readInt());
            getGasStorage().setGasAmount(packet.readInt());
        }
        pressure_state.a_pressure = packet.readBoolean();
        if (!described) {
            liquid_state.c_liquid = liquid_state.s_liquid;
            pressure_state.b_rotate = pressure_state.a_rotate = pressure_state.approachRotate();
        }
        described = true;
        liquid_state.update(true, true);
    }

    @Override
    public boolean activate(EntityPlayer player, int subHit, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (subHit == 4) {
            pressure_state.invert();
            String subtype = "default";
            if (pressure_state.invert_redstone) {
                subtype = "push";
            }
            player.sendMessage(new TextComponentTranslation("enderstorage.tile.mode." + subtype));
            return true;
        }
        if(liquid_state.s_gas_id == 0 && !(getStorage().getFluid().getFluid() instanceof FluidMana)) {
            return FluidUtil.interactWithFluidHandler(player, hand, getStorage());
        }
        return false;
    }

    @Override
    public List<IndexedCuboid6> getIndexedCuboids() {
        ArrayList<IndexedCuboid6> cuboids = new ArrayList<>();

        cuboids.add(new IndexedCuboid6(0, new Cuboid6(0.15, 0, 0.15, 0.85, 0.916, 0.85)));

        for (int i = 0; i < 4; i++) {
            cuboids.add(new IndexedCuboid6(i + 1, selectionBoxes[i].copy().apply(Rotation.quarterRotations[rotation ^ 2].at(center))));
        }
        return cuboids;
    }

    @Override
    public int getLightValue() {
        if (liquid_state.s_liquid.amount > 0) {
            return FluidUtils.getLuminosity(liquid_state.c_liquid, liquid_state.s_liquid.amount / 16D);
        }

        return 0;
    }

    @Override
    public boolean redstoneInteraction() {
        return true;
    }

    public void sync(PacketCustom packet) {
        if (packet.getType() == 5) {
            liquid_state.sync(packet.readFluidStack(), packet.readInt(), packet.readInt());
        } else if (packet.getType() == 6) {
            pressure_state.a_pressure = packet.readBoolean();
        }
    }

    @Override
    public boolean rotate() {
        if (!world.isRemote) {
            rotation = (rotation + 1) % 4;
            PacketCustom.sendToChunk(getUpdatePacket(), world, pos.getX() >> 4, pos.getZ() >> 4);
        }

        return true;
    }

    @Override
    public int comparatorInput() {
        IFluidTankProperties tank = getStorage().getTankProperties()[0];
        FluidStack fluid = tank.getContents();
        if (fluid == null) {
            fluid = FluidUtils.emptyFluid();
        }
        int signal = fluid.amount * 14 / tank.getCapacity() + (fluid.amount > 0 ? 1 : 0);
        if(signal > 0) {
            return signal;
        }
        if(EnderStorage.hooks.MekanismLoaded) {
            signal = getGasStorage().getGasAmount()/EnderGasStorage.CAPACITY+ + (getGasStorage().getGasAmount() > 0 ? 1 : 0);
        }
        return signal;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
        if(EnderStorage.hooks.MekanismLoaded) {
            if (capability == Capabilities.GAS_HANDLER_CAPABILITY) return true;
        }
        return capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || super.hasCapability(capability, facing);
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
        if(EnderStorage.hooks.MekanismLoaded) {
            if (capability == Capabilities.GAS_HANDLER_CAPABILITY) {
                return Capabilities.GAS_HANDLER_CAPABILITY.cast(this);
            }
        }
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(fluidCap);
        }
        return super.getCapability(capability, facing);
    }
}
