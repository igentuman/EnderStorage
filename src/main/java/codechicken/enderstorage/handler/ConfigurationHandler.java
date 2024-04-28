package codechicken.enderstorage.handler;

import codechicken.enderstorage.manager.EnderStorageManager;
import codechicken.enderstorage.util.LogHelper;
import codechicken.lib.config.ConfigFile;
import codechicken.lib.config.ConfigTag;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;
import org.apache.logging.log4j.Level;

import java.io.File;

/**
 * TODO 1.13, move to new config system, Plugins also get ref to the config, so we can't do it now without binary change.
 * Created by covers1624 on 4/11/2016.
 */
public class ConfigurationHandler {

    private static boolean initialized;

    public static ConfigFile config;

    public static boolean anarchyMode;
    public static boolean disableCreatorVisuals;
    public static boolean botaniaManaSupport;
    public static boolean mekanismGasSupport;
    public static boolean enderTankItemFluidHandler;
    public static boolean perDimensionStorage;
    public static boolean useVanillaEnderChestSounds;
    public static int tankSize;
    public static int tankOutputRate;
    public static int manaOutputRate;
    public static ItemStack personalItem;
    public static ItemStack enableAutoCollectItem;

    public static void init(File file) {
        if (!initialized) {
            config = new ConfigFile(file).setComment("EnderStorage Configuration File\n" + "Deleting any element will restore it to it's default value");
            initialized = true;
        }
    }

    public static void loadConfig() {
        config.removeTag("clientUpdateCheck");
        config.removeTag("disableVanilla");
        config.removeTag("disableVanillaRecipe");
        anarchyMode = config.getTag("anarchyMode").setComment("Causes chests to lose personal settings and drop the diamond on break").getBooleanValue(false);

        // Config tag for personalItem
        ConfigTag personalItem_tag = config.getTag("personalItem").setComment("The name of the item used to set the chest to personal. Diamond by default. Format <modid>:<registeredItemName>|<meta>, Meta can be replaced with \"WILD\"");
        //region personalItemParsing
        String personalItem_name = personalItem_tag.getValue("minecraft:diamond|0");
        Item personalItem_item;
        int personalItem_meta;
        try {
            int pipeIndex = personalItem_name.lastIndexOf("|");
            personalItem_item = Item.REGISTRY.getObject(new ResourceLocation(personalItem_name.substring(0, pipeIndex)));
            if (personalItem_item == null) {
                throw new Exception("Item does not exist!");
            }
            String metaString = personalItem_name.substring(pipeIndex + 1);
            if (metaString.equalsIgnoreCase("WILD")) {
                personalItem_meta = OreDictionary.WILDCARD_VALUE;
            } else {
                personalItem_meta = Integer.parseInt(metaString);
            }
        } catch (Exception e) {
            personalItem_tag.setValue("minecraft:diamond|0");
            LogHelper.log(Level.ERROR, e, "Unable to parse Personal item config entry, Resetting to default.");
            personalItem_item = Items.DIAMOND;
            personalItem_meta = 0;
        }
        personalItem = new ItemStack(personalItem_item, 1, personalItem_meta);

        // Config tag for enableAutoCollectItem
        ConfigTag enableAutoCollectItem_tag = config.getTag("enableAutoCollectItem").setComment("The name of the item used to enable auto collect on the pouch. Nether Star by default. Format <modid>:<registeredItemName>|<meta>, Meta can be replaced with \"WILD\"");
        //region enableAutoCollectItemParsing
        String enableAutoCollectItem_name = enableAutoCollectItem_tag.getValue("minecraft:nether_star|0");
        Item enableAutoCollectItem_item;
        int enableAutoCollectItem_meta;
        try {
            int pipeIndex = enableAutoCollectItem_name.lastIndexOf("|");
            enableAutoCollectItem_item = Item.REGISTRY.getObject(new ResourceLocation(enableAutoCollectItem_name.substring(0, pipeIndex)));
            if (enableAutoCollectItem_item == null) {
                throw new Exception("Item does not exist!");
            }
            String metaString = enableAutoCollectItem_name.substring(pipeIndex + 1);
            if (metaString.equalsIgnoreCase("WILD")) {
                enableAutoCollectItem_meta = OreDictionary.WILDCARD_VALUE;
            } else {
                enableAutoCollectItem_meta = Integer.parseInt(metaString);
            }
        } catch (Exception e) {
            enableAutoCollectItem_tag.setValue("minecraft:nether_star|0");
            LogHelper.log(Level.ERROR, e, "Unable to parse auto collect enable item config entry, Resetting to default.");
            enableAutoCollectItem_item = Items.NETHER_STAR;
            enableAutoCollectItem_meta = 0;
        }
        enableAutoCollectItem = new ItemStack(enableAutoCollectItem_item, 1, enableAutoCollectItem_meta);


        //endregion
        tankSize = config.getTag("tankSize").setComment("Ender Tank size in mb.").getIntValue(50000);
        tankOutputRate = config.getTag("tankOutputRate").setComment("Ender Tank pressure mode output rate mb/t.").getIntValue(500);
        mekanismGasSupport = config.getTag("mekanismGasSupport").setComment("Adds Ender Tank support for mekanism gasses.").getBooleanValue(true);
        botaniaManaSupport = config.getTag("botaniaManaSupport").setComment("Adds Ender Tank support mana from Botania.").getBooleanValue(true);
        manaOutputRate = config.getTag("manaOutputRate").setComment("Ender Tank pressure mode output rate mb/t for Botania mana.").getIntValue(100);
        enderTankItemFluidHandler = config.getTag("enderTankItemFluidHandler").setComment("If Ender Tank can be used in inventory slots as fluid capability provider.").getBooleanValue(true);

        perDimensionStorage = config.getTag("perDimensionStorage").setComment("Makes storage connection with the same color only possible inside a dimension. No cross dimension storage.").getBooleanValue(false);
        disableCreatorVisuals = config.getTag("disableCreatorVisuals").setComment("Disables the tank on top of the creators heads.").getBooleanValue(false);
        useVanillaEnderChestSounds = config.getTag("useVanillaEnderChestSounds").setComment("Enable this to make EnderStorage use vanilla's EnderChest sounds instead of the standard chest.").getBooleanValue(false);
        EnderStorageManager.loadConfig(config);
    }

}
