package me.ujun.pvpWorld.duel;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.block.BlockTypes;
import it.unimi.dsi.fastutil.chars.Char2ShortRBTreeMap;
import me.ujun.pvpWorld.arena.*;
import me.ujun.pvpWorld.config.ConfigHandler;
import me.ujun.pvpWorld.kit.Kit;
import me.ujun.pvpWorld.kit.KitManager;
import me.ujun.pvpWorld.saving.ArenasFile;
import me.ujun.pvpWorld.util.ResetUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.ChatPaginator;
import org.jetbrains.annotations.Nullable;

import javax.swing.plaf.SpinnerUI;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.text.DecimalFormat;

public class DuelManager {
    private final ArenaManager arenaManager;
    private final VoidManager voidWorld;
    private final ArenaAllocator allocator;
    private final ArenasFile arenasFile;
    private final KitManager kitManager;
    private final JavaPlugin plugin;
    private final DuelUtil dualUtil;


    public final Set<UUID> spectators = new HashSet<>();
    public final Map<UUID, Instance> byPlayer = new HashMap<>();
    public final Set<UUID> offlinePlayers = new HashSet<>();
    public final Set<UUID> offlineDuelInvited = new HashSet<>();
    public final Set<UUID> leavingPlayer = new HashSet<>();

    public DuelManager(ArenaManager arenaManager, ArenasFile arenasFile, VoidManager voidWorld, ArenaAllocator allocator, KitManager kitManager, DuelUtil dualUtil, JavaPlugin plugin) {
        this.arenaManager = arenaManager;
        this.arenasFile = arenasFile;
        this.voidWorld = voidWorld;
        this.allocator = allocator;
        this.kitManager = kitManager;
        this.dualUtil = dualUtil;
        this.plugin = plugin;
    }


    public Instance getInstanceOf(Player p) {
        return byPlayer.get(p.getUniqueId());
    }

    public boolean startDuel(List<Player> teamA, List<Player> teamB, Kit kit, int roundSetting, boolean party) {

        ArenaMeta meta = pickRandomArenaByType(kit.getType());
        if (meta == null) {
            Bukkit.getLogger().warning("사용 가능한 아레나가 없습니다.");
            return false;
        }


        Component msg1 = Component.text("듀얼이 시작됩니다", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text("\n\n상대: ", NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(dualUtil.joinAnyNames(teamB, "§a§l"), NamedTextColor.GREEN,  TextDecoration.BOLD))
                .append(Component.text("\n키트: ", NamedTextColor.WHITE,  TextDecoration.BOLD))
                .append(Component.text(kit.getDisplayName(), NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text("\n라운드: ", NamedTextColor.WHITE, TextDecoration.BOLD)).
                append(Component.text(String.valueOf(roundSetting), NamedTextColor.YELLOW, TextDecoration.BOLD));

        Component msg2 = Component.text("듀얼이 시작됩니다", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text("\n\n상대: ", NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(dualUtil.joinAnyNames(teamA, "§a§l"), NamedTextColor.GREEN,  TextDecoration.BOLD))
                .append(Component.text("\n키트: ", NamedTextColor.WHITE,  TextDecoration.BOLD))
                .append(Component.text(kit.getDisplayName(), NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text("\n라운드: ", NamedTextColor.WHITE, TextDecoration.BOLD)).
                append(Component.text(String.valueOf(roundSetting), NamedTextColor.YELLOW, TextDecoration.BOLD));

        dualUtil.sendMessageToPlayers(msg1, teamA);
        dualUtil.sendMessageToPlayers(msg2, teamB);


        return startWithArena(teamA, teamB, meta, kit, "duel", roundSetting, party);
    }

    public boolean startFFA(List<Player> players, Kit kit, int roundSetting) {
        ArenaMeta meta = pickRandomArenaByType(kit.getType());
        if (meta == null) {
            Bukkit.getLogger().warning("사용 가능한 아레나가 없습니다.");
            return false;
        }

        for (Player p : players) {
            List<Player> exceptSelf = new ArrayList<>(players);
            exceptSelf.remove(p);

            Component msg = Component.text("ffa가 작됩니다", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .append(Component.text("\n\n상대: ", NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text(dualUtil.joinAnyNames(exceptSelf, "§a§l"), NamedTextColor.GREEN, TextDecoration.BOLD))
                    .append(Component.text("\n키트: ", NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text(kit.getDisplayName(), NamedTextColor.AQUA, TextDecoration.BOLD))
                    .append(Component.text("\n라운드: ", NamedTextColor.WHITE, TextDecoration.BOLD)).
                    append(Component.text(String.valueOf(roundSetting), NamedTextColor.YELLOW, TextDecoration.BOLD));

            p.sendMessage(msg);
        }
        return startWithArena(players, List.of(), meta, kit, "ffa", roundSetting, false);
    }


    private boolean startWithArena(List<Player> teamA, List<Player> teamB, ArenaMeta meta, Kit kit, String type, int roundSetting, boolean party) {
        World w = voidWorld.ensure();
        if (w == null) {
            Bukkit.getLogger().warning("전용 월드 생성 실패");
            return false;
        }
        if (meta.spawn1() == null || meta.spawn2() == null) {
            Bukkit.getLogger().info("§c아레나에 스폰 포인트가 누락되었습니다.");
            return false;
        }
        try {
            var alloc = allocator.acquire(w);
            Location origin = alloc.origin;


            allocator.warmup(w, alloc, meta.sizeX(), meta.sizeZ());

            var cb = WeHelper.readSchem(arenasFile.schemFile(meta.schem()));
            WeHelper.paste(cb, w, origin.getBlock().getLocation(), true);

            Instance inst = new Instance(kit, origin, meta.sizeX(), meta.sizeY(), meta.sizeZ(), type, roundSetting, meta, party);
            inst.slotIndex = alloc.slotIndex;
            removeArenaEntities(w, inst);


            if (type.equals("duel")) {

                Location baseA = origin.clone().add(meta.spawn1().dx(), meta.spawn1().dy(), meta.spawn1().dz());
                baseA.setYaw(meta.spawn1().yaw());
                baseA.setPitch(meta.spawn1().pitch());
                Location baseB = origin.clone().add(meta.spawn2().dx(), meta.spawn2().dy(), meta.spawn2().dz());
                baseB.setYaw(meta.spawn2().yaw());
                baseB.setPitch(meta.spawn2().pitch());

                for (Player p : teamA) {
                    kitManager.applyTo(p, kit, true, true);
                    inst.teamA.add(p.getUniqueId());
                    byPlayer.put(p.getUniqueId(), inst);
                    p.setGameMode(GameMode.SURVIVAL);
                    ResetUtil.resetPlayerState(p);
                    setSpectator(p, false, inst);
                    p.getScoreboardTags().remove(ConfigHandler.ffaTag);

                    if (inst.kit.getType().equals("sumo") || inst.kit.getType().equals("spleef")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 12000, 255, false, false, false));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 12000, 255, false, false, false));
                    }
                }

                for (Player p : teamB) {
                    kitManager.applyTo(p, kit, true, true);
                    inst.teamB.add(p.getUniqueId());
                    byPlayer.put(p.getUniqueId(), inst);
                    p.setGameMode(GameMode.SURVIVAL);
                    ResetUtil.resetPlayerState(p);
                    setSpectator(p, false, inst);

                    if (inst.kit.getType().equals("sumo") || inst.kit.getType().equals("spleef")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 12000, 255, false, false, false));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 12000, 255, false, false, false));
                    }
                }

                if (party) {
                    inst.partyScoreMap.put("teamA", 0);
                    inst.partyScoreMap.put("teamB", 0);
                } else {
                    for (UUID id : dualUtil.getInstPlayers(inst)) {
                        inst.scoreMap.put(id, 0);
                    }
                }

                tpGroup(inst.teamA, baseA, +1);
                tpGroup(inst.teamB, baseB, -1);
            } else if (type.equals("ffa")) {

                List<Player> all = new ArrayList<>(teamA);
                all.addAll(teamB); // 보통 teamB는 비어있게 넘기지만 안전하게 합침


                Location baseC = origin.clone().add(meta.center().dx(), meta.center().dy(), meta.center().dz());
                baseC.add(0.5, 0, 0.5);

                for (Player p : all) {
                    kitManager.applyTo(p, kit, true, true);
                    inst.teamA.add(p.getUniqueId()); // FFA에선 teamA에 전원 수납
                    byPlayer.put(p.getUniqueId(), inst);
                    p.setGameMode(GameMode.SURVIVAL);
                    ResetUtil.resetPlayerState(p);
                    p.teleport(baseC);
                    setSpectator(p, false, inst);

                    if (inst.kit.getType().equals("sumo") || inst.kit.getType().equals("spleef")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 12000, 255, false, false, false));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 12000, 255, false, false, false));
                    }
                }

                for (UUID id : dualUtil.getInstPlayers(inst)) {
                    inst.scoreMap.put(id, 0);
                }
            } else {
                Bukkit.getLogger().warning("알 수 없는 타입: " + type);
                return false;
            }


            startCountdown(inst);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            Bukkit.getLogger().warning("듀얼 시작 실패: " + e.getMessage());
            return false;
        }
    }

    private void startRound(Instance inst) {

        try {
            World w = voidWorld.ensure();

            Location origin = inst.origin;

            var cb = WeHelper.readSchem(arenasFile.schemFile(inst.meta.schem()));
            WeHelper.paste(cb, w, origin, false);
            Bukkit.getLogger().info(inst.meta.display() + " " + origin);
            removeArenaEntities(w, inst);
            inst.isShuttingDown = false;

            if (inst.type.equals("duel")) {
                if (inst.teamA.isEmpty() || inst.teamB.isEmpty()) {
                    inst.isShuttingDown = true;
                    endInternal(inst);
                    return;
                }

                Location baseA = origin.clone().add(inst.meta.spawn1().dx(), inst.meta.spawn1().dy(), inst.meta.spawn1().dz());
                baseA.setYaw(inst.meta.spawn1().yaw());
                baseA.setPitch(inst.meta.spawn1().pitch());
                Location baseB = origin.clone().add(inst.meta.spawn2().dx(), inst.meta.spawn2().dy(), inst.meta.spawn2().dz());
                baseB.setYaw(inst.meta.spawn2().yaw());
                baseB.setPitch(inst.meta.spawn2().pitch());

                for (Player p : dualUtil.getInstOnlinePlayers(inst)) {
                    kitManager.applyTo(p, inst.kit, true, true);
                    p.setGameMode(GameMode.SURVIVAL);
                    setSpectator(p, false, inst);
                    ResetUtil.resetPlayerState(p);
                    if (inst.kit.getType().equals("sumo") || inst.kit.getType().equals("spleef")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 12000, 255, false, false, false));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 12000, 255, false, false, false));
                    }
                }


                for (UUID id : dualUtil.getInstPlayers(inst)) {
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(id);
                    inst.eliminated.remove(id);

                    if (!offlinePlayer.isOnline()) {
                        inst.teamA.remove(id);
                        inst.teamB.remove(id);
                    }
                }

                tpGroup(inst.teamA, baseA, 1);
                tpGroup(inst.teamB, baseB, 1);
            } else if (inst.type.equals("ffa")) {
                if (inst.teamA.size() <= 1) {
                    inst.isShuttingDown = true;
                    endInternal(inst);
                    return;
                }

                Location baseC = origin.clone().add(inst.meta.center().dx(), inst.meta.center().dy(), inst.meta.center().dz());
                baseC.add(0.5, 0, 0.5);

                for (Player p : idsToPlayers(inst.teamA)) {
                    kitManager.applyTo(p, inst.kit, true, true);
                    p.setGameMode(GameMode.SURVIVAL);
                    ResetUtil.resetPlayerState(p);
                    setSpectator(p, false, inst);
                    p.teleport(baseC);

                    if (inst.kit.getType().equals("sumo") || inst.kit.getType().equals("spleef")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 12000, 255, false, false, false));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 12000, 255, false, false, false));
                    }

                }

                for (UUID id : dualUtil.getInstPlayers(inst)) {
                    inst.eliminated.remove(id);
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(id);

                    if (!offlinePlayer.isOnline()) {
                        inst.teamA.remove(id);
                    }
                }

            } else {
                return;
            }


            startCountdown(inst);
        } catch (Exception e) {
            e.printStackTrace();
            Bukkit.getLogger().warning("라운드 시작 실패: " + e.getMessage());
        }
    }

    private List<Player> idsToPlayers(Set<UUID> ids) {
         return ids.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .filter(Player::isOnline)
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

    }

    private void tpGroup(Set<UUID> teamIds, Location base, double spacing) {
        if (teamIds == null || teamIds.isEmpty() || base == null) return;
        World w = base.getWorld();
        if (w == null) return;

        List<Player> team = idsToPlayers(teamIds);

        int x = 0;
        int z = 0;
        int right = (int) base.getYaw();
        right += 90;
        right %= 360;

        if (right == 0 || right == 180) {
            z = 1;
        } else if (right == 90 || right == 270) {
            x = 1;
        }
        // 플레이어를 순서대로 배치
        for (int i = 0; i < team.size(); i++) {
            Player p = team.get(i);
            if (p == null || !p.isOnline()) continue;

            Location to = base.clone().add(0.5, 0, 0.5);
            if (i > 0) {
                int k = (i + 1) / 2;               // 1,1,2,2,3,3 …
                int sign = (i % 2 == 1) ? +1 : -1; // 2번째(+), 3번째(-), 4번째(+), …
                double dist = spacing * k;
                to.add(x * dist, 0.0, z * dist);
                if (sign < 0) { // 왼쪽이면 반대 방향
                    to.subtract(x * 2 * dist, 0.0, z * 2 * dist);
                }
            }

            // 청크 로드 후 텔레포트 (블록 중앙으로 맞추고 싶으면 +0.5 적용)
            w.getChunkAt(to).load();
            to.setYaw(base.getYaw());
            to.setPitch(base.getPitch());
            p.teleport(to);
        }
    }


    private void startCountdown(Instance inst) {
        final int[] left = {3};
        inst.countdown = true;

        for (Player p : dualUtil.getInstWatchersAndPlayers(inst)) {
            inst.leftTime = inst.kit.getDuelTime();
            dualUtil.setSidebar(p, inst);
        }

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (inst.isShuttingDown) {
                    inst.countdown = false;
                    this.cancel();
                    return;
                }

                if (left[0] > 0) {
                    String title =
                            (left[0] == 3) ? "§e3" :
                                    (left[0] == 2) ? "§62" :
                                            "§c1";
                    dualUtil.sendTitleToPlayers(inst, title, 0, 25, 0);
                    dualUtil.playSoundToPlayers(inst, Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    left[0]--;
                    return;
                }
                // START!
                dualUtil.sendTitleToPlayers(inst, "§bSTART!", 0, 10, 0); // 아쿠아
                dualUtil.playSoundToPlayers(inst, Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 1.0f);
                inst.countdown = false;
                startTimeout(inst);
                this.cancel();
            }
        }.runTaskTimer(plugin, 0L, 20L);

        inst.countdownTaskId = task.getTaskId();
    }

    private void startTimeout(Instance inst) {
        inst.leftTime = inst.kit.getDuelTime();

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (inst.leftTime >= 0) {
                    for (Player p : dualUtil.getInstWatchersAndPlayers(inst)) {
                        dualUtil.setSidebar(p, inst);
                    }

                    inst.leftTime--;
                    return;
                }
            checkRoundEnd(inst, true);
            this.cancel();
            }
        }.runTaskTimer(plugin, 0L, 20L);

        inst.timeoutTaskId = task.getTaskId();
    }




    public void endInternal(Instance inst) {
        if (inst.ended) return;
        inst.ended = true;

        // 타이머 정리
        if (inst.countdownTaskId != -1) {
            Bukkit.getScheduler().cancelTask(inst.countdownTaskId);
            inst.countdownTaskId = -1;
        }
        if (inst.timeoutTaskId != -1) {
            Bukkit.getScheduler().cancelTask(inst.timeoutTaskId);
            inst.timeoutTaskId = -1;
        }


        // 제거
        for (UUID id : dualUtil.getInstPlayers(inst)) {
            byPlayer.remove(id);
            offlinePlayers.remove(id);
        }

        //관전자 제거
        for (UUID id : inst.watchers) {
            byPlayer.remove(id);
        }


        // 관전자 해제 + 로비 이동
        for (Player p : dualUtil.getInstWatchersAndPlayers(inst)) {
            setSpectator(p, false, inst);
            ResetUtil.joinLobby(p);
            dualUtil.clearSidebar(p);
        }

        // 아레나 공기화
        World w = inst.origin.getWorld();
        var weWorld = BukkitAdapter.adapt(w);
        var min = BlockVector3.at(
                inst.origin.getBlockX(), inst.origin.getBlockY(), inst.origin.getBlockZ());
        var max = min.add(inst.sx - 1, inst.sy - 1, inst.sz - 1);
        try (EditSession edit =
                     WorldEdit.getInstance().newEditSession(weWorld)) {
            var region = new CuboidRegion(weWorld, min, max);
            edit.setBlocks(region, BlockTypes.AIR.getDefaultState());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // ⬇ 슬롯 반납 (재사용 가능!)
        if (inst.slotIndex >= 0) {
            allocator.release(inst.slotIndex);
            inst.slotIndex = -1;
        }
    }


    public boolean isInDuel(Player p) {
        return byPlayer.containsKey(p.getUniqueId());
    }

    public boolean isCountdown(Player p) {
        Instance i = byPlayer.get(p.getUniqueId());
        return i != null && i.countdown;
    }

    private static boolean typeEquals(String a, String b) {
        return (a == null ? "default" : a).equalsIgnoreCase(b == null ? "default" : b);
    }


    public void setSpectator(Player p, boolean enable,Instance inst) {
        UUID id = p.getUniqueId();
        if (enable) {
            ResetUtil.resetPlayerState(p);
            spectators.add(id);
            p.setGameMode(GameMode.ADVENTURE);
            p.setAllowFlight(true);
            p.setFlying(true);
            p.setCollidable(false);
            p.setCanPickupItems(false);
            p.setInvulnerable(true);
            p.getInventory().clear();
            for (Player other : dualUtil.getInstOnlinePlayers(inst)) {
                if (!other.getUniqueId().equals(id)) other.hidePlayer(plugin, p);
            }
        } else {
            spectators.remove(id);
            p.setAllowFlight(false);
            p.setFlying(false);
            p.setCollidable(true);
            p.setCanPickupItems(true);
            p.setInvulnerable(false);
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.getUniqueId().equals(id)) other.showPlayer(plugin, p);
            }
        }
    }

    private ArenaMeta pickRandomArenaByType(String wantType) {
        var all = arenaManager.all();

        // 1) 원하는 타입 우선
        var list = all.stream()
                .filter(m -> typeEquals(m.type(), wantType))
                .collect(Collectors.toList());

        // 2) 없다면 default 시도
        if (list.isEmpty()) {
            list = all.stream()
                    .filter(m -> typeEquals(m.type(), "default"))
                    .collect(Collectors.toList());
        }


        if (list.isEmpty()) return null;
        int idx = ThreadLocalRandom.current().nextInt(list.size());
        return list.get(idx);
    }

    public static int[] top2AllowDup(Map<UUID, Integer> map) {
        int[] arr = map.values().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.reverseOrder()) // 내림차순
                .limit(2)
                .mapToInt(Integer::intValue)
                .toArray(); // 길이 0~2
        return arr; // arr.length==2면 arr[0]=1등, arr[1]=2등(동점이면 같은 값)
    }

    DecimalFormat df = new DecimalFormat("#.##");

    public void eliminate(Player dead, @Nullable Player killer) {
        Instance inst = byPlayer.get(dead.getUniqueId());
        if (inst == null) return;

        inst.eliminated.add(dead.getUniqueId());
        if (!offlinePlayers.contains(dead.getUniqueId())) {
            setSpectator(dead, true, inst);
        }

        String deathMessage;
        if (killer != null) {
            deathMessage = (ChatColor.RED + dead.getName() + ChatColor.RESET + "님이 듀얼에서 " + ChatColor.GREEN + killer.getName() + ChatColor.RESET + "님에게 살해당했습니다 "
                           + ChatColor.RED + "(" + df.format(killer.getHealth()) + ")");
        } else {
            deathMessage = (ChatColor.RED + dead.getName() + ChatColor.RESET + "님이 듀얼에서 " + "사망했습니다");
        }

        dualUtil.sendMessageToPlayers(deathMessage, dualUtil.getInstWatchersAndPlayers(inst));
        checkRoundEnd(inst, false);
    }

    private int aliveCount(Instance inst, Set<UUID> team) {
        int c = 0;
        for (UUID id : team) if (!inst.eliminated.contains(id)) c++;
        return c;
    }

    private void checkRoundEnd(Instance inst, boolean isTimeout) {

        int top[] = {0, 0};
//        Set<UUID> winnerPlayers = new HashSet<>();
        int aliveA = aliveCount(inst, inst.teamA);
        int aliveB = aliveCount(inst, inst.teamB);

        Bukkit.getLogger().info(String.valueOf(aliveA));

        int onlineA = 0;
        int onlineB = 0;

        for (UUID id : dualUtil.getInstPlayers(inst)) {
            if (offlinePlayers.contains(id)) {
                continue;
            }

            if (inst.teamA.contains(id)) {
                onlineA++;
            }
            if (inst.teamB.contains(id)) {
                onlineB++;
            }
        }


        if (inst.type.equals("duel")) {
            if (aliveA == 0 || aliveB == 0) {

                checkRoundVictory(inst);

                if (inst.party) {
                    int teamAVictoryCount = inst.partyScoreMap.get("teamA");
                    int teamBVictoryCount = inst.partyScoreMap.get("teamB");

                    top[0] = Math.max(teamAVictoryCount, teamBVictoryCount);
                    top[1] = Math.min(teamAVictoryCount, teamBVictoryCount);
                } else {
                    top = top2AllowDup(inst.scoreMap);
                }
            }
        } else if (inst.type.equals("ffa")) {
            if (aliveA <= 1) {
                checkRoundVictory(inst);
                top = top2AllowDup(inst.scoreMap);

                onlineA--;
                onlineB = -1;
            }
        } else {
            return;
        }


        if (!isTimeout) {
            if (inst.type.equals("duel")) {
                if (aliveA > 0 && aliveB > 0) return;
            } else if (inst.type.equals("ffa")) {
                if (aliveA > 1) return;
            }
        }

//        Bukkit.getLogger().info(aliveA + " | " + aliveB);


        if (isTimeout) {
            dualUtil.sendTitleToPlayers(inst, ChatColor.YELLOW + "무승부", 0, 20, 0);
            dualUtil.playSoundToPlayers(inst, Sound.BLOCK_NOTE_BLOCK_BASS, 1, 1);
        } else {
            inst.top[0] = top[0];
            inst.top[1] = top[1];

            for (Player p : dualUtil.getInstWatchersAndPlayers(inst)) {
                dualUtil.setSidebar(p, inst);
            }
//
//            String message = "§6§l라운드 종료!\n§f" + top[0] + " §7-§f " + top[1] + "\n§r";
//
//            String joined = idsToPlayers(winnerPlayers).stream()
//                    .map(p -> {
//                        String name = p.getName();
//                        String status;
//                        if (inst.eliminated.contains(p.getUniqueId())) {
//                            status = "§cdead";
//                        } else {
//                            double hp = Math.max(0.0, p.getHealth());
//                            status = String.format("%.2f❤", hp);
//                        }
//                        return name + "§7(§c" + status + "§7)§r";
//                    })
//                    .collect(Collectors.joining("§r / §r"));
//
//            message += joined + " §a§l승리";
//            dualUtil.sendMessageToPlayers(message, dualUtil.getInstWatchersAndPlayers(inst));
            dualUtil.sendTitleToPlayers(inst, "§f" + top[0] + " §7-§f " + top[1], 0, 40, 0);
        }

        if (inst.countdownTaskId != -1) {
            Bukkit.getScheduler().cancelTask(inst.countdownTaskId);
            inst.countdownTaskId = -1;
        }
        if (inst.timeoutTaskId != -1) {
            Bukkit.getScheduler().cancelTask(inst.timeoutTaskId);
            inst.timeoutTaskId = -1;
        }

        inst.isShuttingDown = true;
        if ((top[0] == inst.roundSetting) || (onlineA == 0 || onlineB == 0)) {
            checkVictory(inst);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                endInternal(inst);
            }, 100L);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                startRound(inst);
            }, 60L);
        }

        for (UUID id : dualUtil.getInstPlayers(inst)) {
            if (offlinePlayers.contains(id)) {
                offlinePlayers.remove(id);
                byPlayer.remove(id);

                inst.teamA.remove(id);
                inst.teamB.remove(id);
            }
        }
    }



    public void leaveDuel(Player p, Instance inst) {
        leavingPlayer.add(p.getUniqueId());
        int teamSize = 0;

        if (inst.watchers.contains(p.getUniqueId())) {
            setSpectator(p, false, inst);
            inst.watchers.remove(p.getUniqueId());
            byPlayer.remove(p.getUniqueId());
        } else {
            if (inst.type.equals("duel")) {
                if (inst.teamB.contains(p.getUniqueId())) {
                    teamSize = inst.teamA.size();
                } else {
                    teamSize = inst.teamB.size();
                }
            } else {
                teamSize = inst.teamA.size();
                teamSize--;
            }
        }

        if (teamSize > 1) {
            eliminate(p, p.getKiller());
            byPlayer.remove(p.getUniqueId());

            inst.teamA.remove(p.getUniqueId());
            inst.teamB.remove(p.getUniqueId());
        } else if (teamSize == 1) {

            offlinePlayers.add(p.getUniqueId());
            eliminate(p, p.getKiller());
            byPlayer.remove(p.getUniqueId());
        }

        if (p.getGameMode().equals(GameMode.ADVENTURE) || p.getGameMode().equals(GameMode.SURVIVAL)) {
            p.setInvulnerable(false);
            p.setAllowFlight(false);
        }
        ResetUtil.joinLobby(p);
        dualUtil.clearSidebar(p);
        leavingPlayer.remove(p.getUniqueId());

    }

    private void checkRoundVictory(Instance inst) {
        int aliveA = aliveCount(inst, inst.teamA);
        int aliveB = aliveCount(inst, inst.teamB);
//        Set<UUID> winnerPlayers = new HashSet<>();

            if (inst.type.equals("duel")) {
                if (aliveA == 0 && aliveB > 0) {
                    if (inst.party) {
                        inst.partyScoreMap.put("teamB", inst.partyScoreMap.get("teamB") +1 );
//                        winnerPlayers = inst.teamB;
                    } else {
                        for (UUID id : inst.teamB) {
                            inst.scoreMap.put(id, inst.scoreMap.get(id) + 1);
//                            winnerPlayers = inst.teamB;
                        }
                    }
                } else if (aliveB == 0 && aliveA > 0) {
                    if (inst.party) {
                        inst.partyScoreMap.put("teamA", inst.partyScoreMap.get("teamA") +1 );
//                        winnerPlayers = inst.teamA;
                    } else {
                        for (UUID id : inst.teamA) {
                            inst.scoreMap.put(id, inst.scoreMap.get(id) +1 );
//                            winnerPlayers = inst.teamA;
                        }
                    }
                } else if (aliveA == 0 && aliveB == 0) {
                    dualUtil.sendTitleToPlayers(inst,ChatColor.YELLOW + "무승부",0, 20, 0);
                }
            } else if (inst.type.equals("ffa")) {
                if (aliveA == 1) {
                    for (UUID id : inst.teamA) {
                        if (!inst.eliminated.contains(id)) {
                            inst.scoreMap.put(id, inst.scoreMap.get(id) + 1);
//                            winnerPlayers.add(id);
                            break;
                        }
                    }
                } else if (aliveA == 0) {
                    dualUtil.sendTitleToPlayers(inst,ChatColor.YELLOW + "무승부",0, 20, 0);
                }
            }

            dualUtil.playSoundToPlayers(inst, Sound.BLOCK_NOTE_BLOCK_HARP, 1.0f, 1.0f);
//            return winnerPlayers;

    }

    private void checkVictory(Instance inst) {
        UUID id = inst.scoreMap.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .filter(e -> !leavingPlayer.contains(e.getKey()))
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);


        if (inst.type.equals("duel")) {
            if (inst.party) {
                Set<UUID> victoryTeam = (inst.partyScoreMap.get("teamA") > inst.partyScoreMap.get("teamB")) ? inst.teamA : inst.teamB;

                if (victoryTeam.stream().anyMatch(leavingPlayer::contains)) {
                    victoryTeam = (inst.teamB.equals(victoryTeam) ? inst.teamA : inst.teamB);
                }

                if (inst.teamA.equals(victoryTeam)) {
                    dualUtil.sendEndTitle(inst, inst.teamA);
                } else if (inst.teamB.equals(victoryTeam)) {
                    dualUtil.sendEndTitle(inst, inst.teamB);
                }
            } else {
                if (inst.teamA.contains(id)) {
                    dualUtil.sendEndTitle(inst, inst.teamA);
                } else if (inst.teamB.contains(id)) {
                    dualUtil.sendEndTitle(inst, inst.teamB);
                }
            }
        } else if (inst.type.equals("ffa")) {
            inst.teamB.add(id);
            dualUtil.sendEndTitle(inst, inst.teamB);
        }
        dualUtil.playSoundToPlayers(inst, Sound.BLOCK_NOTE_BLOCK_HARP, 1.0f, 1.0f);
    }


    private void removeArenaEntities(World world, Instance inst) {
        int x = inst.origin.getBlockX();
        int y = inst.origin.getBlockY();
        int z = inst.origin.getBlockZ();

        int dx = inst.meta.sizeX();
        int dy = inst.meta.sizeY();
        int dz = inst.meta.sizeZ();

        double x2 = x + dx, y2 = y + dy, z2 = z + dz;
        double minX = Math.min(x, x2), minY = Math.min(y, y2), minZ = Math.min(z, z2);
        double maxX = Math.max(x, x2), maxY = Math.max(y, y2), maxZ = Math.max(z, z2);

        BoundingBox box = new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);

        for (Entity e : world.getNearbyEntities(box)) {
            if (e instanceof Player) continue; // 플레이어 제외 옵션
            e.remove();
        }

    }
}

