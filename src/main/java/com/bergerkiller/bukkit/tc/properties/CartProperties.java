package com.bergerkiller.bukkit.tc.properties;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentModel;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.properties.api.IProperty;
import com.bergerkiller.bukkit.tc.properties.api.IPropertyRegistry;
import com.bergerkiller.bukkit.tc.properties.standard.FieldBackedStandardCartPropertiesHolder;
import com.bergerkiller.bukkit.tc.properties.standard.StandardProperties;
import com.bergerkiller.bukkit.tc.properties.standard.type.ExitOffset;
import com.bergerkiller.bukkit.tc.properties.standard.type.SignSkipOptions;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import com.bergerkiller.bukkit.tc.storage.OfflineMember;
import com.bergerkiller.bukkit.tc.utils.SoftReference;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;

public class CartProperties extends CartPropertiesStore implements IProperties {
    private SoftReference<MinecartMember<?>> member = new SoftReference<>();
    private final FieldBackedStandardCartPropertiesHolder standardProperties = new FieldBackedStandardCartPropertiesHolder();
    private final ConfigurationNode config;
    private final UUID uuid;

    private final Set<Material> blockBreakTypes = new HashSet<>();
    protected TrainProperties group = null;
    private boolean allowPlayerExit = true;
    private boolean allowPlayerEnter = true;
    private boolean invincible = false;
    private String enterMessage = null;
    private boolean isPublic = true;
    private boolean pickUp = false;
    private boolean spawnItemDrops = true;
    private AttachmentModel model = null;
    private String driveSound = "";

    protected CartProperties(TrainProperties group, ConfigurationNode config, UUID uuid) {
        this.uuid = uuid;
        this.group = group;
        this.config = config;
    }

    public static boolean hasGlobalOwnership(Player player) {
        return Permission.COMMAND_GLOBALPROPERTIES.has(player);
    }

    public TrainProperties getTrainProperties() {
        return this.group;
    }

    @Override
    public String getTypeName() {
        return "cart";
    }

    @Override
    public final ConfigurationNode getConfig() {
        return this.config;
    }

    @Override
    public final <T> T get(IProperty<T> property) {
        return property.get(this);
    }

    @Override
    public final <T> void set(IProperty<T> property, T value) {
        property.set(this, value);
    }

    /**
     * Internal use only
     */
    public FieldBackedStandardCartPropertiesHolder getStandardPropertiesHolder() {
        return standardProperties;
    }

    /**
     * Sets the holder of these properties. Internal use only.
     * 
     * @param holder
     */
    protected void setHolder(MinecartMember<?> holder) {
        this.member.set(holder);
    }

    @Override
    public MinecartMember<?> getHolder() {
        MinecartMember<?> member = this.member.get();
        if (member == null || member.getEntity() == null || !member.getEntity().getUniqueId().equals(this.uuid)) {
            return this.member.set(MinecartMemberStore.getFromUID(this.uuid));
        } else {
            return member;
        }
    }

    @Override
    public boolean hasHolder() {
        return getHolder() != null;
    }

    @Override
    public boolean restore() {
        return getTrainProperties().restore() && hasHolder();
    }

    public MinecartGroup getGroup() {
        MinecartMember<?> member = this.getHolder();
        if (member == null) {
            return this.group == null ? null : this.group.getHolder();
        } else {
            return member.getGroup();
        }
    }

    public UUID getUUID() {
        return this.uuid;
    }

    public void tryUpdate() {
        MinecartMember<?> m = this.getHolder();
        if (m != null) m.onPropertiesChanged();
    }

    /**
     * Gets a collection of player UUIDs that are editing these properties
     *
     * @return Collection of editing player UUIDs
     */
    public Collection<UUID> getEditing() {
        ArrayList<UUID> players = new ArrayList<>();
        for (Map.Entry<UUID, CartProperties> entry : editing.entrySet()) {
            if (entry.getValue() == this) {
                players.add(entry.getKey());
            }
        }
        return players;
    }

    /**
     * Gets a collection of online players that are editing these properties
     *
     * @return Collection of editing players
     */
    public Collection<Player> getEditingPlayers() {
        Collection<UUID> uuids = getEditing();
        ArrayList<Player> players = new ArrayList<>(uuids.size());
        for (UUID uuid : uuids) {
            Player p = Bukkit.getServer().getPlayer(uuid);
            if (p != null) {
                players.add(p);
            }
        }
        return players;
    }

    /*
     * Block obtaining
     */
    public boolean canBreak(Block block) {
        return !this.blockBreakTypes.isEmpty() && this.blockBreakTypes.contains(block.getType());
    }

    /*
     * Owners
     */
    @Override
    public boolean hasOwnership(Player player) {
        if (hasGlobalOwnership(player) || this.isOwnedByEveryone() || this.isOwner(player)) {
            return true;
        }
        for (String ownerPermission : this.getOwnerPermissions()) {
            if (CommonUtil.hasPermission(player, ownerPermission)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isOwner(Player player) {
        return this.isOwner(player.getName());
    }

    public boolean isOwner(String player) {
        return get(StandardProperties.OWNERS).contains(player.toLowerCase());
    }

    public void setOwner(String player) {
        this.setOwner(player, true);
    }

    public void setOwner(final String player, final boolean owner) {
        update(StandardProperties.OWNERS, curr_owners -> {
            String player_lc = player.toLowerCase();
            if (curr_owners.contains(player_lc) == owner) {
                return curr_owners;
            } else {
                HashSet<String> new_owners = new HashSet<>(curr_owners);
                LogicUtil.addOrRemove(new_owners, player_lc, owner);
                return new_owners;
            }
        });
    }

    public void setOwner(Player player) {
        this.setOwner(player, true);
    }

    public void setOwner(Player player, boolean owner) {
        if (player == null) {
            return;
        }
        this.setOwner(player.getName().toLowerCase(), owner);
    }

    @Override
    public boolean isOwnedByEveryone() {
        return !this.hasOwners() && !this.hasOwnerPermissions();
    }

    @Override
    public Set<String> getOwnerPermissions() {
        return get(StandardProperties.OWNER_PERMISSIONS);
    }

    public void addOwnerPermission(final String permission) {
        update(StandardProperties.OWNER_PERMISSIONS, curr_perms -> {
            if (curr_perms.contains(permission)) {
                return curr_perms;
            } else {
                HashSet<String> new_perms = new HashSet<>(curr_perms);
                new_perms.add(permission);
                return new_perms;
            }
        });
    }

    public void removeOwnerPermission(final String permission) {
        update(StandardProperties.OWNER_PERMISSIONS, curr_perms -> {
            if (!curr_perms.contains(permission)) {
                return curr_perms;
            } else {
                HashSet<String> new_perms = new HashSet<>(curr_perms);
                new_perms.remove(permission);
                return new_perms;
            }
        });
    }

    @Override
    public void clearOwnerPermissions() {
        set(StandardProperties.OWNER_PERMISSIONS, Collections.emptySet());
    }

    @Override
    public boolean hasOwnerPermissions() {
        return !get(StandardProperties.OWNER_PERMISSIONS).isEmpty();
    }

    @Override
    public Set<String> getOwners() {
        return get(StandardProperties.OWNERS);
    }

    @Override
    public void clearOwners() {
        set(StandardProperties.OWNERS, Collections.emptySet());
    }

    @Override
    public boolean hasOwners() {
        return !get(StandardProperties.OWNERS).isEmpty();
    }

    /**
     * Gets the relative exit offset players are ejected at when
     * seat attachments don't define one.
     * 
     * @return relative exit offset
     */
    public ExitOffset getExitOffset() {
        return get(StandardProperties.EXIT_OFFSET);
    }

    /**
     * Sets the relative exit offset players are ejected at when
     * seat attachments don't define one.
     * 
     * @param new_offset New offset to set to
     */
    public void setExitOffset(ExitOffset new_offset) {
        set(StandardProperties.EXIT_OFFSET, new_offset);
    }

    /**
     * Gets whether this Minecart can pick up nearby items
     *
     * @return True if it can pick up items, False if not
     */
    public boolean canPickup() {
        return this.pickUp;
    }

    public void setPickup(boolean pickup) {
        this.pickUp = pickup;
    }

    @Override
    public boolean isPublic() {
        return this.isPublic;
    }

    @Override
    public void setPublic(boolean state) {
        this.isPublic = state;
    }

    @Override
    public boolean matchTag(String tag) {
        return Util.matchText(this.getTags(), tag);
    }

    @Override
    public boolean hasTags() {
        return !this.getTags().isEmpty();
    }

    @Override
    public void clearTags() {
        set(StandardProperties.TAGS, Collections.emptySet());
    }

    @Override
    public void addTags(final String... tags) {
        update(StandardProperties.TAGS, curr_tags -> {
            HashSet<String> new_tags = new HashSet<String>(curr_tags);
            new_tags.addAll(Arrays.asList(tags));
            return new_tags;
        });
    }

    @Override
    public void removeTags(final String... tags) {
        update(StandardProperties.TAGS, curr_tags -> {
            HashSet<String> new_tags = new HashSet<String>(curr_tags);
            new_tags.removeAll(Arrays.asList(tags));
            return new_tags;
        });
    }

    @Override
    public Set<String> getTags() {
        return get(StandardProperties.TAGS);
    }

    @Override
    public void setTags(String... tags) {
        set(StandardProperties.TAGS, new HashSet<String>(Arrays.asList(tags)));
    }

    @Override
    public boolean getSpawnItemDrops() {
        return this.spawnItemDrops;
    }

    @Override
    public void setSpawnItemDrops(boolean spawnDrops) {
        this.spawnItemDrops = spawnDrops;
    }

    @Override
    public BlockLocation getLocation() {
        MinecartMember<?> member = this.getHolder();
        if (member != null) {
            return new BlockLocation(member.getEntity().getLocation().getBlock());
        } else {
            // Offline member?
            OfflineMember omember = OfflineGroupManager.findMember(this.getTrainProperties().getTrainName(), this.getUUID());
            if (omember == null) {
                return null;
            } else {
                // Find world
                World world = Bukkit.getWorld(omember.group.worldUUID);
                if (world == null) {
                    return new BlockLocation("Unknown", omember.cx << 4, 0, omember.cz << 4);
                } else {
                    return new BlockLocation(world, omember.cx << 4, 0, omember.cz << 4);
                }
            }
        }
    }

    /**
     * Tests whether the Minecart has block types it can break
     *
     * @return True if materials are contained, False if not
     */
    public boolean hasBlockBreakTypes() {
        return !this.blockBreakTypes.isEmpty();
    }

    /**
     * Clears all the materials this Minecart can break
     */
    public void clearBlockBreakTypes() {
        this.blockBreakTypes.clear();
    }

    /**
     * Gets a Collection of materials this Minecart can break
     *
     * @return a Collection of blocks that are broken
     */
    public Collection<Material> getBlockBreakTypes() {
        return this.blockBreakTypes;
    }

    /**
     * Gets the Enter message that is currently displayed when a player enters
     *
     * @return Enter message
     */
    public String getEnterMessage() {
        return this.enterMessage;
    }

    @Override
    public void setEnterMessage(String message) {
        this.enterMessage = message;
    }

    /**
     * Gets whether an Enter message is set
     *
     * @return True if a message is set, False if not
     */
    public boolean hasEnterMessage() {
        return this.enterMessage != null && !this.enterMessage.equals("");
    }

    /**
     * Shows the enter message to the player specified
     *
     * @param player to display the message to
     */
    public void showEnterMessage(Player player) {
        if (this.hasEnterMessage()) {
            TrainCarts.sendMessage(player, ChatColor.YELLOW + TrainCarts.getMessage(enterMessage));
        }
    }

    public void clearDestination() {
        set(StandardProperties.DESTINATION, "");
    }

    @Override
    public boolean hasDestination() {
        return !get(StandardProperties.DESTINATION).isEmpty();
    }

    @Override
    public String getDestination() {
        return get(StandardProperties.DESTINATION);
    }

    @Override
    public void setDestination(String destination) {
        set(StandardProperties.DESTINATION, destination);
    }

    @Override
    public List<String> getDestinationRoute() {
        return get(StandardProperties.DESTINATION_ROUTE);
    }

    @Override
    public void setDestinationRoute(List<String> route) {
        set(StandardProperties.DESTINATION_ROUTE, route);
    }

    @Override
    public void clearDestinationRoute() {
        set(StandardProperties.DESTINATION_ROUTE, Collections.emptyList());
    }

    @Override
    public void addDestinationToRoute(final String destination) {
        if (destination != null && !destination.isEmpty()) {
            update(StandardProperties.DESTINATION_ROUTE, curr_route -> {
                ArrayList<String> new_route = new ArrayList<String>(curr_route);
                new_route.add(destination);
                return new_route;
            });
        }
    }

    @Override
    public void removeDestinationFromRoute(final String destination) {
        if (destination != null && !destination.isEmpty()) {
            update(StandardProperties.DESTINATION_ROUTE, curr_route -> {
                ArrayList<String> new_route = new ArrayList<String>(curr_route);
                while (new_route.remove(destination)); // remove all instances
                return new_route;
            });
        }
    }

    @Override
    public int getCurrentRouteDestinationIndex() {
        List<String> destinationRoute = this.getDestinationRoute();
        String destination = this.getDestination();
        if (destinationRoute.isEmpty() || destination.isEmpty()) {
            return -1;
        }

        int destinationRouteIndex = get(StandardProperties.DESTINATION_ROUTE_INDEX);
        if (destinationRouteIndex < 0 || destinationRouteIndex >= destinationRoute.size()) {
            return destinationRoute.indexOf(destination);
        } else if (destination.equals(destinationRoute.get(destinationRouteIndex))) {
            return destinationRouteIndex;
        } else {
            return destinationRoute.indexOf(destination);
        }
    }

    @Override
    public String getNextDestinationOnRoute() {
        List<String> destinationRoute = this.getDestinationRoute();
        if (destinationRoute.isEmpty()) {
            return "";
        }

        // Correct out of bounds route index
        int destinationRouteIndex = get(StandardProperties.DESTINATION_ROUTE_INDEX);
        if (destinationRouteIndex < 0 || destinationRouteIndex >= destinationRoute.size()) {
            set(StandardProperties.DESTINATION_ROUTE_INDEX, 0);
            destinationRouteIndex = 0;
        }

        // If no destination is set, then we go to whatever index we were last at
        // By default this is 0 (start of the route)
        String destination = this.getDestination();
        if (destination.isEmpty()) {
            return destinationRoute.get(destinationRouteIndex);
        }

        int index;
        if (destination.equals(destinationRoute.get(destinationRouteIndex))) {
            // If current destination matches the current route at the index, pick next one in the list
            index = destinationRouteIndex;
        } else {
            // Index is wrong / out of order destination, pick first one that matches
            index = destinationRoute.indexOf(destination);
            if (index == -1) {
                return ""; // it's not on the route!
            }
        }

        // Next one (loop back to beginning)
        return destinationRoute.get((index + 1) % destinationRoute.size());
    }

    @Override
    public String getLastPathNode() {
        return get(StandardProperties.DESTINATION_LAST_PATH_NODE);
    }

    @Override
    public void setLastPathNode(String nodeName) {
        set(StandardProperties.DESTINATION_LAST_PATH_NODE, nodeName);
    }

    /**
     * Gets the attachment model set for this particular cart. If no model was previously set,
     * a model is created based on the vanilla default model that is used. This model is not saved
     * unless additional changes are made to it.
     * 
     * @return model set, null for Vanilla
     */
    public AttachmentModel getModel() {
        if (this.model == null) {
            // No model was set. Create a Vanilla model based on the Minecart information
            MinecartMember<?> member = this.getHolder();
            EntityType minecartType = (member == null) ? EntityType.MINECART : member.getEntity().getType();
            this.model = AttachmentModel.getDefaultModel(minecartType);
        }
        return this.model;
    }

    /**
     * Resets any set model, restoring the Minecart to its Vanilla defaults.
     */
    public void resetModel() {
        if (this.model != null) {
            MinecartMember<?> member = this.getHolder();
            this.model.resetToDefaults((member == null) ? EntityType.MINECART : member.getEntity().getType());
        }
    }

    /**
     * Sets the attachment model to a named model from the attachment model store.
     * The model will be stored as a named link when saved/reloaded.
     * Calling this method will remove any model set for this minecart.
     * 
     * @param modelName
     */
    public void setModelName(String modelName) {
        if (this.model == null) {
            this.model = new AttachmentModel();
        }
        this.model.resetToName(modelName);
    }

    @Override
    public boolean parseSet(String key, String arg) {
        TrainPropertiesStore.markForAutosave();
        if (key.equalsIgnoreCase("exitoffset")) {
            final Vector vec = Util.parseVector(arg, null);
            if (vec != null) {
                if (vec.length() > TCConfig.maxEjectDistance) {
                    vec.normalize().multiply(TCConfig.maxEjectDistance);
                }
                update(StandardProperties.EXIT_OFFSET, curr_off -> ExitOffset.create(
                        vec, curr_off.getYaw(), curr_off.getPitch()
                ));
            }
        } else if (key.equalsIgnoreCase("exityaw")) {
            final float new_yaw = ParseUtil.parseFloat(arg, 0.0f);
            update(StandardProperties.EXIT_OFFSET, curr_off -> ExitOffset.create(
                    curr_off.getRelativeX(), curr_off.getRelativeY(), curr_off.getRelativeZ(),
                    new_yaw, curr_off.getPitch()
            ));
        } else if (key.equalsIgnoreCase("exitpitch")) {
            final float new_pitch = ParseUtil.parseFloat(arg, 0.0f);
            update(StandardProperties.EXIT_OFFSET, curr_off -> ExitOffset.create(
                    curr_off.getRelativeX(), curr_off.getRelativeY(), curr_off.getRelativeZ(),
                    curr_off.getYaw(), new_pitch
            ));
        } else if (LogicUtil.containsIgnoreCase(key, "exitrot", "exitrotation")) {
            String[] angletext = Util.splitBySeparator(arg);
            final float new_yaw;
            final float new_pitch;
            if (angletext.length == 2) {
                new_yaw = ParseUtil.parseFloat(angletext[0], 0.0f);
                new_pitch = ParseUtil.parseFloat(angletext[1], 0.0f);
            } else if (angletext.length == 1) {
                new_yaw = ParseUtil.parseFloat(angletext[0], 0.0f);
                new_pitch = 0.0f;
            } else {
                new_yaw = 0.0f;
                new_pitch = 0.0f;
            }
            update(StandardProperties.EXIT_OFFSET, curr_off -> ExitOffset.create(
                    curr_off.getRelativeX(), curr_off.getRelativeY(), curr_off.getRelativeZ(),
                    new_yaw, new_pitch
            ));
        } else if (key.equalsIgnoreCase("addtag")) {
            this.addTags(arg);
        } else if (key.equalsIgnoreCase("settag")) {
            this.setTags(arg);
        } else if (LogicUtil.containsIgnoreCase(key, "remtag", "removetag")) {
            this.removeTags(arg);
        } else if (key.equalsIgnoreCase("destination")) {
            this.setDestination(arg);
        } else if (key.equalsIgnoreCase("addroute")) {
            this.addDestinationToRoute(arg);
        } else if (LogicUtil.containsIgnoreCase(key, "remroute", "removeroute")) {
            this.removeDestinationFromRoute(arg);
        } else if (key.equalsIgnoreCase("clearroute")) {
            this.clearDestinationRoute();
        } else if (key.equalsIgnoreCase("setroute")) {
            this.clearDestinationRoute();
            this.addDestinationToRoute(arg);
        } else if (key.equalsIgnoreCase("loadroute")) {
            this.setDestinationRoute(TrainCarts.plugin.getRouteManager().findRoute(arg));
        } else if (key.equalsIgnoreCase("playerenter")) {
            this.setPlayersEnter(ParseUtil.parseBool(arg));
        } else if (key.equalsIgnoreCase("playerexit")) {
            this.setPlayersExit(ParseUtil.parseBool(arg));
        } else if (LogicUtil.containsIgnoreCase(key, "invincible", "godmode")) {
            this.setInvincible(ParseUtil.parseBool(arg));
        } else if (key.equalsIgnoreCase("setownerperm")) {
            this.clearOwnerPermissions();
            this.addOwnerPermission(arg);
        } else if (key.equalsIgnoreCase("addownerperm")) {
            this.addOwnerPermission(arg);
        } else if (key.equalsIgnoreCase("remownerperm")) {
            this.removeOwnerPermission(arg);
        } else if (key.equalsIgnoreCase("setowner")) {
            this.clearOwners();
            this.setOwner(arg);
        } else if (key.equalsIgnoreCase("addowner")) {
            this.setOwner(arg, true);
        } else if (key.equalsIgnoreCase("remowner")) {
            this.setOwner(arg, false);
        } else if (key.equalsIgnoreCase("model")) {
            setModelName(arg);
        } else if (LogicUtil.containsIgnoreCase(key, "clearmodel", "resetmodel")) {
            resetModel();
        } else if (LogicUtil.containsIgnoreCase(key, "spawnitemdrops", "spawndrops", "killdrops")) {
            this.setSpawnItemDrops(ParseUtil.parseBool(arg));
        } else if (LogicUtil.containsIgnoreCase(key, "drivesound", "driveeffect")) {
            this.setDriveSound(arg);
        } else if (LogicUtil.containsIgnoreCase(key, "entermessage", "entermsg")) {
            this.setEnterMessage(arg);
        } else {
            return false;
        }
        this.tryUpdate();
        return true;
    }

    /**
     * Applies configuration. Will be replaced by IProperties eventually.
     * 
     * @param node
     */
    protected void applyConfig(ConfigurationNode node) {
        this.allowPlayerEnter = node.get("allowPlayerEnter", this.allowPlayerEnter);
        this.allowPlayerExit = node.get("allowPlayerExit", this.allowPlayerExit);
        this.invincible = node.get("invincible", this.invincible);
        this.isPublic = node.get("isPublic", this.isPublic);
        this.pickUp = node.get("pickUp", this.pickUp);
        this.spawnItemDrops = node.get("spawnItemDrops", this.spawnItemDrops);
        this.driveSound = node.get("driveSound", this.driveSound);

        if (node.contains("blockBreakTypes")) {
            this.blockBreakTypes.clear();
            for (String blocktype : node.getList("blockBreakTypes", String.class)) {
                Material mat = ParseUtil.parseMaterial(blocktype, null);
                if (mat != null) {
                    this.blockBreakTypes.add(mat);
                }
            }
        }
        if (node.isNode("model")) {
            if (this.model != null) {
                this.model.update(node.getNode("model").clone(), true);
            } else {
                this.model = new AttachmentModel(node.getNode("model").clone());
            }
        }
    }

    /**
     * Loads the properties from the CartProperties source specified<br>
     * This is used when duplicating a cart to a new unique entity.
     *
     * @param source to load from
     * @see #load(ConfigurationNode)
     */
    public void load(CartProperties source) {
        this.load(source.saveToConfig());
    }

    @Override
    public void load(ConfigurationNode node) {
        // Wipe all previous configuration
        this.config.clear();

        // Deep-copy input cart configuration to self cart configuration
        Util.cloneInto(node, this.config, Collections.emptySet());

        // Reload properties from YAML
        onConfigurationChanged();
    }

    @Override
    public void save(ConfigurationNode node) {
        Util.cloneInto(saveToConfig(), node, Collections.emptySet());
    }

    // Temporary while loading is done here
    private <T> T getConfigValue(String key, T defaultValue) {
        return config.contains(key) ? config.get(key, defaultValue) : defaultValue;
    }

    protected void onConfigurationChanged() {
        // Refresh registered IProperties
        // All below should eventually become IProperties, which is when this function
        // can be removed!
        for (IProperty<?> property : IPropertyRegistry.instance().all()) {
            property.onConfigurationChanged(this);
        }

        // TODO: Replace all below with IProperty objects
        // Note: completely disregards all previous configuration!
        this.allowPlayerEnter = getConfigValue("allowPlayerEnter", true);
        this.allowPlayerExit = getConfigValue("allowPlayerExit", true);
        this.invincible = getConfigValue("invincible", false);
        this.isPublic = getConfigValue("isPublic", this.isPublic);
        this.pickUp = getConfigValue("pickUp", false);
        this.spawnItemDrops = getConfigValue("spawnItemDrops", true);
        this.driveSound = getConfigValue("driveSound", "");

        this.blockBreakTypes.clear();
        if (config.contains("blockBreakTypes")) {
            for (String blocktype : config.getList("blockBreakTypes", String.class)) {
                Material mat = ParseUtil.parseMaterial(blocktype, null);
                if (mat != null) {
                    this.blockBreakTypes.add(mat);
                }
            }
        }

        if (config.isNode("model")) {
            if (this.model != null) {
                this.model.update(config.getNode("model").clone(), true);
            } else {
                this.model = new AttachmentModel(config.getNode("model").clone());
            }
        }
    }

    /**
     * Forces all properties to be saved to the {@link #getConfig()}.
     * Note: this will be removed once all properties are
     * part of IProperties! Then they are all live-updated and this
     * method is no longer needed.
     * 
     * @return saved {@link #getConfig()}
     */
    public ConfigurationNode saveToConfig() {
        config.set("allowPlayerEnter", this.allowPlayerEnter ? null : false);
        config.set("allowPlayerExit", this.allowPlayerExit ? null : false);
        config.set("invincible", this.invincible ? true : null);
        config.set("isPublic", this.isPublic ? null : false);
        config.set("pickUp", this.pickUp ? true : null);
        config.set("driveSound", this.driveSound == "" ? null : this.driveSound);
        if (this.blockBreakTypes.isEmpty()) {
            config.remove("blockBreakTypes");
        } else {
            List<String> items = config.getList("blockBreakTypes", String.class);
            items.clear();
            for (Material mat : this.blockBreakTypes) {
                items.add(mat.toString());
            }
        }
        config.set("enterMessage", this.hasEnterMessage() ? this.enterMessage : null);
        config.set("spawnItemDrops", this.spawnItemDrops ? null : false);

        if (this.model != null) {
            config.set("model", this.model.getConfig());
        } else {
            config.remove("model");
        }

        return this.config;
    }

    // Stores all the default property values not already covered by IProperty
    protected static void generateDefaults(ConfigurationNode node) {
        node.set("allowPlayerEnter", true);
        node.set("allowPlayerExit", true);
        node.set("invincible", false);
        node.set("isPublic", true);
        node.set("pickUp", false);
        node.set("driveSound", "");
        node.set("blockBreakTypes", StringUtil.EMPTY_ARRAY);
        node.set("enterMessage", "");
        node.set("spawnItemDrops", true);
    }

    /**
     * Gets wether this Train is invincible or not
     *
     * @return True if enabled, False if not
     */
    public boolean isInvincible() {
        return this.invincible;
    }

    /**
     * Sets wether this Train can be damages
     *
     * @param enabled state to set to
     */
    public void setInvincible(boolean enabled) {
        this.invincible = enabled;
    }

    @Override
    public boolean getPlayersEnter() {
        return this.allowPlayerEnter;
    }

    @Override
    public void setPlayersEnter(boolean state) {
        this.allowPlayerEnter = state;
    }

    @Override
    public boolean getPlayersExit() {
        return this.allowPlayerExit;
    }

    @Override
    public void setPlayersExit(boolean state) {
        this.allowPlayerExit = state;
    }

    public SignSkipOptions getSkipOptions() {
        return get(StandardProperties.SIGN_SKIP);
    }

    public void setSkipOptions(SignSkipOptions options) {
        // Must preserve the signs!
        Set<BlockLocation> old_skipped_signs = get(StandardProperties.SIGN_SKIP).skippedSigns();
        if (old_skipped_signs.equals(options.skippedSigns())) {
            set(StandardProperties.SIGN_SKIP, options);
        } else {
            set(StandardProperties.SIGN_SKIP, SignSkipOptions.create(
                    options.ignoreCounter(),
                    options.skipCounter(),
                    options.filter(),
                    old_skipped_signs));
        }
    }

    public String getDriveSound() {
        return driveSound;
    }

    public void setDriveSound(String driveSound) {
        this.driveSound = driveSound;
    }
}
