package li.cil.occ.mods.cofh.tileentity;

import cofh.api.tileentity.ISecurable;
import li.cil.oc.api.network.Arguments;
import li.cil.oc.api.network.Callback;
import li.cil.oc.api.network.Context;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.prefab.DriverTileEntity;
import li.cil.occ.mods.ManagedTileEntityEnvironment;
import net.minecraft.world.World;
import org.apache.commons.lang3.text.WordUtils;

public final class DriverSecureTile extends DriverTileEntity {
    @Override
    public Class<?> getTileEntityClass() {
        return ISecurable.class;
    }

    @Override
    public ManagedEnvironment createEnvironment(final World world, final int x, final int y, final int z) {
        return new Environment((ISecurable) world.getTileEntity(x, y, z));
    }

    public static final class Environment extends ManagedTileEntityEnvironment<ISecurable> {
        public Environment(final ISecurable tileEntity) {
            super(tileEntity, "secure_tile");
        }

        @Callback(doc = "function(name:string):boolean --  Returns whether the player with the given name can access the component")
        public Object[] canPlayerAccess(final Context context, final Arguments args) {
            return new Object[]{tileEntity.canPlayerAccess(args.checkString(0))};
        }

        @Callback(doc = "function():string --  Returns the type of the access.")
        public Object[] getAccess(final Context context, final Arguments args) {
            return new Object[]{WordUtils.capitalize(tileEntity.getAccess().name())};
        }

        @Callback(doc = "function():string --  Returns the name of the owner.")
        public Object[] getOwnerName(final Context context, final Arguments args) {
            return new Object[]{tileEntity.getOwnerName()};
        }
    }
}
