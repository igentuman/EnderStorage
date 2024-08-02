package codechicken.enderstorage.network;

import codechicken.enderstorage.EnderStorage;
import codechicken.enderstorage.api.AbstractEnderStorage;
import codechicken.enderstorage.api.Frequency;
import codechicken.enderstorage.manager.EnderStorageManager;
import codechicken.enderstorage.storage.EnderGasStorage;
import codechicken.enderstorage.storage.EnderLiquidStorage;
import codechicken.lib.fluid.FluidUtils;
import codechicken.lib.math.MathHelper;
import codechicken.lib.packet.PacketCustom;
import codechicken.lib.util.ClientUtils;
import codechicken.lib.util.ServerUtils;
import com.google.common.collect.Sets;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class TankSynchroniser {

    public static abstract class TankState {

        public Frequency frequency;
        public FluidStack c_liquid = FluidUtils.emptyFluid();
        public FluidStack s_liquid = FluidUtils.emptyFluid();
        public FluidStack f_liquid = FluidUtils.emptyFluid();
        public int s_manaAmount = 0;
        public int c_manaAmount = 0;
        public int c_gas_id = 0;
        public int c_gas_amount = 0;
        public int s_gas_id = 0;
        public int s_gas_amount = 0;
        public int forceUpdateCounter = 0;
        public void setFrequency(Frequency frequency) {
            this.frequency = frequency;
        }

        public void forceUpdate()
        {
            forceUpdateCounter++;
            if(forceUpdateCounter>20) {
                forceUpdateCounter = 0;
                sendSyncPacket();
            }

        }

        public void update(boolean client) {
            FluidStack b_liquid;
            FluidStack a_liquid;
            if (client) {
                b_liquid = c_liquid.copy();
                c_gas_amount = s_gas_amount;
                c_gas_id = s_gas_id;
                c_manaAmount = s_manaAmount;
                if (s_liquid.isFluidEqual(c_liquid)) {
                    c_liquid.amount = MathHelper.approachExpI(c_liquid.amount, s_liquid.amount, 0.1);
                } else if (c_liquid.amount > 100) {
                    c_liquid.amount = MathHelper.retreatExpI(c_liquid.amount, 0, f_liquid.amount, 0.1, 1000);
                } else {
                    c_liquid = FluidUtils.copy(s_liquid, 0);
                }

                a_liquid = c_liquid;
            } else {
                if(EnderStorage.hooks.MekanismLoaded) {
                    s_gas_amount = getGasStorage(false).getGasAmount();
                    s_gas_id = getGasStorage(false).getGasId();
                    if(s_gas_amount == 0) {
                        s_gas_id = 0;
                    }
                    if(c_gas_id != s_gas_id) {
                        sendSyncPacket();
                        c_gas_id = s_gas_id;
                        c_gas_amount = s_gas_amount;
                    } else {
                        if(Math.abs(c_gas_amount - s_gas_amount) > 100 || (s_gas_amount == 0 && c_gas_amount > 0)) {
                            sendSyncPacket();
                            c_gas_id = s_gas_id;
                            c_gas_amount = s_gas_amount;
                        }
                    }
                    forceUpdate();
                }

                s_liquid = getStorage(false).getFluid();
                b_liquid = s_liquid.copy();
                if (!s_liquid.isFluidEqual(c_liquid)) {
                    sendSyncPacket();
                    c_liquid = s_liquid;
                } else if (Math.abs(c_liquid.amount - s_liquid.amount) > 100 || (s_liquid.amount == 0 && c_liquid.amount > 0)) {// Diff grater than 250 Or server no longer has liquid and client does.
                    sendSyncPacket();
                    c_liquid = s_liquid;
                }

                a_liquid = s_liquid;
            }
            if ((b_liquid.amount == 0) != (a_liquid.amount == 0) || !b_liquid.isFluidEqual(a_liquid)) {
                onLiquidChanged();
            }
        }

        public void onLiquidChanged() {
        }

        public abstract void sendSyncPacket();

        public void sync(FluidStack liquid, int gas_id, int gas_amount) {
            s_liquid = liquid;
            s_gas_id = gas_id;
            s_gas_amount = gas_amount;
            if (!s_liquid.isFluidEqual(c_liquid)) {
                f_liquid = c_liquid.copy();
            }
            //getStorage(true).setFluid(f_liquid);
        }

        //SERVER SIDE ONLY!
        public EnderLiquidStorage getStorage(boolean client) {
            return (EnderLiquidStorage) EnderStorageManager.instance(client).getStorage(frequency, "liquid");
        }
        @Optional.Method(modid = "mekanism")
        public EnderGasStorage getGasStorage(boolean client) {
            return (EnderGasStorage) EnderStorageManager.instance(client).getStorage(frequency, "gas");
        }
    }

    public static class PlayerItemTankState extends TankState {

        private EntityPlayerMP player;
        private boolean tracking;

        public PlayerItemTankState(EntityPlayerMP player, AbstractEnderStorage storage) {
            this.player = player;
            setFrequency(storage.freq);
            tracking = true;
        }

        public PlayerItemTankState() {
        }

        @Override
        public void sendSyncPacket() {
            if (!tracking) {
              //  return;
            }

            PacketCustom packet = new PacketCustom(EnderStorageSPH.channel, 4);
            getStorage(false).freq.writeToPacket(packet);
            //packet.writeString(storage.owner);
            packet.writeFluidStack(s_liquid);
            packet.writeInt(s_gas_amount);
            packet.writeInt(s_gas_amount);
            packet.sendToPlayer(player);
        }

        public void setTracking(boolean t) {
            tracking = t;
        }

        @Override
        public void update(boolean client) {
            super.update(client);
        }
    }

    public static class PlayerItemTankCache {

        private boolean client;
        private HashMap<String, PlayerItemTankState> tankStates = new HashMap<>();
        //client
        private HashSet<Frequency> b_visible;
        private HashSet<Frequency> a_visible;
        //server
        private EntityPlayerMP player;

        public PlayerItemTankCache(EntityPlayerMP player) {
            this.player = player;
            client = false;
        }

        public PlayerItemTankCache() {
            client = true;
            a_visible = new HashSet<>();
            b_visible = new HashSet<>();
        }

        public void track(Frequency freq, boolean t) {
            String key = freq.toString();
            PlayerItemTankState state = tankStates.get(key);
            if (state == null) {
                if (!t) {
                    return;
                }
                try {
                tankStates.put(key, state = new PlayerItemTankState(player, (EnderLiquidStorage) EnderStorageManager.instance(false).getStorage(freq, "liquid")));
                tankStates.put(key, state = new PlayerItemTankState(player, (EnderGasStorage) EnderStorageManager.instance(false).getStorage(freq, "gas")));
                } catch (Exception ignore) { }
            }
            state.setTracking(t);
        }

        public void sync(Frequency freq, FluidStack liquid, int gas_id, int gas_amount) {
            String key = freq.toString();
            PlayerItemTankState state = tankStates.computeIfAbsent(key, k -> new PlayerItemTankState());
            state.sync(liquid, gas_id, gas_amount);
        }

        public void update() {
            for (Map.Entry<String, PlayerItemTankState> entry : tankStates.entrySet()) {
                entry.getValue().update(client);
            }

            if (client) {
                Sets.SetView<Frequency> new_visible = Sets.difference(a_visible, b_visible);
                Sets.SetView<Frequency> old_visible = Sets.difference(b_visible, a_visible);
                boolean forceUpddate = net.minecraft.client.Minecraft.getMinecraft().world.getTotalWorldTime() % 10 == 0;
                if (!new_visible.isEmpty() || !old_visible.isEmpty() || forceUpddate) {
                    PacketCustom packet = new PacketCustom(EnderStorageCPH.channel, 1);
                    packet.writeBoolean(forceUpddate);
                    packet.writeShort(new_visible.size());
                    new_visible.forEach(freq -> freq.writeToPacket(packet));

                    packet.writeShort(old_visible.size());
                    old_visible.forEach(freq -> freq.writeToPacket(packet));

                    packet.sendToServer();
                }

                HashSet<Frequency> temp = b_visible;
                temp.clear();
                b_visible = a_visible;
                a_visible = temp;
            }
        }

        public FluidStack getLiquid(Frequency freq) {
            String key = freq.toString();
            a_visible.add(freq);
            PlayerItemTankState state = tankStates.get(key);
            return state == null ? FluidUtils.emptyFluid() : state.c_liquid;
        }

        public void handleVisiblityPacket(PacketCustom packet) {
            boolean force = packet.readBoolean();
            int k = packet.readUShort();
            for (int i = 0; i < k; i++) {
                track(Frequency.readFromPacket(packet), true);
            }
            k = packet.readUShort();
            for (int i = 0; i < k; i++) {
                track(Frequency.readFromPacket(packet), false);
            }
        }
    }

    private static HashMap<String, PlayerItemTankCache> playerItemTankStates;
    private static PlayerItemTankCache clientState;

    public static void syncClient(Frequency freq, FluidStack liquid, int gas_id, int gas_amount) {
        clientState.sync(freq, liquid, gas_id, gas_amount);
    }

    public static FluidStack getClientLiquid(Frequency freq) {
        if (clientState != null) {
            return clientState.getLiquid(freq);
        }
        return FluidUtils.emptyFluid();
    }

    public static void handleVisiblityPacket(EntityPlayerMP player, PacketCustom packet) {
        playerItemTankStates.get(player.getName()).handleVisiblityPacket(packet);
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        playerItemTankStates.put(event.player.getName(), new PlayerItemTankCache((EntityPlayerMP) event.player));
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (playerItemTankStates != null) { //sometimes world unloads before players logout
            playerItemTankStates.remove(event.player.getName());
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        playerItemTankStates.put(event.player.getName(), new PlayerItemTankCache((EntityPlayerMP) event.player));
    }

    @SubscribeEvent
    public void tickEnd(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && playerItemTankStates != null) {
            for (Map.Entry<String, PlayerItemTankCache> entry : playerItemTankStates.entrySet()) {
                entry.getValue().update();
            }
        }
    }

    @SubscribeEvent
    public void tickEnd(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            if (ClientUtils.inWorld() && clientState != null) {
                clientState.update();
            }
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (!event.getWorld().isRemote && !ServerUtils.mc().isServerRunning()) {
            playerItemTankStates = null;
        }
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (event.getWorld().isRemote) {
            clientState = new PlayerItemTankCache();
        } else if (playerItemTankStates == null) {
            playerItemTankStates = new HashMap<>();
        }
    }
}
