package codechicken.enderstorage.init;

import codechicken.enderstorage.EnderStorage;
import codechicken.enderstorage.fluid.FluidMana;
import codechicken.enderstorage.handler.ConfigurationHandler;
import net.minecraftforge.fluids.FluidRegistry;

import static codechicken.enderstorage.handler.ConfigurationHandler.botaniaManaSupport;

public class ModFluids {

    public static FluidMana fluidMana;

    public static void init() {

        if(EnderStorage.hooks.BotaniaLoaded) {
            ConfigurationHandler.loadConfig();
            if(botaniaManaSupport) {
                fluidMana = new FluidMana();
                FluidRegistry.registerFluid(fluidMana);
            }
        }
    }
}
