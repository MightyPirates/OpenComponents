package li.cil.occ.mods.appeng;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.GridAccessException;
import appeng.tile.networking.TileController;
import appeng.util.item.AEItemStack;
import li.cil.oc.api.network.Arguments;
import li.cil.oc.api.network.Callback;
import li.cil.oc.api.network.Context;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.prefab.AbstractValue;
import li.cil.oc.api.prefab.DriverTileEntity;
import li.cil.occ.OpenComponents;
import li.cil.occ.mods.ManagedTileEntityEnvironment;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Future;

public class DriverController extends DriverTileEntity {
    @Override
    public Class<?> getTileEntityClass() {
        return TileController.class;
    }

    @Override
    public ManagedEnvironment createEnvironment(final World world, final int x, final int y, final int z) {
        return new Environment((TileController) world.getTileEntity(x, y, z));
    }

    public static final class Environment extends ManagedTileEntityEnvironment<TileController> {
        public Environment(final TileController tileEntity) {
            super(tileEntity, "me_controller");
        }

        @Callback(doc = "function():table -- Get a list of tables representing the available CPUs in the network.")
        public Object[] getCpus(final Context context, final Arguments args) throws GridAccessException {
            final ICraftingGrid grid = tileEntity.getProxy().getCrafting();
            final ArrayList<Object> result = new ArrayList<Object>();
            for (final ICraftingCPU cpu : grid.getCpus()) {
                final HashMap<String, Object> map = new HashMap<String, Object>();
                map.put("name", cpu.getName());
                map.put("storage", cpu.getAvailableStorage());
                map.put("coprocessor", cpu.getCoProcessors());
                map.put("busy", cpu.isBusy());

                result.add(map);
            }
            return new Object[]{result};
        }

        @Callback(doc = "function():table -- Get a list of known item recipes. These can be used to issue crafting requests.")
        public Object[] getCraftables(final Context context, final Arguments args) throws GridAccessException {
            final IMEMonitor<IAEItemStack> inventory = tileEntity.getProxy().getStorage().getItemInventory();
            final ArrayList<Craftable> result = new ArrayList<Craftable>();
            for (final IAEItemStack stack : inventory.getStorageList()) {
                if (stack.isCraftable()) {
                    result.add(new Craftable(tileEntity, stack));
                }
            }
            return new Object[]{result};
        }

        @Callback(doc = "function():table -- Get a list of the stored items in the network.")
        public Object[] getItemsInNetwork(final Context context, final Arguments args) throws GridAccessException {
            final IMEMonitor<IAEItemStack> inventory = tileEntity.getProxy().getStorage().getItemInventory();
            final ArrayList<ItemStack> result = new ArrayList<ItemStack>();
            for (final IAEItemStack stack : inventory.getStorageList()) {
                result.add(stack.getItemStack());
            }
            return new Object[]{result};
        }

        @Callback(doc = "function():table -- Get a list of the stored fluids in the network.")
        public Object[] getFluidsInNetwork(final Context context, final Arguments args) throws GridAccessException {
            final IMEMonitor<IAEFluidStack> inventory = tileEntity.getProxy().getStorage().getFluidInventory();
            final ArrayList<FluidStack> result = new ArrayList<FluidStack>();
            for (final IAEFluidStack stack : inventory.getStorageList()) {
                result.add(stack.getFluidStack());
            }
            return new Object[]{result};
        }

        @Callback(doc = "function():number -- Get the average power injection into the network.")
        public Object[] getAvgPowerInjection(final Context context, final Arguments args) throws GridAccessException {
            return new Object[]{tileEntity.getProxy().getEnergy().getAvgPowerInjection()};
        }

        @Callback(doc = "function():number -- Get the average power usage of the network.")
        public Object[] getAvgPowerUsage(final Context context, final Arguments args) throws GridAccessException {
            return new Object[]{tileEntity.getProxy().getEnergy().getAvgPowerUsage()};
        }

        @Callback(doc = "function():number -- Get the idle power usage of the network.")
        public Object[] getIdlePowerUsage(final Context context, final Arguments args) throws GridAccessException {
            return new Object[]{tileEntity.getProxy().getEnergy().getIdlePowerUsage()};
        }

        @Callback(doc = "function():number -- Get the maximum stored power in the network.")
        public Object[] getMaxStoredPower(final Context context, final Arguments args) throws GridAccessException {
            return new Object[]{tileEntity.getProxy().getEnergy().getMaxStoredPower()};
        }

        @Callback(doc = "function():number -- Get the stored power in the network. ")
        public Object[] getStoredPower(final Context context, final Arguments args) throws GridAccessException {
            return new Object[]{tileEntity.getProxy().getEnergy().getStoredPower()};
        }
    }

    public static final class Craftable extends AbstractValue {
        private IAEItemStack stack;
        private TileController controller;

        public Craftable(final TileController controller, final IAEItemStack stack) {
            this.controller = controller;
            this.stack = stack.copy();
        }

        // Default constructor for loading.
        public Craftable() {
        }

        @Callback(doc = "function():table -- Returns the item stack representation of the crafting result.")
        public Object[] getItemStack(final Context context, final Arguments args) {
            return new Object[]{stack.getItemStack()};
        }

        @Callback(doc = "function([int amount]):userdata -- Requests the item to be crafted, returning an object that allows tracking the crafting status.")
        public Object[] request(final Context context, final Arguments args) throws GridAccessException {
            if (controller == null || controller.isInvalid()) {
                return new Object[]{null, "no controller"};
            }
            final int count = args.count() > 0 ? args.checkInteger(0) : 1;
            final IAEItemStack request = stack.copy();
            request.setStackSize(count);

            final ICraftingGrid craftingGrid = controller.getProxy().getCrafting();
            final BaseActionSource source = new MachineSource(controller);
            final Future<ICraftingJob> future = craftingGrid.beginCraftingJob(controller.getWorldObj(), controller.getProxy().getGrid(), source, request, null);

            // OK, so this is, admittedly, a little messy. AE2 computes stuff for requests in the background, meaning
            // we have to wait for the future we get to have a result. To avoid having each controller tick all the
            // time - and having to modify stuff in the controller from the "outside" - this is done in yet another
            // background task that is scheduled to be executed by our global executor. Once that is done we schedule
            // a tick handler to submit the job from the main server thread, to avoid concurrent modification errors.
            final CraftingStatus status = new CraftingStatus();
            OpenComponents.Executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        final ICraftingJob job = future.get();
                        OpenComponents.schedule(new Runnable() {
                            @Override
                            public void run() {
                                status.setLink(craftingGrid.submitJob(job, null, null, true, source));
                            }
                        });
                    } catch (Exception e) {
                        OpenComponents.Log.debug("Error submitting job to AE2.", e);
                        status.cancel();
                    }
                }
            });

            return new Object[]{status};
        }

        @Override
        public void load(final NBTTagCompound nbt) {
            super.load(nbt);
            stack = AEItemStack.loadItemStackFromNBT(nbt);
            if (nbt.hasKey("dimension")) {
                final int dimension = nbt.getInteger("dimension");
                final int x = nbt.getInteger("x");
                final int y = nbt.getInteger("y");
                final int z = nbt.getInteger("z");
                // We have to fetch the tile entity in a delayed fashion be cause accessing the
                // world here can lead to an infinite loop (because the chunk is not fully loaded
                // so it is loaded again, which will in turn call this, and so on).
                OpenComponents.schedule(new Runnable() {
                    @Override
                    public void run() {
                        final World world = DimensionManager.getWorld(dimension);
                        final TileEntity tileEntity = world.getTileEntity(x, y, z);
                        if (tileEntity != null && tileEntity instanceof TileController) {
                            controller = (TileController) tileEntity;
                        }
                    }
                });
            }
        }

        @Override
        public void save(final NBTTagCompound nbt) {
            super.save(nbt);
            stack.writeToNBT(nbt);
            if (controller != null && !controller.isInvalid()) {
                nbt.setInteger("dimension", controller.getWorldObj().provider.dimensionId);
                nbt.setInteger("x", controller.xCoord);
                nbt.setInteger("y", controller.yCoord);
                nbt.setInteger("z", controller.zCoord);
            }
        }
    }

    public static final class CraftingStatus extends AbstractValue {
        private boolean isComputing = true;

        private ICraftingLink link;

        public void setLink(final ICraftingLink value) {
            link = value;
            isComputing = false;
        }

        public void cancel() {
            isComputing = false;
        }

        @Callback(doc = "function():boolean -- Get whether the crafting request has been canceled.")
        public Object[] isCanceled(final Context context, final Arguments args) {
            if (isComputing) return new Object[]{false, "computing"};
            if (link != null) return new Object[]{link.isCanceled()};
            return new Object[]{false, "no link"}; // This is the case after loading.
        }

        @Callback(doc = "function():boolean -- Get whether the crafting request is done.")
        public Object[] isDone(final Context context, final Arguments args) {
            if (isComputing) return new Object[]{false, "computing"};
            if (link != null) return new Object[]{link.isDone()};
            return new Object[]{true, "no link"}; // This is the case after loading.
        }

        @Override
        public void load(final NBTTagCompound nbt) {
            super.load(nbt);
            isComputing = false;
        }
    }
}
