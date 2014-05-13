	package li.cil.occ.mods.computercraft;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import li.cil.oc.api.FileSystem;
import li.cil.oc.api.Network;
import li.cil.oc.api.network.Arguments;
import li.cil.oc.api.network.Context;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.occ.OpenComponents;
import net.minecraft.world.World;

import com.google.common.collect.Iterables;
import com.google.gson.Gson;

import cpw.mods.fml.common.Loader;
import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.filesystem.IWritableMount;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

public final class DriverPeripheral16 extends DriverPeripheral<IPeripheral> {
    private static final Method ComputerCraft_getPeripheralAt;
    
    static {
        Method getPeripheralAt = null;
        try {
            getPeripheralAt = Class.forName("dan200.computercraft.ComputerCraft").
                    getMethod("getPeripheralAt", World.class, int.class, int.class, int.class, int.class);
        } catch (Exception e) {
            if (Loader.instance().getIndexedModList().get("ComputerCraft").getVersion().startsWith("1.6")) {
                OpenComponents.Log.log(Level.WARNING, "Error getting access to ComputerCraft peripherals.", e);
            }
        }
        ComputerCraft_getPeripheralAt = getPeripheralAt;
    }

    @Override
    protected IPeripheral findPeripheral(World world, int x, int y, int z) {
        if (ComputerCraft_getPeripheralAt != null) {
            try {
                return (IPeripheral) ComputerCraft_getPeripheralAt.invoke(null, world, x, y, z, -1);
            } catch (Exception e) {
                OpenComponents.Log.log(Level.WARNING, String.format("Error accessing ComputerCraft peripheral @ (%d, %d, %d).", x, y, z), e);
            }
        }
        return null;
    }

    @Override
    public ManagedEnvironment createEnvironment(final World world, final int x, final int y, final int z) {
        return new Environment(findPeripheral(world, x, y, z));
    }

    public static class Environment extends li.cil.oc.api.prefab.ManagedEnvironment implements li.cil.oc.api.network.ManagedPeripheral {
    	public final IPeripheral peripheral;
    	public final SupportedLuaContext luaCon = new SupportedLuaContext(this);
    	protected HashMap<String, Object[]> events = new HashMap<String, Object[]>();
    	
        protected final List<String> _methods;

        protected final Map<String, FakeComputerAccess> accesses = new HashMap<String, FakeComputerAccess>();

        public Environment(final IPeripheral peripheral) {
            this.peripheral = peripheral;
            _methods = Arrays.asList(peripheral.getMethodNames());
            node = Network.newNode(this, Visibility.Network).create();
        }

        @Override
        public String[] methods() {
            return peripheral.getMethodNames();
        }

        @Override
        public Object[] invoke(final String method, final Context context, final Arguments args) throws Exception {
            final int index = _methods.indexOf(method);
            if (index < 0) {
                throw new NoSuchMethodException();
            }
            final Object[] argArray = Iterables.toArray(args, Object.class);
            for (int i = 0; i < argArray.length; ++i) {
                if (argArray[i] instanceof byte[]) {
                    argArray[i] = new String((byte[]) argArray[i], "UTF-8");
                }
            }
            final FakeComputerAccess access;
            if (accesses.containsKey(context.node().address())) {
                access = accesses.get(context.node().address());
            } else {
                // The calling contexts is not visible to us, meaning we never got
                // an onConnect for it. Create a temporary access.
                access = new FakeComputerAccess(this, context);
            }

            return peripheral.callMethod(access, luaCon, index, argArray);
        }

        @Override
        public void onConnect(final Node node) {
            super.onConnect(node);
            if (node.host() instanceof Context) {
                final FakeComputerAccess access = new FakeComputerAccess(this, (Context) node.host());
                accesses.put(node.address(), access);
                peripheral.attach(access);
            }
        }

        @Override
        public void onDisconnect(final Node node) {
            super.onDisconnect(node);
            if (node.host() instanceof Context) {
                final FakeComputerAccess access = accesses.remove(node.address());
                if (access != null) {
                    peripheral.detach(access);
                }
            } else if (node == this.node) {
                for (FakeComputerAccess access : accesses.values()) {
                    peripheral.detach(access);
                    access.close();
                }
                accesses.clear();
            }
        }

        /**
         * Map interaction with the computer to our format as good as we can.
         */
        private static class FakeComputerAccess implements IComputerAccess {
            protected final Environment owner;
            protected final Context context;
            protected final Map<String, li.cil.oc.api.network.ManagedEnvironment> fileSystems = new HashMap<String, li.cil.oc.api.network.ManagedEnvironment>();

            public FakeComputerAccess(final Environment owner, final Context context) {
                this.owner = owner;
                this.context = context;
            }

            public void close() {
                for (li.cil.oc.api.network.ManagedEnvironment fileSystem : fileSystems.values()) {
                    fileSystem.node().remove();
                }
                fileSystems.clear();
            }

            @Override
            public String mount(final String desiredLocation, final IMount mount) {
                if (fileSystems.containsKey(desiredLocation)) {
                    return null;
                }
                return mount(desiredLocation, FileSystem.asManagedEnvironment(FileSystem.fromComputerCraft(mount)));
            }

            @Override
            public String mountWritable(final String desiredLocation, final IWritableMount mount) {
                if (fileSystems.containsKey(desiredLocation)) {
                    return null;
                }
                return mount(desiredLocation, FileSystem.asManagedEnvironment(FileSystem.fromComputerCraft(mount)));
            }

            private String mount(final String path, final li.cil.oc.api.network.ManagedEnvironment fileSystem) {
                fileSystems.put(path, fileSystem); //TODO This is per peripheral/Environment. It would be far better with per computer
                context.node().connect(fileSystem.node());
                return path;
            }

            @Override
            public void unmount(final String location) {
                final li.cil.oc.api.network.ManagedEnvironment fileSystem = fileSystems.remove(location);
                if (fileSystem != null) {
                    fileSystem.node().remove();
                }
            }

            @Override
            public int getID() {
                return context.node().address().hashCode();
            }

            @Override
            public void queueEvent(final String event, final Object[] arguments) {
            	owner.events.put(event, arguments);
            	Gson g = new Gson();
            	if(event.equals("op_tick_sync")){
            		System.out.println("HMM mam to id:" + g.toJson(arguments));
            		
            	}
            	
                context.signal(event, arguments);
            }

            @Override
            public String getAttachmentName() {
                return owner.node.address();
            }
        }

        /**
         * Since we abstract away anything language specific, we cannot support the
         * Lua context specific operations ComputerCraft provides.
         */
        private final static class SupportedLuaContext implements ILuaContext {
            Environment owner;
            private SupportedLuaContext(Environment owner) {
            	this.owner = owner;
            }

            @Override
            public Object[] pullEvent(final String filter) throws Exception {
            	while(owner.events.get(filter)==null){
            		Thread.sleep(50);
            	}
            	Object[] arr={null,owner.events.get(filter)[0]}; 
            	return arr;
            }

            @Override
            public Object[] pullEventRaw(final String filter) throws InterruptedException {
            	while(owner.events.get(filter)==null){
            		Thread.sleep(50);
            	}
            	Object[] arr={null,owner.events.get(filter)[0]}; 
            	return arr;
            }

            @Override
            public Object[] yield(final Object[] arguments) throws InterruptedException {
                throw new UnsupportedOperationException();
            }
        }
    }
}
