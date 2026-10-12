package org.infernalstudios.questlog.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.infernalstudios.questlog.Questlog;
import org.infernalstudios.questlog.network.packet.GroupStatePacket;
import org.infernalstudios.questlog.platform.Services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-owned, world-local adventure groups. All entry points run on the server thread. */
public final class AdventureGroups {
    public static AdventureGroups INSTANCE;
    private static final String FILE = "questlog_groups.dat";
    private final MinecraftServer server;
    private final Path testPath;
    private final Map<UUID, Group> groups = new LinkedHashMap<>();
    private final Set<UUID> disconnecting = new LinkedHashSet<>();

    public static final class Group {
        public final UUID id;
        public String name;
        public UUID leader;
        public boolean open = true;
        public int lootPolicy;
        public final Map<UUID, String> members = new LinkedHashMap<>();
        public final Map<UUID, String> applicants = new LinkedHashMap<>();
        public final Set<UUID> invitations = new LinkedHashSet<>();

        Group(UUID id, String name, UUID leader) {
            this.id = id;
            this.name = name;
            this.leader = leader;
        }
    }

    public AdventureGroups(MinecraftServer server) {
        this.server = server;
        this.testPath = null;
        load();
    }

    AdventureGroups(Path path) {
        this.server = null;
        this.testPath = path;
        load();
    }

    private Path path() {
        return testPath != null ? testPath : server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(FILE);
    }

    public Group group(UUID id) { return groups.get(id); }
    void register(Group group) { groups.put(group.id, group); }

    public Group membership(UUID player) {
        for (Group group : groups.values()) if (group.members.containsKey(player)) return group;
        return null;
    }

    private static UUID uuid(CompoundTag tag, String key) {
        try { return UUID.fromString(tag.getString(key)); }
        catch (IllegalArgumentException e) { return null; }
    }

    private static ListTag people(Map<UUID, String> names) {
        ListTag list = new ListTag();
        names.forEach((id, name) -> {
            CompoundTag row = new CompoundTag();
            row.putString("id", id.toString());
            row.putString("name", name);
            list.add(row);
        });
        return list;
    }

    private static Map<UUID, String> readPeople(CompoundTag tag, String key) throws IOException {
        Map<UUID, String> result = new LinkedHashMap<>();
        for (Tag item : tag.getList(key, Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) item;
            UUID id = uuid(row, "id");
            if (id == null || result.putIfAbsent(id, row.getString("name")) != null) throw new IOException("Invalid or duplicate person in " + key);
        }
        return result;
    }

    private CompoundTag write() {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        for (Group group : groups.values()) {
            CompoundTag row = new CompoundTag();
            row.putString("id", group.id.toString());
            row.putString("name", group.name);
            row.putString("leader", group.leader.toString());
            row.putBoolean("open", group.open);
            row.putInt("loot", group.lootPolicy);
            row.put("members", people(group.members));
            row.put("applicants", people(group.applicants));
            ListTag invitations = new ListTag();
            for (UUID id : group.invitations) {
                CompoundTag invite = new CompoundTag();
                invite.putString("id", id.toString());
                invitations.add(invite);
            }
            row.put("invitations", invitations);
            list.add(row);
        }
        root.put("groups", list);
        return root;
    }

    private void load() {
        Path file = path();
        Path backup = file.resolveSibling(file.getFileName() + "_old");
        if (!Files.isRegularFile(file) && !Files.isRegularFile(backup)) return;
        try {
            read(file);
        } catch (IOException | RuntimeException primary) {
            groups.clear();
            Questlog.LOGGER.error("Cannot load adventure groups; trying the backup", primary);
            try {
                read(backup);
                Files.copy(backup, file, StandardCopyOption.REPLACE_EXISTING);
                Questlog.LOGGER.warn("Adventure groups restored from backup");
            } catch (IOException | RuntimeException secondary) {
                groups.clear();
                Questlog.LOGGER.error("Cannot load adventure group backup; group actions are disabled to protect the save", secondary);
                loadFailed = true;
            }
        }
    }

    private void read(Path file) throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("Missing group file: " + file);
        CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        if (!root.contains("groups", Tag.TAG_LIST)) throw new IOException("Missing group list");
        Set<UUID> seenMembers = new LinkedHashSet<>();
        for (Tag item : root.getList("groups", Tag.TAG_COMPOUND)) {
                CompoundTag row = (CompoundTag) item;
                UUID id = uuid(row, "id"), leader = uuid(row, "leader");
                if (id == null || leader == null || groups.containsKey(id)) throw new IOException("Invalid or duplicate group identity");
                if (!row.contains("members", Tag.TAG_LIST) || !row.contains("applicants", Tag.TAG_LIST)
                        || !row.contains("invitations", Tag.TAG_LIST) || !row.contains("name", Tag.TAG_STRING))
                    throw new IOException("Incomplete group entry");
                Group group = new Group(id, row.getString("name"), leader);
                group.open = row.getBoolean("open");
                group.lootPolicy = Math.max(0, Math.min(2, row.getInt("loot")));
                group.members.putAll(readPeople(row, "members"));
                group.applicants.putAll(readPeople(row, "applicants"));
                for (Tag invite : row.getList("invitations", Tag.TAG_COMPOUND)) {
                    UUID player = uuid((CompoundTag) invite, "id");
                    if (player == null || !group.invitations.add(player)) throw new IOException("Invalid or duplicate invitation");
                }
                if (!group.members.containsKey(leader)) throw new IOException("Invalid leader");
                for (UUID member : group.members.keySet()) if (!seenMembers.add(member)) throw new IOException("Duplicate member across groups");
                register(group);
        }
    }

    private boolean loadFailed;

    public boolean save() {
        if (loadFailed) return false;
        Path file = path(), temp = null;
        try {
            Files.createDirectories(file.getParent());
            temp = Files.createTempFile(file.getParent(), "questlog-groups-", ".dat");
            NbtIo.writeCompressed(write(), temp);
            if (Files.isRegularFile(file)) Files.copy(file, file.resolveSibling(file.getFileName() + "_old"), StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            Questlog.LOGGER.error("Cannot save adventure groups", e);
            if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
            return false;
        }
    }

    private void notify(UUID id, String key, Object... args) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player != null) player.sendSystemMessage(Component.literal("[Questlog] ").append(Component.translatable("questlog.groups.message." + key, args)));
    }

    private void changed() {
        if (!save()) {
            loadFailed = true;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) notify(player.getUUID(), "save_failed");
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sync(player, "");
    }

    public void onLogin(ServerPlayer player) {
        disconnecting.remove(player.getUUID());
        Group group = membership(player.getUUID());
        if (group != null && !player.getGameProfile().getName().equals(group.members.get(player.getUUID()))) {
            group.members.put(player.getUUID(), player.getGameProfile().getName());
            changed();
        } else sync(player, "");
    }

    public void onLogout(ServerPlayer player) {
        disconnecting.add(player.getUUID());
        Group group = membership(player.getUUID());
        if (group != null) for (UUID member : group.members.keySet()) if (!member.equals(player.getUUID())) notify(member, "offline", player.getGameProfile().getName());
        for (ServerPlayer other : server.getPlayerList().getPlayers()) if (!other.getUUID().equals(player.getUUID())) sync(other, "");
    }

    void clearOffers(UUID player) {
        for (Group group : groups.values()) {
            group.applicants.remove(player);
            group.invitations.remove(player);
        }
    }

    void join(Group group, UUID player, String name) {
        clearOffers(player);
        group.members.put(player, name);
    }

    void leave(Group group, UUID player) {
        group.members.remove(player);
        if (group.members.isEmpty()) groups.remove(group.id);
    }

    private void error(ServerPlayer player, String key) { notify(player.getUUID(), key); sync(player, ""); }

    static boolean canApply(Group own, Group group, UUID actor) {
        return own == null && group != null && group.open && !group.applicants.containsKey(actor) && !group.invitations.contains(actor);
    }
    static boolean canAcceptApplicant(Group group, UUID actor, UUID target, Group targetGroup) {
        return group != null && actor.equals(group.leader) && target != null && group.applicants.containsKey(target) && targetGroup == null;
    }
    static boolean canInvite(Group group, UUID actor, UUID target, Group targetGroup, boolean online) {
        return group != null && actor.equals(group.leader) && target != null && online && targetGroup == null && !group.invitations.contains(target);
    }
    static boolean canLeave(Group own, UUID actor) {
        return own != null && (!own.leader.equals(actor) || own.members.size() == 1);
    }
    static boolean canManageMember(Group group, UUID actor, UUID target) {
        return group != null && actor.equals(group.leader) && target != null && !target.equals(actor) && group.members.containsKey(target);
    }

    public void act(ServerPlayer player, int action, UUID groupId, UUID target, String value) {
        if (loadFailed) { error(player, "unavailable"); return; }
        UUID actor = player.getUUID();
        String name = player.getGameProfile().getName();
        Group own = membership(actor), group = groups.get(groupId);
        switch (action) {
            case 0 -> { // create
                String title = value.trim();
                if (own != null || title.isEmpty() || title.length() > 32 || title.chars().anyMatch(c -> Character.isISOControl((char)c))) { error(player, "invalid_name"); return; }
                if (groups.values().stream().anyMatch(g -> g.name.equalsIgnoreCase(title))) { error(player, "duplicate_name"); return; }
                Group created = new Group(UUID.randomUUID(), title, actor);
                created.members.put(actor, name);
                register(created);
            }
            case 1 -> { // apply
                if (!canApply(own, group, actor)) { error(player, "cannot_apply"); return; }
                group.applicants.put(actor, name);
                notify(group.leader, "applied", name, group.name);
            }
            case 2 -> { // cancel application
                if (group == null || group.applicants.remove(actor) == null) { error(player, "stale_application"); return; }
                notify(group.leader, "application_cancelled", name);
            }
            case 3 -> { // accept applicant
                if (!canAcceptApplicant(group, actor, target, target == null ? null : membership(target))) { error(player, "stale_application"); return; }
                join(group, target, group.applicants.get(target));
                notify(target, "joined", group.name);
            }
            case 4 -> { // refuse applicant
                if (group == null || !actor.equals(group.leader) || target == null || group.applicants.remove(target) == null) { error(player, "stale_application"); return; }
                notify(target, "application_refused", group.name);
            }
            case 5 -> { // invite online ungrouped player
                ServerPlayer invited = target == null ? null : server.getPlayerList().getPlayer(target);
                if (!canInvite(group, actor, target, target == null ? null : membership(target), invited != null)) { error(player, "cannot_invite"); return; }
                group.invitations.add(target);
                notify(target, "invited", name, group.name);
            }
            case 6 -> { // accept invite
                if (own != null || group == null || !group.invitations.contains(actor)) { error(player, "stale_invite"); return; }
                join(group, actor, name);
                notify(group.leader, "invite_accepted", name);
            }
            case 7 -> { // refuse invite
                if (group == null || !group.invitations.remove(actor)) { error(player, "stale_invite"); return; }
                notify(group.leader, "invite_refused", name);
            }
            case 8 -> { // leave
                if (own == null || !own.id.equals(groupId) || !canLeave(own, actor)) { error(player, "transfer_first"); return; }
                leave(own, actor);
                if (!own.members.isEmpty()) notify(own.leader, "left", name);
            }
            case 9 -> { // kick
                if (!canManageMember(group, actor, target)) { error(player, "cannot_manage"); return; }
                String removed = group.members.remove(target);
                notify(target, "kicked", group.name);
                notify(actor, "member_kicked", removed);
            }
            case 10 -> { // transfer leadership
                if (!canManageMember(group, actor, target)) { error(player, "cannot_manage"); return; }
                group.leader = target;
                notify(target, "leader", group.name);
            }
            case 11 -> { // recruitment
                if (group == null || !actor.equals(group.leader)) { error(player, "denied"); return; }
                group.open = !group.open;
            }
            case 12 -> { // loot policy, storage only
                int policy;
                try { policy = Integer.parseInt(value); } catch (NumberFormatException e) { error(player, "invalid_rule"); return; }
                if (group == null || !actor.equals(group.leader) || policy < 0 || policy > 2) { error(player, "denied"); return; }
                group.lootPolicy = policy;
            }
            default -> { error(player, "unknown_action"); return; }
        }
        changed();
    }

    public void sync(ServerPlayer viewer, String search) {
        CompoundTag data = new CompoundTag();
        UUID id = viewer.getUUID();
        Group own = membership(id);
        if (own != null) data.putString("own", own.id.toString());
        ListTag list = new ListTag();
        for (Group group : groups.values()) {
            CompoundTag row = new CompoundTag();
            row.putString("id", group.id.toString());
            row.putString("name", group.name);
            row.putString("leader", group.leader.toString());
            row.putBoolean("open", group.open);
            row.putInt("loot", group.lootPolicy);
            row.putInt("count", group.members.size());
            row.put("members", people(group.members));
            if (canViewApplicants(own, group)) row.put("applicants", people(group.applicants));
            row.putBoolean("applied", group.applicants.containsKey(id));
            row.putBoolean("invited", group.invitations.contains(id));
            ListTag online = new ListTag();
            for (UUID member : group.members.keySet()) {
                if (server.getPlayerList().getPlayer(member) != null && !disconnecting.contains(member)) {
                    CompoundTag marker = new CompoundTag(); marker.putString("id", member.toString()); online.add(marker);
                }
            }
            row.put("online", online);
            list.add(row);
        }
        data.put("groups", list);
        if (own != null && own.leader.equals(id)) {
            List<ServerPlayer> candidates = new ArrayList<>(server.getPlayerList().getPlayers());
            String query = search.trim().toLowerCase(Locale.ROOT);
            candidates.removeIf(candidate -> candidate.getUUID().equals(id) || membership(candidate.getUUID()) != null || !candidate.getGameProfile().getName().toLowerCase(Locale.ROOT).contains(query));
            if (query.isEmpty()) candidates.removeIf(candidate -> candidate.level().dimension() != viewer.level().dimension());
            candidates.sort(query.isEmpty()
                    ? Comparator.comparingDouble(candidate -> candidate.distanceToSqr(viewer))
                    : Comparator.comparing(candidate -> candidate.getGameProfile().getName().toLowerCase(Locale.ROOT)));
            ListTag players = new ListTag();
            for (ServerPlayer candidate : candidates.stream().limit(10).toList()) {
                CompoundTag row = new CompoundTag(); row.putString("id", candidate.getUUID().toString());
                row.putString("name", candidate.getGameProfile().getName()); players.add(row);
            }
            data.put("candidates", players);
        }
        Services.PLATFORM.sendPacketToClient(viewer, new GroupStatePacket(data));
    }

    static boolean canViewApplicants(Group own, Group viewed) { return own != null && own == viewed; }
}
