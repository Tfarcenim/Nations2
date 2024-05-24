package tfar.nations2.platform;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import tfar.nations2.platform.services.IPlatformHelper;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import tk.sciwhiz12.concord.Concord;
import tk.sciwhiz12.concord.msg.Messaging;

public class ForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {

        return "Forge";
    }

    @Override
    public boolean isModLoaded(String modId) {

        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {

        return !FMLLoader.isProduction();
    }

    @Override
    public ServerPlayer getFakePlayer(ServerLevel level, GameProfile gameProfile) {
        return FakePlayerFactory.get(level,gameProfile);
    }

    @Override
    public void sendMessageToDiscord(String text) {
        Messaging.sendToChannel(Concord.getBot().getDiscord(),text);
    }
}