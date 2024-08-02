package codechicken.enderstorage.tile;

import codechicken.enderstorage.fluid.FluidMana;
import codechicken.enderstorage.manager.EnderStorageManager;
import codechicken.enderstorage.misc.EnderDyeButton;
import codechicken.enderstorage.misc.EnderKnobSlot;
import codechicken.enderstorage.storage.EnderItemStorage;
import codechicken.lib.data.MCDataInput;
import codechicken.lib.data.MCDataOutput;
import codechicken.lib.math.MathHelper;
import codechicken.lib.packet.PacketCustom;
import codechicken.lib.raytracer.IndexedCuboid6;
import codechicken.lib.vec.Cuboid6;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;

import java.util.ArrayList;
import java.util.List;

import static codechicken.enderstorage.handler.ConfigurationHandler.useVanillaEnderChestSounds;
import static codechicken.enderstorage.tile.TileEnderChest.ChestMode.PUSH;
import static com.ibm.icu.impl.duration.impl.DataRecord.ENumberSystem.DEFAULT;
import static net.minecraft.init.SoundEvents.*;

public class TileEnderChest extends TileFrequencyOwner {

    public double a_lidAngle;
    public double b_lidAngle;
    public int c_numOpen;
    public int rotation;

    public static EnderDyeButton[] buttons;

    static {
        buttons = new EnderDyeButton[3];
        for (int i = 0; i < 3; i++) {
            buttons[i] = new EnderDyeButton(i);
        }
    }

    private byte mode;

    public TileEnderChest() {
    }

    @Override
    public void update() {
        super.update();

        if (!world.isRemote && (world.getTotalWorldTime() % 20 == 0 || c_numOpen != getStorage().getNumOpen())) {
            if(mode == PUSH.mode) {
                pushItems();
            }
            c_numOpen = getStorage().getNumOpen();
            world.addBlockEvent(getPos(), getBlockType(), 1, c_numOpen);
            world.notifyNeighborsOfStateChange(pos, getBlockType(), true);
        }

        b_lidAngle = a_lidAngle;
        a_lidAngle = MathHelper.approachLinear(a_lidAngle, c_numOpen > 0 ? 1 : 0, 0.1);

        if (b_lidAngle >= 0.5 && a_lidAngle < 0.5) {
            world.playSound(null, getPos(), useVanillaEnderChestSounds ? BLOCK_ENDERCHEST_CLOSE : BLOCK_CHEST_CLOSE, SoundCategory.BLOCKS, 0.5F, world.rand.nextFloat() * 0.1F + 0.9F);
        } else if (b_lidAngle == 0 && a_lidAngle > 0) {
            world.playSound(null, getPos(), useVanillaEnderChestSounds ? BLOCK_ENDERCHEST_OPEN : BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.5F, world.rand.nextFloat() * 0.1F + 0.9F);
        }
    }

    private List<EnumFacing> emptySides = new ArrayList<>();
    private void pushItems() {
        emptySides.clear();
        for(ItemStack stack: getStorage().getInventory()) {
            if(stack.isEmpty()) continue;
            for (EnumFacing side: EnumFacing.VALUES) {
                if(emptySides.contains(side)) continue;
                TileEntity te = world.getTileEntity(getPos().offset(side));
                if(te == null) {
                    emptySides.add(side);
                    continue;
                }
                IItemHandler inventory = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side.getOpposite());
                if(inventory == null) {
                    emptySides.add(side);
                    continue;
                }

                for(int i = 0; i < inventory.getSlots();i++) {
                    ItemStack left = inventory.insertItem(i, stack, true);
                    if(left.getCount() > 0) {
                        int toInsert = stack.getCount() - left.getCount();
                        stack.shrink(toInsert);
                        ItemStack insertStack = stack.copy();
                        insertStack.setCount(toInsert);
                        inventory.insertItem(i, insertStack, false);
                        getStorage().markDirty();
                    } else {
                        inventory.insertItem(i, stack.copy(), false);
                        stack.setCount(0);
                        getStorage().markDirty();
                    }
                }
            }
        }
    }

    @Override
    public boolean receiveClientEvent(int id, int type) {
        if (id == 1) {
            c_numOpen = type;
            return true;
        }
        return false;
    }

    public double getRadianLidAngle(float frame) {
        double a = MathHelper.interpolate(b_lidAngle, a_lidAngle, frame);
        a = 1.0F - a;
        a = 1.0F - a * a * a;
        return a * 3.141593 * -0.5;
    }

    @Override
    public EnderItemStorage getStorage() {
        return (EnderItemStorage) EnderStorageManager.instance(world.isRemote).getStorage(frequency, "item");
    }

    @Override
    public void writeToPacket(MCDataOutput packet) {
        super.writeToPacket(packet);
        packet.writeByte(rotation);
        packet.writeByte(mode);
    }

    @Override
    public void readFromPacket(MCDataInput packet) {
        super.readFromPacket(packet);
        rotation = packet.readUByte() & 3;
        mode = packet.readByte();
    }

    @Override
    public void onPlaced(EntityLivingBase entity) {
        rotation = (int) Math.floor(entity.rotationYaw * 4 / 360 + 2.5D) & 3;
        mode = 0;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setByte("rot", (byte) rotation);
        tag.setByte("mode", (byte) mode);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        rotation = tag.getByte("rot") & 3;
        mode = tag.getByte("mode");
    }

    @Override
    public boolean activate(EntityPlayer player, int subHit, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (subHit == 4) {
            toggleMode();
            String subtype = "default";
            if (mode == 1) {
                subtype = "push";
            }
            player.sendMessage(new TextComponentTranslation("enderstorage.tile.mode." + subtype));
            return true;
        }
        getStorage().openSMPGui(player, "tile.enderChest.name");
        return true;
    }

    private void toggleMode() {
        switch (mode) {
            case 0:
                mode = 1;
                break;
            case 1:
                mode = 0;
                break;
        }
        markDirty();
    }

    @Override
    public List<IndexedCuboid6> getIndexedCuboids() {
        List<IndexedCuboid6> cuboids = new ArrayList<>();

        cuboids.add(new IndexedCuboid6(0, new Cuboid6(1 / 16D, 0, 1 / 16D, 15 / 16D, 14 / 16D, 15 / 16D)));

        // Remove other boxes if the chest has lid open.
        if (getRadianLidAngle(0) < 0) {
            return cuboids;
        }

        // DyeButtons.
        for (int button = 0; button < 3; button++) {
            EnderDyeButton ebutton = TileEnderChest.buttons[button].copy();
            ebutton.rotate(0, 0.5625, 0.0625, 1, 0, 0, 0);
            ebutton.rotateMeta(rotation);

            cuboids.add(new IndexedCuboid6(button + 1, new Cuboid6(ebutton.getMin(), ebutton.getMax())));
        }

        //Lock Button.
        cuboids.add(new IndexedCuboid6(4, new Cuboid6(new EnderKnobSlot(rotation).getSelectionBB())));
        return cuboids;
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
        return Container.calcRedstoneFromInventory(getStorage());
    }

    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY;
    }

    @SuppressWarnings ("unchecked")
    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return (T) new InvWrapper(getStorage());
        }
        return super.getCapability(capability, facing);
    }

    public byte mode() {
        return mode;
    }

    public enum ChestMode {
        DEFAULT((byte) 0),
        PUSH((byte) 1);

        public final byte mode;
        public byte mode() {
            return mode;
        }
        ChestMode(byte mode) {
            this.mode = mode;
        }
    }
}
