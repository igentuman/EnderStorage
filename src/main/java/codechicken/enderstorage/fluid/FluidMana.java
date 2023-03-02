package codechicken.enderstorage.fluid;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;

public class FluidMana extends Fluid {
    public FluidMana() {
        super("botania_mana", new ResourceLocation("botania", "blocks/mana_water"), new ResourceLocation("botania", "blocks/mana_water"), 0x0036E8FF);
    }
}
