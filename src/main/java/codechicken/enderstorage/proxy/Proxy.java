package codechicken.enderstorage.proxy;

import codechicken.enderstorage.EnderStorage;
import codechicken.enderstorage.init.ModBlocks;
import codechicken.enderstorage.init.ModFluids;
import codechicken.enderstorage.init.ModItems;
import codechicken.enderstorage.item.ItemEnderPouch;
import codechicken.enderstorage.manager.EnderStorageManager;
import codechicken.enderstorage.network.EnderStorageSPH;
import codechicken.enderstorage.network.TankSynchroniser;
import codechicken.enderstorage.plugin.EnderGasStoragePlugin;
import codechicken.enderstorage.plugin.EnderItemStoragePlugin;
import codechicken.enderstorage.plugin.EnderLiquidStoragePlugin;
import codechicken.lib.packet.PacketCustom;
import net.minecraftforge.common.MinecraftForge;

/**
 * Created by covers1624 on 4/11/2016.
 */
public class Proxy {

    public void preInit() {
        EnderStorageManager.registerPlugin(new EnderItemStoragePlugin());
        EnderStorageManager.registerPlugin(new EnderLiquidStoragePlugin());
        if(EnderStorage.hooks.MekanismLoaded) {
            EnderStorageManager.registerPlugin(new EnderGasStoragePlugin());
        }
        ModBlocks.init();
        ModItems.init();
        ModFluids.init();
        //MinecraftForge.EVENT_BUS.register(EnderStorageRecipe.init());
        MinecraftForge.EVENT_BUS.register(new EnderStorageManager.EnderStorageSaveHandler());
        MinecraftForge.EVENT_BUS.register(new TankSynchroniser());
        MinecraftForge.EVENT_BUS.register(new ItemEnderPouch());
    }

    public void init() {
        PacketCustom.assignHandler(EnderStorageSPH.channel, new EnderStorageSPH());
    }

}
