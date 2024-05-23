package tfar.nations2.level;

import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.objects.Object2LongArrayMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tfar.nations2.Nations2;

import java.util.concurrent.TimeUnit;

public class OfflineTrackerData extends SavedData {

    Object2LongMap<GameProfile> lastSeen = new Object2LongArrayMap<>();

    private static final Logger LOG = LogManager.getLogger();

    private static OfflineTrackerData getOrCreateInstance(ServerLevel serverLevel) {
        return serverLevel.getDataStorage()
                .computeIfAbsent(OfflineTrackerData::loadStatic, OfflineTrackerData::new, Nations2.MOD_ID+"_offline_tracker");
    }

    public static OfflineTrackerData getOrCreateDefaultInstance(MinecraftServer server) {
        return getOrCreateInstance(server.overworld());
    }

    public void saveTimeStamp(ServerPlayer loggingOff) {
        lastSeen.put(loggingOff.getGameProfile(),loggingOff.server.overworld().getGameTime());
        setDirty();
    }

    public long ticksSinceOnline(MinecraftServer server,GameProfile profile) {
        if (!lastSeen.containsKey(profile)) {
            LOG.warn(profile +" was never on this server");
            return -1;
        }
        return server.overworld().getGameTime() - lastSeen.getLong(profile);
    }

    @Override
    public CompoundTag save(CompoundTag compoundTag) {
        ListTag listTag = new ListTag();

        for (Object2LongMap.Entry<GameProfile> entry : lastSeen.object2LongEntrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.put("profile", NbtUtils.writeGameProfile(new CompoundTag(),entry.getKey()));
            tag.putLong("lastSeen",entry.getLongValue());
            listTag.add(tag);
        }
        compoundTag.put("profiles",listTag);
        return compoundTag;
    }

    public void load(CompoundTag tag) {
        lastSeen.clear();
        ListTag listTag = tag.getList("profiles", Tag.TAG_COMPOUND);
        for (Tag iTag :listTag) {
            CompoundTag compoundTag = (CompoundTag) iTag;
            lastSeen.put(NbtUtils.readGameProfile(compoundTag.getCompound("profile")),compoundTag.getLong("lastSeen"));
        }
    }

    public static OfflineTrackerData loadStatic(CompoundTag compoundTag) {
        OfflineTrackerData id = new OfflineTrackerData();
        id.load(compoundTag);
        return id;
    }

    public static String formatTime(long ticks)  {
        return formatElapsedSecs(ticks/20);
    }

    /**
     * Formats an elapsed time in seconds as days, hh:mm:ss.
     *
     * @param secs
     *            Elapsed seconds
     * @return A string representation of elapsed time
     */
    public static String formatElapsedSecs(long secs) {
        long eTime = secs;
        final long days = TimeUnit.SECONDS.toDays(eTime);
        eTime -= TimeUnit.DAYS.toSeconds(days);
        final long hr = TimeUnit.SECONDS.toHours(eTime);
        eTime -= TimeUnit.HOURS.toSeconds(hr);
        final long min = TimeUnit.SECONDS.toMinutes(eTime);
        eTime -= TimeUnit.MINUTES.toSeconds(min);
        final long sec = eTime;
        return String.format("%d days, %02d:%02d:%02d", days, hr, min, sec);
    }

}
