package codechicken.enderstorage.item;

import codechicken.enderstorage.api.Frequency;
import codechicken.enderstorage.client.EnderPouchBakery;
import codechicken.enderstorage.handler.ConfigurationHandler;
import codechicken.enderstorage.manager.EnderStorageManager;
import codechicken.enderstorage.storage.EnderItemStorage;
import codechicken.enderstorage.tile.TileEnderChest;
import codechicken.lib.model.bakery.IBakeryProvider;
import codechicken.lib.model.bakery.generation.IBakery;
import codechicken.lib.util.ItemNBTUtils;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

public class ItemEnderPouch extends Item implements IBakeryProvider {

    public ItemEnderPouch() {
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.TRANSPORTATION);
        setUnlocalizedName("ender_pouch");
    }

    private boolean canAutoPickup(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return (tag != null && tag.hasKey("AutoPickup")) && tag.getBoolean("AutoPickup");
    }

    private boolean isAutoPickupEnabled(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return (tag != null && tag.hasKey("Enabled")) && tag.getBoolean("Enabled");
    }

    private String isAutoPickupEnabledToString(ItemStack stack) {
        return isAutoPickupEnabled(stack) ? "Enabled" : "Disabled";
    }

    private static void flipBoolean(ItemStack stack, String key) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        assert stack.getTagCompound() != null;
        boolean currentValue = false;
        if (stack.getTagCompound().hasKey(key)) {
            currentValue = stack.getTagCompound().getBoolean(key);
        }
        stack.getTagCompound().setBoolean(key, !currentValue);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        Frequency freq = Frequency.readFromStack(stack);
        if (freq.owner != null) {
            tooltip.add(freq.owner);
        }
        tooltip.add(freq.getTooltip());
        if (canAutoPickup(stack)) {
            tooltip.add("Auto-Pickup: " + isAutoPickupEnabledToString(stack));
        }
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, EnumHand hand) {
        if (world.isRemote) {
            return EnumActionResult.PASS;
        }

        ItemStack stack = player.getHeldItem(hand);
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileEnderChest && player.isSneaking()) {
            TileEnderChest chest = (TileEnderChest) tile;
            ItemNBTUtils.validateTagExists(stack);
            Frequency frequency = chest.frequency.copy();
            if (ConfigurationHandler.anarchyMode && !(frequency.owner != null && frequency.owner.equals(player.getDisplayNameString()))) {
                frequency.setOwner(null);
            }

            frequency.writeToStack(stack);

            return EnumActionResult.SUCCESS;
        }
        return EnumActionResult.PASS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (player.isSneaking()) {
            if (canAutoPickup(stack)) {
                flipBoolean(stack, "Enabled");
                if (world.isRemote) {
                    player.sendMessage(new TextComponentTranslation("Changed Auto-Pickup: " + isAutoPickupEnabledToString(stack), isAutoPickupEnabledToString(stack)));
                }
            }
            return new ActionResult<>(EnumActionResult.PASS, stack);
        }
        if(!world.isRemote) {
            Frequency frequency = Frequency.readFromStack(stack);
            ((EnderItemStorage) EnderStorageManager.instance(world.isRemote).getStorage(frequency, "item")).openSMPGui(player, stack.getUnlocalizedName() + ".name");
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    private NonNullList<ItemStack> findPouchesInInventory(EntityPlayer player, Item item) {
        NonNullList<ItemStack> found = NonNullList.create();

        for (int i = 0; i < player.inventory.getSizeInventory(); ++i) {
            ItemStack itemInSlot = player.inventory.getStackInSlot(i);

            if (Objects.equals(itemInSlot.getItem().getClass(), item.getClass())) {
                found.add(itemInSlot);
            }
        }
        return found;
    }


    private static boolean canMerge(final ItemStack chestItem, final ItemStack groundItem) {
        if (chestItem.isEmpty() || groundItem.isEmpty()) {
            return false;
        }
        return (groundItem.getItem().equals(chestItem.getItem()) && groundItem.getItemDamage() == chestItem.getItemDamage() && chestItem.getCount() < chestItem.getMaxStackSize() && ItemStack.areItemStackTagsEqual(groundItem, chestItem));
    }


    @SubscribeEvent
    public void onEntityItemPickupEvent(EntityItemPickupEvent event) {
        if (event.getItem().isDead) {
            return;
        }

        boolean itemsAdded = false;
        ItemStack itemStackOnGround = event.getItem().getItem();
        World world = event.getEntityPlayer().getEntityWorld();

        if (itemStackOnGround.isEmpty() || itemStackOnGround.getItem() == this) {
            return;
        }

        NonNullList<ItemStack> foundPouches = this.findPouchesInInventory(event.getEntityPlayer(), this);

        // Match
        for (ItemStack stackIsPouch : foundPouches) {
            if (!isAutoPickupEnabled(stackIsPouch)) {
                continue;
            }
            if (itemsAdded) {
                break;
            }
            EnderItemStorage enderStorage = ((EnderItemStorage) EnderStorageManager.instance(world.isRemote).getStorage(Frequency.readFromStack(stackIsPouch), "item"));
            if (enderStorage == null) {
                break;
            }

            for (int i = 0; i < enderStorage.getSizeInventory(); i++) {
                if (itemsAdded) {
                    break;
                }
                ItemStack itemStackInSlot = enderStorage.getStackInSlot(i);
                if (canMerge(itemStackInSlot, itemStackOnGround)) {
                    int amountAvailable = itemStackInSlot.getMaxStackSize() - itemStackInSlot.getCount();
                    if (itemStackOnGround.getCount() >= amountAvailable) {
                        itemStackOnGround.setCount(itemStackOnGround.getCount() - amountAvailable);
                        itemStackInSlot.setCount(itemStackOnGround.getMaxStackSize());

                    } else {
                        itemStackInSlot.setCount(itemStackInSlot.getCount() + itemStackOnGround.getCount());
                        itemStackOnGround.setCount(0);
                    }

                    if (itemStackOnGround.getCount() == 0) {
                        itemsAdded = true;
                    }
                }
            }
            /*
            if (!itemsAdded && !itemStackOnGround.isEmpty()) {
                LogHelper.log(Level.INFO, "No available slot to merge " + itemStackOnGround.getItem().getTranslationKey());
            }
            */
        }

        // Is Air
        for (ItemStack stackIsPouch : foundPouches) {
            if (!isAutoPickupEnabled(stackIsPouch)) {
                continue;
            }
            if (itemsAdded) {
                break;
            }
            EnderItemStorage enderStorage = ((EnderItemStorage) EnderStorageManager.instance(world.isRemote).getStorage(Frequency.readFromStack(stackIsPouch), "item"));
            if (enderStorage == null) {
                break;
            }

            for (int i = 0; i < enderStorage.getSizeInventory(); i++) {
                if (itemsAdded) {
                    break;
                }
                ItemStack itemStackInSlot = enderStorage.getStackInSlot(i);
                if (itemStackInSlot.isEmpty()) {

                    enderStorage.setInventorySlotContents(i, itemStackOnGround.copy());
                    itemStackInSlot.setCount(Math.max(itemStackOnGround.getCount() - enderStorage.getInventoryStackLimit(), 0));
                    itemStackOnGround.setCount(0);

                    if (itemStackOnGround.getCount() == 0) {
                        itemsAdded = true;
                    }
                }
            }
            /*
            if (!itemsAdded && !itemStackOnGround.isEmpty()) {
                LogHelper.log(Level.INFO, "No available slot to add " + itemStackOnGround.getItem().getTranslationKey());
            }
             */
        }

        if (itemStackOnGround.isEmpty()) {
            event.getItem().setDead(); // Mark the original item as picked up
        }

    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean hasEffect(ItemStack stack) {
        return this.isAutoPickupEnabled(stack);
    }

    @SideOnly (Side.CLIENT)
    @Override
    public IBakery getBakery() {
        return EnderPouchBakery.INSTANCE;
    }
}
