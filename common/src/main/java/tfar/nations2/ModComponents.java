package tfar.nations2;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class ModComponents {

   public static final MutableComponent NO = Component.literal("No");
   public static final MutableComponent YES = Component.literal("Yes");
   public static final MutableComponent NO_NATIONS_OUTSIDE_OF_OVERWORLD = Component.literal("Can't use Nations outside of overworld");

   public static final MutableComponent CREATE_NATION = Component.literal("Create Nation?");
   public static final MutableComponent SIEGE_TERMINATED = Component.literal("Siege has been terminated");
   public static final MutableComponent ATTACKERS_WIN = Component.literal("Attackers are victorious");
   public static final MutableComponent ATTACKERS_DEFEATED = Component.literal("Attackers have been defeated");

}
