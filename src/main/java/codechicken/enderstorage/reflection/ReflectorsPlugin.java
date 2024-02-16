package codechicken.enderstorage.reflection;

import codechicken.enderstorage.EnderStorage;
import com.github.mjaroslav.reflectors.v4.Reflectors;
import mekanism.common.util.FluidContainerUtils;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import static codechicken.enderstorage.handler.ConfigurationHandler.enderTankItemFluidHandler;

@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.Name("ReflectorsPlugin")
public class ReflectorsPlugin extends Reflectors.FMLLoadingPluginAdapter
        implements IFMLLoadingPlugin, IClassTransformer {
    public ReflectorsPlugin() {
        Reflectors.enabledLogs = true;
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[]{getClass().getName()};
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
            if (transformedName.equals("mekanism.common.util.FluidContainerUtils")) {
                return Reflectors.reflectClass(basicClass, transformedName, FluidContainerUtilsReflection.class.getName());
            }
        return basicClass;
    }
}