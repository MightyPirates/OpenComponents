package li.cil.occ.mods.appeng;

import li.cil.oc.api.Driver;
import li.cil.occ.mods.IMod;

public class ModAppEng implements IMod {
    public static final String MOD_ID = "appliedenergistics2";

    @Override
    public String getModId() {
        return MOD_ID;
    }

    @Override
    public void initialize() {
        //Driver.add(new DriverCellContainer()); Not possible but maybe keep for reference in case of api change.
        Driver.add(new DriverController());
        Driver.add(new ConverterCellInventory());
    }
}
