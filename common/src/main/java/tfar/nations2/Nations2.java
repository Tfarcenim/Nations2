package tfar.nations2;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import tfar.nations2.level.OfflineTrackerData;
import tfar.nations2.nation.*;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfar.nations2.sgui.api.elements.GuiElementBuilder;
import tfar.nations2.sgui.api.gui.SimpleGui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.UnaryOperator;

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

    public static void login(ServerPlayer player) {

        NationData nationData = NationData.getOrCreateDefaultNationsInstance(player.server);
        Siege siege = nationData.getActiveSiege();
        if (siege != null && siege.isPlayerInvolved(player,nationData)) {
            nationData.endSiege(Siege.Result.TERMINATED);
        }
        RemoteTeams.syncFromNations(nationData,player.server);
        RemoteTeams.syncToAllClients(player.server);
    }

    public static void logout(ServerPlayer player) {
        NationData nationData = NationData.getOrCreateDefaultNationsInstance(player.server);
        Siege siege = nationData.getActiveSiege();
        if (siege != null) {
            siege.attackerDefeated(player);
            siege.defenderDefeated(player);
        }

        OfflineTrackerData offlineTrackerData = OfflineTrackerData.getOrCreateDefaultInstance(player.server);
        offlineTrackerData.saveTimeStamp(player);
    }


    public static final Item YES = Items.GREEN_STAINED_GLASS_PANE;
    public static final Item NO = Items.RED_STAINED_GLASS_PANE;
    public static final Item BLANK = Items.LIGHT_GRAY_STAINED_GLASS_PANE;

    public static final String TAG_HIDE_FLAGS = "HideFlags";


    public static void onCommandRegister(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(MOD_ID)
                .executes(Nations2::openGui)
                .then(Commands.literal("rename")

                        .then(Commands.argument("nation", StringArgumentType.string()).suggests(LEADER_OR_OP)
                                .then(Commands.argument("name",StringArgumentType.string())
                                        .executes(Nations2::renameNation)
                                )
                        )));

        dispatcher.register(Commands.literal("nationsop")
                .requires(p -> p.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(Nations2::createNation)))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.string()).suggests(ALL_NATIONS)
                                .executes(Nations2::removeNation)))
                .then(Commands.literal("siege")
                        .then(Commands.literal("stop")
                                .executes(Nations2::stopSiege)
                        ))

        );
    }

    private static int stopSiege(CommandContext<CommandSourceStack> commandContext) {
        NationData nationData = getOverworldInstance(commandContext);
        boolean worked = nationData.endSiege(Siege.Result.TERMINATED);
        if (worked) {
            commandContext.getSource().sendSuccess(() -> Component.literal("Active siege cancelled"),false);
            return 1;
        } else {
            commandContext.getSource().sendFailure(Component.literal("No active siege"));
            return 0;
        }
    }

    private static int createNation(CommandContext<CommandSourceStack> commandContext) {
        NationData nationData = getOverworldInstance(commandContext);
        String string = StringArgumentType.getString(commandContext, "name");
        if (nationData.createNation(string) != null) {
            return 1;
        }
        commandContext.getSource().sendFailure(Component.literal("Nation " + string + " already exists!"));
        return 0;
    }

    private static int removeNation(CommandContext<CommandSourceStack> commandContext) {
        NationData nationData = getOverworldInstance(commandContext);
        String string = StringArgumentType.getString(commandContext, "name");
        if (nationData.removeNation(string)) {
            commandContext.getSource().sendSuccess(() -> Component.literal("Removed " + string + " Nation"), true);
            return 1;
        }
        commandContext.getSource().sendFailure(Component.literal("Nation " + string + " doesn't exist!"));
        return 0;
    }

    private static NationData getOverworldInstance(CommandContext<CommandSourceStack> commandContext) {
        MinecraftServer server = commandContext.getSource().getServer();
        return NationData.getOrCreateDefaultNationsInstance(server);
    }

    private static final SuggestionProvider<CommandSourceStack> ALL_NATIONS = (commandContext, suggestionsBuilder) -> {
        List<String> collection = Nations2.getOverworldInstance(commandContext).getNations().stream().map(Nation::getName).toList();
        return SharedSuggestionProvider.suggest(collection, suggestionsBuilder);
    };

    private static final SuggestionProvider<CommandSourceStack> LEADER_OR_OP = (commandContext, suggestionsBuilder) -> {
        List<Nation> nations = new ArrayList<>(Nations2.getOverworldInstance(commandContext).getNations());
        CommandSourceStack commandSourceStack = commandContext.getSource();
        if (!commandSourceStack.hasPermission(Commands.LEVEL_GAMEMASTERS)) {
            if (commandSourceStack.isPlayer()) {
                nations.removeIf(nation -> !nation.isOwner(commandSourceStack.getPlayer()));
            } else {
                nations.clear();
            }
        }
        return SharedSuggestionProvider.suggest(nations.stream().map(Nation::getName), suggestionsBuilder);
    };

    private static int renameNation(CommandContext<CommandSourceStack> commandContext) {
        CommandSourceStack commandSourceStack = commandContext.getSource();
        NationData nationData = getOverworldInstance(commandContext);

        String nationString = StringArgumentType.getString(commandContext,"nation");
        String name = StringArgumentType.getString(commandContext,"name");
        Nation nation = nationData.getNationByName(nationString);
        if (nation == null) {
            commandSourceStack.sendFailure(Component.literal("Nation with name "+nationString +" doesn't exist"));
            return 0;
        }

        if (!commandSourceStack.hasPermission(Commands.LEVEL_GAMEMASTERS)) {
            ServerPlayer player = commandSourceStack.getPlayer();
            if (player == null) {
                commandSourceStack.sendFailure(Component.literal("Insufficient Permission to edit " + nationString +" name"));
                return 0;
            } else {
                if (!nation.isOwner(player)) {
                    commandSourceStack.sendFailure(Component.literal("Only team leaders or OPs can edit nation names"));
                    return 0;
                }
            }
        }

        String oldName = nation.getName();
        nationData.renameNation(nation,name);
        commandSourceStack.sendSuccess(() -> Component.literal("Renamed nation "+oldName +" to "+name),true);
        return 1;
    }

    private static int openGui(CommandContext<CommandSourceStack> objectCommandContext) {
        try {
            ServerPlayer player = objectCommandContext.getSource().getPlayerOrException();

            if (player.serverLevel().dimension() != Level.OVERWORLD) {
                player.sendSystemMessage(ModComponents.NO_NATIONS_OUTSIDE_OF_OVERWORLD);
                return 0;
            }

            NationData nationData = getOverworldInstance(objectCommandContext);
            Nation existingNation = nationData.getNationOf(player);

            Nation invitedTo = nationData.getInviteForPlayer(player);

            if (invitedTo != null) {
                SimpleGui inviteGui = new SimpleGui(MenuType.HOPPER, player, false);
                inviteGui.setTitle(Component.literal("Accept invite to " + invitedTo.getName() + " ?"));
                inviteGui.setSlot(0, new GuiElementBuilder()
                        .setItem(YES)
                        .setName(ModComponents.YES)
                        .setCallback((index, clickType, actionType) -> {
                            nationData.joinNation(invitedTo.getName(), List.of(player));
                            nationData.removeInvite(player);
                            player.sendSystemMessage(Component.literal("You are now part of " + invitedTo.getName() + " nation"), false);
                            inviteGui.close();
                        })
                );
                inviteGui.setSlot(4, new GuiElementBuilder()
                        .setItem(NO)
                        .setName(ModComponents.NO)
                        .setCallback((index, clickType, actionType) -> {
                            nationData.removeInvite(player);
                            inviteGui.close();
                        })
                );
                inviteGui.open();
                return 1;
            }

            if (existingNation == null) {
                SimpleGui gui = new SimpleGui(MenuType.HOPPER, player, false);
                gui.setTitle(ModComponents.CREATE_NATION);
                gui.setSlot(0, new GuiElementBuilder()
                        .setItem(YES)
                        .setName(ModComponents.YES)
                        .setCallback((index, clickType, actionType) -> {
                            ServerPlayer serverPlayer = gui.getPlayer();
                            String name = serverPlayer.getGameProfile().getName();
                            nationData.createNation(name);
                            nationData.setOwner(name, serverPlayer);
                            serverPlayer.sendSystemMessage(Component.literal("Created Nation " + name));
                            gui.close();
                        })
                );
                gui.setSlot(4, new GuiElementBuilder()
                        .setItem(NO)
                        .setName(ModComponents.NO)
                        .setCallback((index, clickType, actionType) -> gui.close())
                );
                gui.open();
            } else {
                if (existingNation.isOwner(player)) {
                    openTeamLeaderGui(nationData, existingNation, player);
                } else {
                    if (existingNation.isOfficer(player)) {
                        openTeamOfficerMenu(nationData, existingNation, player);
                    } else {
                        openTeamMemberMenu(nationData, existingNation, player);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 1;
    }

    private static void openTeamOfficerMenu(NationData nationData, Nation existingNation, ServerPlayer player) {
        SimpleGui teamOfficerMenu = new SimpleGui(MenuType.HOPPER, player, false);
        teamOfficerMenu.setTitle(Component.literal("Nation Officer Menu"));
        teamOfficerMenu.setSlot(0, ServerButtons.managePlayersButton(player, nationData, existingNation));
        teamOfficerMenu.setSlot(1, ServerButtons.topNationsButton(player, nationData));
        teamOfficerMenu.setSlot(2, ServerButtons.onlinePlayersButton(player, nationData));
        teamOfficerMenu.setSlot(4, ServerButtons.leaveTeamButton(player, nationData, existingNation));
        teamOfficerMenu.open();
    }

    private static void openTeamMemberMenu(NationData nationData, Nation existingNation, ServerPlayer player) {
        SimpleGui teamMemberMenu = new SimpleGui(MenuType.HOPPER, player, false);
        teamMemberMenu.setTitle(Component.literal("Nation Member Menu"));
        teamMemberMenu.setSlot(0, ServerButtons.topNationsButton(player, nationData));
        teamMemberMenu.setSlot(1, ServerButtons.onlinePlayersButton(player, nationData));
        teamMemberMenu.setSlot(2, ServerButtons.leaveTeamButton(player, nationData, existingNation));
        teamMemberMenu.open();
    }

    private static void openTeamLeaderGui(NationData nationData, Nation existingNation, ServerPlayer player) {
        SimpleGui teamLeaderMenu = new SimpleGui(MenuType.GENERIC_9x1, player, false);
        teamLeaderMenu.setTitle(Component.literal("Nation Leader Menu"));
        teamLeaderMenu.setSlot(0, new GuiElementBuilder()
                .setItem(Items.BARRIER)
                .setName(Component.literal("Disband Nation"))
                .setCallback((index, clickType, actionType) -> {
                    SimpleGui confirmGui = new SimpleGui(MenuType.HOPPER, player, false);
                    confirmGui.setTitle(Component.literal("Disband Nation?"));
                    confirmGui.setSlot(0, new GuiElementBuilder()
                            .setItem(YES)
                            .setName(ModComponents.YES)
                            .setCallback((index1, clickType1, actionType1) -> {
                                ServerPlayer serverPlayer = confirmGui.getPlayer();
                                nationData.removeNation(existingNation.getName());
                                serverPlayer.sendSystemMessage(Component.literal("Disbanded Nation " + existingNation.getName()));
                                confirmGui.close();
                            })
                    );
                    confirmGui.setSlot(4, new GuiElementBuilder()
                            .setItem(NO)
                            .setName(ModComponents.NO)
                            .setCallback((index1, clickType1, actionType1) -> confirmGui.close())
                    );
                    confirmGui.open();
                }));

        teamLeaderMenu.setSlot(1, ServerButtons.managePlayersButton(player, nationData, existingNation));
        teamLeaderMenu.setSlot(2,ServerButtons.claimChunksButton1(player, nationData, existingNation));
        teamLeaderMenu.setSlot(3,ServerButtons.unClaimChunksButton1(player, nationData, existingNation));

        teamLeaderMenu.setSlot(4, new GuiElementBuilder()
                .setItem(Items.SHIELD)
                .setName(ModComponents.NATION_POLITICS)
                .setCallback((index, type, action, gui) -> {

                    Nation allianceInvite = nationData.getAllianceInvite(existingNation);

                    if (allianceInvite != null) {
                        SimpleGui inviteGui = new SimpleGui(MenuType.HOPPER, player, false);
                        inviteGui.setTitle(Component.literal("Accept alliance invite to " + allianceInvite.getName() + " ?"));
                        inviteGui.setSlot(0, new GuiElementBuilder()
                                .setItem(YES)
                                .setName(ModComponents.YES)
                                .setCallback((index1, clickType, actionType) -> {
                                    nationData.createAllianceBetween(player.server, allianceInvite, existingNation);
                                    nationData.removeAllyInvite(allianceInvite, existingNation);
                                    player.sendSystemMessage(Component.literal("You are now allied with " + allianceInvite.getName() + " nation"), false);
                                    inviteGui.close();
                                })
                        );
                        inviteGui.setSlot(4, new GuiElementBuilder()
                                .setItem(NO)
                                .setName(ModComponents.NO)
                                .setCallback((index1, clickType, actionType) -> {
                                    nationData.removeAllyInvite(allianceInvite, existingNation);
                                    inviteGui.close();
                                })
                        );
                        inviteGui.open();
                    } else {

                        SimpleGui politicsGui = new SimpleGui(MenuType.HOPPER, player, false);
                        politicsGui.setTitle(ModComponents.NATION_POLITICS);
                        politicsGui.setSlot(0, new GuiElementBuilder()
                                .setItem(Items.FEATHER)
                                .setName(ModComponents.MAKE_ALLIANCE)
                                .setCallback((index1, type1, action1, gui1) -> {
                                    SimpleGui allianceGui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
                                    allianceGui.setTitle(ModComponents.MAKE_ALLIANCE);
                                    List<Nation> nonAlliedLeaders = nationData.getNonAllianceNations(existingNation);
                                    for (int i = 0; i < nonAlliedLeaders.size(); i++) {
                                        Nation nation = nonAlliedLeaders.get(i);
                                        allianceGui.setSlot(i, new GuiElementBuilder(Items.PLAYER_HEAD)
                                                .setSkullOwner(nation.getOwner(), player.server)
                                                .setName(Component.literal(nation.getOwner().getName()))

                                                .setCallback((index2, type2, action2, gui2) -> {
                                                    nationData.sendAllyInvites(existingNation, nation,player.server);
                                                    gui2.close();
                                                })
                                        );
                                    }
                                    allianceGui.open();
                                })

                        );

                        politicsGui.setSlot(1, new GuiElementBuilder()
                                .setItem(Items.NETHERITE_SWORD)
                                .hideFlags()
                                .setName(ModComponents.MAKE_ENEMY)
                                .setCallback((index1, type1, action1, gui1) -> {
                                    SimpleGui enemyGui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
                                    enemyGui.setTitle(ModComponents.MAKE_ENEMY);
                                    List<Nation> nonAlliedLeaders = nationData.getNonEnemyNations(existingNation);
                                    for (int i = 0; i < nonAlliedLeaders.size(); i++) {
                                        Nation nation = nonAlliedLeaders.get(i);
                                        enemyGui.setSlot(i, new GuiElementBuilder(Items.PLAYER_HEAD)
                                                .setSkullOwner(nation.getOwner(), player.server)
                                                .setName(Component.literal(nation.getOwner().getName()))
                                                .setCallback((index2, type2, action2, gui2) -> {
                                                    nationData.makeEnemy(player.server, existingNation, nation);
                                                    gui2.close();
                                                })
                                        );
                                    }
                                    enemyGui.open();
                                })

                        );

                        politicsGui.setSlot(2, new GuiElementBuilder()
                                .setItem(Items.ENDER_EYE)
                                .setName(ModComponents.MAKE_NEUTRAL)
                                .setCallback((index1, type1, action1, gui1) -> {

                                    SimpleGui enemyGui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
                                    enemyGui.setTitle(ModComponents.MAKE_NEUTRAL);
                                    List<Nation> nonAlliedLeaders = nationData.getNonNeutralNations(existingNation);
                                    for (int i = 0; i < nonAlliedLeaders.size(); i++) {
                                        Nation nation = nonAlliedLeaders.get(i);
                                        boolean isFriendly = nation.isAlly(existingNation);
                                        enemyGui.setSlot(i, new GuiElementBuilder(Items.PLAYER_HEAD)
                                                .setSkullOwner(nation.getOwner(), player.server)
                                                .setName(Component.literal(nation.getOwner().getName() + " - " + (isFriendly ? "Allied" : "Enemy")))
                                                .setCallback((index2, type2, action2, gui2) -> {
                                                    nationData.makeNeutral(player.server, existingNation, nation);
                                                    gui2.close();
                                                })
                                        );
                                    }
                                    enemyGui.open();

                                })

                        );

                        politicsGui.setSlot(3, new GuiElementBuilder()
                                .setItem(Items.BOOK)
                                .setName(ModComponents.NATION_STATUS)
                                .setCallback((index1, type1, action1, gui1) -> {
                                    SimpleGui statusGui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
                                    statusGui.setTitle(ModComponents.NATION_STATUS);
                                    List<Nation> nonAlliedLeaders = nationData.getNonNeutralNations(existingNation);
                                    for (int i = 0; i < nonAlliedLeaders.size(); i++) {
                                        Nation nation = nonAlliedLeaders.get(i);
                                        boolean isFriendly = nation.isAlly(existingNation);
                                        statusGui.setSlot(i, new GuiElementBuilder(Items.PLAYER_HEAD)
                                                .setSkullOwner(nation.getOwner(), player.server)
                                                .setName(Component.literal(nation.getOwner().getName() + " - " + (isFriendly ? "Allied" : "Enemy")))
                                                .setCallback((index2, type2, action2, gui2) -> {
                                                })
                                        );
                                    }
                                    statusGui.open();
                                })
                        );
                        politicsGui.open();
                    }
                })
        );

        teamLeaderMenu.setSlot(5, ServerButtons.topNationsButton(player, nationData));
        teamLeaderMenu.setSlot(6, ServerButtons.onlinePlayersButton(player, nationData));
        teamLeaderMenu.setSlot(7, new GuiElementBuilder(Items.RED_BANNER)
                .setName(Component.literal("Declare Siege"))
                .setCallback((index, type, action, gui) -> {
                    SimpleGui raidGui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
                    raidGui.setTitle(ModComponents.SELECT_ENEMY_NATION);
                    Set<String> enemies = existingNation.getEnemies();
                    int i = 0;
                    for (String s : enemies) {
                        Nation enemyNation = nationData.getNationByName(s);

                        if (enemyNation != null) {

                            List<ServerPlayer> online = enemyNation.getOnlineMembers(player.server);
                            int total = enemyNation.getMembers().size();

                            double percentage = online.size() / (double) total;

                            String name = enemyNation.getName() + " | " + online.size() + "/" + total + " online";

                            raidGui.setSlot(i++, new GuiElementBuilder(Items.PLAYER_HEAD)
                                    .setSkullOwner(enemyNation.getOwner(), player.server)
                                    .setName(Component.literal(name))
                                    .setCallback((index1, type1, action1, gui1) -> {

                                        if (percentage >= .5) {
                                            SimpleGui siege2Gui = new SimpleGui(MenuType.GENERIC_9x6, player, false);
                                            siege2Gui.setTitle(Component.literal("Select Enemy Claim"));
                                            ChunkPos chunkPos = new ChunkPos(player.blockPosition());
                                            int i1 = 0;
                                            for (int z = -2; z < 4; z++) {
                                                for (int x = -4; x < 5; x++) {
                                                    ChunkPos offset = new ChunkPos(chunkPos.x + x, chunkPos.z + z);

                                                    Nation claimed = nationData.getNationAtChunk(offset);
                                                    Item icon = Items.LIGHT_GRAY_STAINED_GLASS_PANE;

                                                    if (claimed != null) {
                                                        if (claimed == existingNation)
                                                            icon = Items.GREEN_STAINED_GLASS_PANE;
                                                        else {
                                                            icon = Items.RED_STAINED_GLASS_PANE;
                                                        }
                                                    }

                                                    String nationName = claimed != null ? claimed.getName() : "Wilderness";
                                                    boolean glow = offset.equals(chunkPos);

                                                    GuiElementBuilder elementBuilder = new GuiElementBuilder()
                                                            .setItem(icon)
                                                            .setName(Component.literal(nationName + " (" + offset.x + "," + offset.z + ")"))
                                                            .setCallback((index2, type2, action2, gui2) -> {
                                                                if (claimed != enemyNation) {
                                                                    player.sendSystemMessage(Component.literal("Incorrect nation claim " + nationName
                                                                            + ", expected " + enemyNation.getName()));
                                                                } else {
                                                                    if (!TeamHandler.membersNearby(player.server, offset, existingNation)) {
                                                                        nationData.startSiege(existingNation, enemyNation, player.serverLevel(), offset);
                                                                        gui2.close();
                                                                    } else {
                                                                        player.sendSystemMessage(ModComponents.TOO_CLOSE_TO_START_SIEGE);
                                                                    }
                                                                }
                                                            });

                                                    if (glow) {
                                                        elementBuilder.glow();
                                                    }

                                                    siege2Gui.setSlot(i1, elementBuilder);
                                                    i1++;
                                                }
                                            }
                                            siege2Gui.open();
                                        } else {
                                            player.sendSystemMessage(Component.literal("Not enough members online to raid "+enemyNation.getName()));
                                        }
                                    })
                            );
                        }
                    }
                    raidGui.open();
                })
        );

        teamLeaderMenu.open();
    }

    static final UnaryOperator<Style> NO_ITALIC = style -> style.withItalic(false);

    static List<ServerPlayer> getUninvitedPlayers(ServerPlayer leader, NationData nationData) {
        List<ServerPlayer> allPlayers = new ArrayList<>(leader.server.getPlayerList().getPlayers());
        allPlayers.removeIf(player -> nationData.getNationOf(player) != null);
        return allPlayers;
    }

    static List<GameProfile> getAllTeamMembersExceptLeader(ServerPlayer leader, Nation nation) {
        Set<GameProfile> members = nation.getMembers();
        List<GameProfile> list = new ArrayList<>(members);
        list.remove(leader.getGameProfile());
        return list;
    }


    public static boolean onMovePacket(ServerGamePacketListenerImpl packetHandler, ServerPlayer player, ServerboundMovePlayerPacket packet) {
        NationData nationData = NationData.getNationInstance(player.serverLevel());
        if (nationData != null) {
            Siege siege = nationData.getActiveSiege();
            if (siege != null) {
                if (siege.isAttacking(player, nationData) && siege.shouldBlockAttackers() && TeamHandler.isPlayerNearClaim(player, siege.getClaimPos())) {
                    if (packet.hasPosition()) {
                        player.sendSystemMessage(ModComponents.CANT_MOVE_INTO_CLAIM_STAGE_1);
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