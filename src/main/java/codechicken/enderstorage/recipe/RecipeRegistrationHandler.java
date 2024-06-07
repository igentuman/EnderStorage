package codechicken.enderstorage.recipe;

import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import static codechicken.enderstorage.EnderStorage.MOD_ID;


@Mod.EventBusSubscriber(modid = MOD_ID)
public class RecipeRegistrationHandler {
    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        event.getRegistry().register(new RecipeWithNBT().setRegistryName(new ResourceLocation(MOD_ID, "recipe_out_with_modified_nbt")));
    }
}
