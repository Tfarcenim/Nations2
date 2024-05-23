package tfar.nations2;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import tfar.nations2.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.TreeMap;

// This class is part of the common project meaning it is shared between all supported loaders. Code written here can only
// import and access the vanilla codebase, libraries used by vanilla, and optionally third party libraries that provide
// common compatible binaries. This means common code can not directly use loader specific concepts such as Forge events
// however it will be compatible with all supported mod loaders.
public class Nations2 {

    public static final String MOD_ID = "nations2";
    public static final String MOD_NAME = "Nations2";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    // The loader specific projects are able to import and use any code from the common project. This allows you to
    // write the majority of your code here and load it from your loader specific projects. This example has some
    // code that gets invoked by the entry point of the loader specific projects.
    public static void init() {
    }


    public static boolean onMovePacket(ServerGamePacketListenerImpl packetHandler, ServerPlayer player, ServerboundMovePlayerPacket packet) {
        NationData nationData = NationData.getNationInstance(player.getLevel());
        if (nationData != null) {
            Siege siege = nationData.getActiveSiege();
            if (siege != null) {
                if (siege.isAttacking(player, nationData) && siege.shouldBlockAttackers() && TeamHandler.isPlayerNearClaim(player, siege.getClaimPos())) {
                    if (packet.hasPosition()) {
                        player.sendSystemMessage(Component.literal("Can't move into enemy claim during start of siege"));
                        Vec3 newPos = getNearestLegalPosition(player.position(), siege.getClaimPos(), 1);
                        packetHandler.teleport(newPos.x, newPos.y, newPos.z, player.getYRot(), player.getXRot());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static Vec3 getNearestLegalPosition(Vec3 position, ChunkPos claim, int radius) {
        double playerX = position.x;
        double playerZ = position.z;

        int minLegalX = claim.getMinBlockX() - 16 * radius;
        int minLegalZ = claim.getMinBlockZ() - 16 * radius;

        int maxLegalX = claim.getMaxBlockX() + 16 * radius;
        int maxLegalZ = claim.getMaxBlockZ() + 16 * radius;

        double toMoveZ1 = maxLegalZ - playerZ;//distance to south border
        double toMoveX1 = maxLegalX - playerX;//distance to east border

        double toMoveZ2 = playerZ - minLegalZ;//distance to north border
        double toMoveX2 = playerX - minLegalX;//distance to west border

        TreeMap<Double, Direction> map = new TreeMap<>();
        map.put(toMoveZ1, Direction.SOUTH);
        map.put(toMoveX1, Direction.EAST);
        map.put(toMoveZ2, Direction.NORTH);
        map.put(toMoveX2, Direction.WEST);
        double y = position.y;

        Direction toMove = map.get(map.keySet().iterator().next());

        double backTeleport = .1;

        switch (toMove) {
            case NORTH -> {
                return new Vec3(playerX, y, minLegalZ - backTeleport);
            }
            case EAST -> {
                return new Vec3(maxLegalX + 1 + backTeleport, y, playerZ);
            }
            case SOUTH -> {
                return new Vec3(playerX, y, maxLegalZ + 1 + backTeleport);
            }
            case WEST -> {
                return new Vec3(minLegalX - backTeleport, y, playerZ);
            }
            default -> {
                return new Vec3(playerX, y, minLegalZ - backTeleport);
            }
        }
    }

}