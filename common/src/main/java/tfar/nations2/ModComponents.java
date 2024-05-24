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
   public static final MutableComponent INSUFFICIENT_NATION_POWER = Component.literal("Insufficient nation power");
   public static final MutableComponent CLAIM_LAND = Component.literal("Claim land");
   public static final MutableComponent UNCLAIM_LAND = Component.literal("Unclaim land");

   public static final MutableComponent ALREADY_A_CLAIM = Component.literal("There's already a claim at this chunk");
   public static final MutableComponent MAKE_ALLIANCE = Component.literal("Make Alliance");
   public static final MutableComponent NATION_POLITICS = Component.literal("Nation Politics");

   public static final MutableComponent MAKE_ENEMY = Component.literal("Make Enemy");

   public static final MutableComponent MAKE_NEUTRAL = Component.literal("Make Neutral");
   public static final MutableComponent NATION_STATUS = Component.literal("Nation Status");
   public static final MutableComponent CANT_MOVE_INTO_CLAIM_STAGE_1 = Component.literal("Can't move into enemy claim during start of siege");
   public static final MutableComponent TOO_CLOSE_TO_START_SIEGE = Component.literal("Can't start siege with any nation members within 16 blocks of enemy claim");

   public static final MutableComponent SELECT_ENEMY_NATION = Component.literal("Select Enemy Nation");

}
