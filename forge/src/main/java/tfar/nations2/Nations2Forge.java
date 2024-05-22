package tfar.nations2;

import net.minecraftforge.fml.common.Mod;

@Mod(Nations2.MOD_ID)
public class Nations2Forge {
    
    public Nations2Forge() {
    
        // This method is invoked by the Forge mod loader when it is ready
        // to load your mod. You can access Forge and Common code in this
        // project.
    
        // Use Forge to bootstrap the Common mod.
        Nations2.LOG.info("Hello Forge world!");
        Nations2.init();
        
    }
}