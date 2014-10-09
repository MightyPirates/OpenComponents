package li.cil.occ.mods.appeng;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.GridAccessException;
import appeng.tile.networking.TileController;
import appeng.util.item.AEItemStack;
import com.google.common.collect.ImmutableSet;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Created by lordjoda on 30.09.2014.
 */
public class DriverController extends DriverTileEntity {
    @Override
    public Class<?> getTileEntityClass() {
        return TileController.class;
    }

    @Override
    public ManagedEnvironment createEnvironment(final World world, final int x, final int y, final int z) {
        return new Evironment((TileController) world.getTileEntity(x, y, z));
    }

    public static final class Evironment extends ManagedTileEntityEnvironment<TileController> {
        public Evironment(final TileController tileEntity) {
            super(tileEntity, "cell_controller");
        }

        @Callback(doc = "function():table -- Returns a list of tables of the available CPUs in the network.")
        public Object[] getCpus(final Context context, final Arguments args) {
            ICraftingGrid craftingGrid = tileEntity.getActionableNode().getGrid().getCache(ICraftingGrid.class);
            ImmutableSet<ICraftingCPU> cpus = craftingGrid.getCpus();
            ArrayList<Object> objects = new ArrayList<Object>();
            for (ICraftingCPU cpu : cpus) {
                HashMap<String, Object> currentCPU = new HashMap<String, Object>();
                currentCPU.put("name", cpu.getName());
                currentCPU.put("storage", cpu.getAvailableStorage());
                currentCPU.put("coprocessor", cpu.getCoProcessors());
                currentCPU.put("busy", cpu.isBusy());

                objects.add(currentCPU);
            }
            return new Object[]{objects};
        }


        @Callback(doc = "function():table -- Returns a list of craftable items.")
        public Object[] getCraftables(final Context context, final Arguments args) {
            IMEMonitor<IAEItemStack> craftingGrid = null;
            try {
                craftingGrid = tileEntity.getProxy().getStorage().getItemInventory();
                ArrayList<CraftingRequest> stacks = new ArrayList<CraftingRequest>();
                for (IAEItemStack stack : craftingGrid.getStorageList()) {
                    if (stack.isCraftable())
                        stacks.add(new CraftingRequest(tileEntity, stack));
                }
                return new Object[]{stacks};
            } catch (GridAccessException e) {
                return new Object[]{null, e.getMessage()};
            }
        }

        @Callback(doc = "function():table -- Returns a list of the available items in the network.")
        public Object[] getItemsInNetwork(final Context context, final Arguments args) {
            IMEMonitor<IAEItemStack> craftingGrid = null;
            try {
                craftingGrid = tileEntity.getProxy().getStorage().getItemInventory();
                ArrayList<ItemStack> stacks = new ArrayList<ItemStack>();
                for (IAEItemStack stack : craftingGrid.getStorageList()) {
                    stacks.add(stack.getItemStack());
                }
                return new Object[]{stacks};
            } catch (GridAccessException e) {
                return new Object[]{null, e.getMessage()};
            }
        }

        @Callback(doc = "function():table -- Returns a list of the available fluids in the network.")
        public Object[] getFluidsInNetwork(final Context context, final Arguments args) {
            IMEMonitor<IAEFluidStack> craftingGrid = null;
            try {
                craftingGrid = tileEntity.getProxy().getStorage().getFluidInventory();
                ArrayList<FluidStack> stacks = new ArrayList<FluidStack>();
                for (IAEFluidStack stack : craftingGrid.getStorageList()) {
                    stacks.add(stack.getFluidStack());
                }
                return new Object[]{stacks};
            } catch (GridAccessException e) {
                return new Object[]{null, e.getMessage()};
            }
        }

        @Callback(doc = "function():table -- Returns the average power injection")
        public Object[] getAvgPowerInjection(final Context context, final Arguments args) {
            try {
                return new Object[]{tileEntity.getProxy().getEnergy().getAvgPowerInjection()};
            } catch (GridAccessException e) {
                return new Object[]{null, e.getMessage()};
            }
        }

        @Callback(doc = "function():table -- Returns the average power usage")
        public Object[] getAvgPowerUsage(final Context context, final Arguments args) {
            try {
                return new Object[]{tileEntity.getProxy().getEnergy().getAvgPowerUsage()};
            } catch (GridAccessException e) {
                return new Object[]{null, e.getMessage()};
            }
        }

        @Callback(doc = "function():table -- Returns the idle power usage")
        public Object[] getIdlePowerUsage(final Context context, final Arguments args) {
            try {
                return new Object[]{tileEntity.getProxy().getEnergy().getIdlePowerUsage()};
            } catch (GridAccessException e) {
                return new Object[]{null, e.getMessage()};
            }
        }

        @Callback(doc = "function():table -- Returns the maximum stored power")
        public Object[] getMaxStoredPower(final Context context, final Arguments args) {
            try {
                return new Object[]{tileEntity.getProxy().getEnergy().getMaxStoredPower()};
            } catch (GridAccessException e) {
                return new Object[]{null, e.getMessage()};
            }
        }

        @Callback(doc = "function():table -- Returns the stored power")
        public Object[] getStoredPower(final Context context, final Arguments args) {
            try {
                return new Object[]{tileEntity.getProxy().getEnergy().getStoredPower()};
            } catch (GridAccessException e) {
                return new Object[]{null, e.getMessage()};
            }
        }

    }

    public static class CraftingStatus extends AbstractValue {
        ICraftingLink link;

        public void setLink(ICraftingLink link) {
            this.link = link;
        }

        @Callback(doc = "function():table -- Returns if the crafting request has been canceled")
        public Object[] isCancled(final Context context, final Arguments args) {
            if (link == null)
                return new Object[]{null, "no link"};
            return new Object[]{link.isCanceled()};
        }

        @Callback(doc = "function():table --  Returns if the crafting request is done")
        public Object[] isDone(final Context context, final Arguments args) {
            if (link == null)
                return new Object[]{true, "no link"};
            return new Object[]{link.isDone()};
        }
    }

    public static class CraftingRequest extends AbstractValue {
        private IAEItemStack stack;
        private TileEntity tileEntity;

        public CraftingRequest(TileController controller, IAEItemStack stack) {
            this.tileEntity = controller;
            this.stack = stack.copy();
        }

        public CraftingRequest() {

        }

        @Override
        public void load(NBTTagCompound nbt) {
            super.load(nbt);
            stack = AEItemStack.loadItemStackFromNBT(nbt);
            if (nbt.hasKey("dimension")) {
                World world = DimensionManager.getWorld(nbt.getInteger("dimension"));
                TileEntity te = world.getTileEntity(nbt.getInteger("x"), nbt.getInteger("y"), nbt.getInteger("z"));
                if (te != null && te instanceof TileController) {
                    tileEntity =  te;
                }
            }
        }

        @Override
        public void save(NBTTagCompound nbt) {
            super.save(nbt);
            stack.writeToNBT(nbt);
            if (tileEntity != null) {
                nbt.setInteger("dimension", tileEntity.getWorldObj().provider.dimensionId);
                nbt.setInteger("x", tileEntity.xCoord);
                nbt.setInteger("y", tileEntity.yCoord);
                nbt.setInteger("z", tileEntity.zCoord);
            }
        }

        @Callback(doc = "function():ItemStack -- Returns the item")
        public Object[] getStack(final Context context, final Arguments args) {
            return new Object[]{stack.getItemStack()};
        }

        @Callback(doc = "function([int amount]):CraftingStatus -- Requests the item to be crafted. Allows specifying the number of crafts ")
        public Object[] request(final Context context, final Arguments args) {
            if (tileEntity == null) {
                return new Object[]{null, "Invalid request object"};
            }
            final TileController controller = (TileController)tileEntity;
            ICraftingGrid craftingGrid = controller.getActionableNode().getGrid().getCache(ICraftingGrid.class);
            final int count = args.count() > 0 ? args.checkInteger(0) : 1;
            final CraftingStatus status = new CraftingStatus();
            try {
                IAEItemStack requestedStack = stack.copy();
                requestedStack.setStackSize(count);
                MachineSource source = new MachineSource((IActionHost) controller.getActionableNode().getMachine());
                final Future<ICraftingJob> future = craftingGrid.beginCraftingJob(tileEntity.getWorldObj(), controller.getProxy().getGrid(), source, requestedStack, null);
                OpenComponents.executorService.submit(new Runnable() {
                    @Override
                    public void run() {

                        try {
                            OpenComponents.schedule(new CraftingRunner(controller, future.get(), status));
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        } catch (ExecutionException e) {
                            e.printStackTrace();
                        }
                    }
                });
            } catch (Throwable e) {
                e.printStackTrace();
            }
            return new Object[]{status};
        }
    }

    public static class CraftingRunner implements Runnable {
        TileController controller;
        ICraftingJob job;
        CraftingStatus status;

        public CraftingRunner(TileController controller, ICraftingJob job, CraftingStatus status) {
            this.controller = controller;
            this.job = job;
            this.status = status;
        }

        @Override
        public void run() {
            IGridNode node = controller.getActionableNode();
            if (node == null)
                return;
            IGrid grid = controller.getActionableNode().getGrid();
            if (grid == null)
                return;
            ICraftingGrid craftingGrid = grid.getCache(ICraftingGrid.class);
            MachineSource source = new MachineSource((IActionHost) controller.getActionableNode().getMachine());
            status.setLink(craftingGrid.submitJob(job, null, null, true, source));
        }
    }
}
