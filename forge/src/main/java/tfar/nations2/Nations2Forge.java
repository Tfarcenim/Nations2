package tfar.nations2;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import tfar.nations2.datagen.Datagen;
import tfar.nations2.nation.Nation;
import tfar.nations2.nation.NationData;
import tfar.nations2.nation.Siege;
import tfar.nations2.nation.TeamHandler;

import java.util.List;

@Mod(Nations2.MOD_ID)
public class Nations2Forge {

    public Nations2Forge() {

        // This method is invoked by the Forge mod loader when it is ready
        // to load your mod. You can access Forge and Common code in this
        // project.

        // Use Forge to bootstrap the Common mod.
        // Nations.LOG.info("Hello Forge world!");



        MinecraftForge.EVENT_BUS.addListener(this::onCommandRegister);
        MinecraftForge.EVENT_BUS.addListener(this::playerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(this::playerLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(this::blockBreak);
        MinecraftForge.EVENT_BUS.addListener(this::blockInteract);
        MinecraftForge.EVENT_BUS.addListener(this::levelTick);
        MinecraftForge.EVENT_BUS.addListener(this::teleportEvent);
        MinecraftForge.EVENT_BUS.addListener(this::teleportPearlEvent);
        MinecraftForge.EVENT_BUS.addListener(this::explosion);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW,this::playerDied);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(Datagen::gather);

        if (FMLEnvironment.dist.isClient()) {
            FMLJavaModLoadingContext.get().getModEventBus().addListener(Nations2ClientForge::clientSetup);
        }

        Nations2.init();
    }

    private void teleportEvent(EntityTeleportEvent.ChorusFruit event) {
        Entity entity = event.getEntity();
        if (entity instanceof ServerPlayer serverPlayer) {
            if (onTeleportForge(serverPlayer, event.getTarget())) {
                event.setCanceled(true);
            }
        }
    }

    private void teleportPearlEvent(EntityTeleportEvent.EnderPearl event) {
        Entity entity = event.getEntity();
        if (entity instanceof ServerPlayer serverPlayer) {
            if (onTeleportForge(serverPlayer, event.getTarget())) {
                event.setCanceled(true);
            }
        }
    }

    private boolean onTeleportForge(ServerPlayer player, Vec3 target) {
        NationData nationData = NationData.getOrCreateNationInstance(player.serverLevel());
        Siege siege = nationData.getActiveSiege();
        if (siege != null) {
            if (siege.isAttacking(player, nationData) && siege.shouldBlockAttackers() && TeamHandler.isPointInArea(target, siege.getClaimPos(), 1)) {
                player.sendSystemMessage(Component.literal("Can't move into enemy claim during start of siege"));
                return true;
            }
        }
        return false;
    }

    private void levelTick(TickEvent.LevelTickEvent event) {
        if (event.level instanceof ServerLevel serverLevel && event.phase == TickEvent.Phase.START) {
            NationData nationData = NationData.getNationInstance(serverLevel);
            if (nationData != null) {
                nationData.tick(serverLevel);
            }
        }
    }

    private void blockInteract(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        BlockPos pos = event.getPos();
        BlockState state = player.level().getBlockState(pos);
        ChunkPos chunkPos = new ChunkPos(pos);
        if (player instanceof ServerPlayer serverPlayer) {
            NationData nationData = NationData.getOrCreateDefaultNationsInstance(serverPlayer.server);
            Nation nationChunk = nationData.getNationAtChunk(chunkPos);
            if (nationChunk == null) return;
            if (state.is(ModTags.CLAIM_RESISTANT)) {
                Nation nation = nationData.getNationOf(serverPlayer);
                if (nationChunk == nation || nationChunk.isAlly(nation)) {

                } else {
                    Siege siege = nationData.getActiveSiege();
                    if (siege != null && siege.getClaimPos().equals(chunkPos) && !siege.shouldBlockAttackers()) {
                        return;
                    }
                    event.setCanceled(true);
                }
            }
        }
    }

    private void blockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        ChunkPos chunkPos = new ChunkPos(pos);
        if (player instanceof ServerPlayer serverPlayer) {
            NationData nationData = NationData.getOrCreateDefaultNationsInstance(serverPlayer.server);
            Nation nationAtChunk = nationData.getNationAtChunk(chunkPos);
            if (nationAtChunk == null) return;
            Nation nation = nationData.getNationOf(serverPlayer);
            if (nationAtChunk == nation || nationAtChunk.isAlly(nation)) {

            } else {
                Siege siege = nationData.getActiveSiege();
                if (siege != null && siege.getClaimPos().equals(chunkPos) && !siege.shouldBlockAttackers()) {
                    return;
                }
                event.setCanceled(true);
            }
        }
    }

    private void explosion(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level instanceof ServerLevel serverLevel) {
            List<BlockPos> posList = event.getAffectedBlocks();
            NationData nationData = NationData.getOrCreateDefaultNationsInstance(serverLevel.getServer());
            posList.removeIf(pos -> {
                Nation nation = nationData.getNationAtChunk(new ChunkPos(pos));
                return nation != null;
            });
        }
    }

    private void playerDied(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            NationData data = NationData.getOrCreateDefaultNationsInstance(serverPlayer.server);
            Siege siege = data.getActiveSiege();
            if (siege != null) {
                siege.attackerDefeated(serverPlayer);
                if (siege.getStage() != Siege.Stage.ONE) {
                    siege.defenderDefeated(serverPlayer);
                }
            }
        }
    }
    private void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Nations2.logout((ServerPlayer) event.getEntity());
    }

    private void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Nations2.login((ServerPlayer) event.getEntity());
    }

    private void onCommandRegister(RegisterCommandsEvent event) {
        Nations2.onCommandRegister(event.getDispatcher());
    }
}