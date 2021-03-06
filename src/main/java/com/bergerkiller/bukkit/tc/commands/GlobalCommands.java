package com.bergerkiller.bukkit.tc.commands;

import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.internal.CommonPlugin;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.ui.AttachmentEditor;
import com.bergerkiller.bukkit.tc.attachments.ui.SetValueTarget;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandRequiresPermission;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.debug.DebugTool;
import com.bergerkiller.bukkit.tc.editor.TCMapControl;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.exception.command.NoTrainStorageChestItemException;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.pathfinding.PathWorld;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.CartPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.statements.Statement;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import com.bergerkiller.bukkit.tc.utils.StoredTrainItemUtil;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.Flag;
import cloud.commandframework.annotations.Hidden;
import cloud.commandframework.annotations.specifier.Greedy;
import cloud.commandframework.annotations.specifier.Range;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Consumer;

public class GlobalCommands {

    @CommandMethod("train version")
    @CommandDescription("Shows installed version of TrainCarts and BKCommonLib")
    private void commandShowVersion(
            final CommandSender sender,
            final TrainCarts plugin
    ) {
        plugin.onVersionCommand("version", sender);
    }

    @CommandMethod("train list destinations")
    @CommandDescription("Lists all the destination names that exist on the server")
    private void commandListDestinations(
            final CommandSender sender
    ) {
        MessageBuilder builder = new MessageBuilder();
        builder.yellow("The following train destinations are available:");
        builder.newLine().setSeparator(ChatColor.WHITE, " / ");
        Collection<PathWorld> worlds;
        if (sender instanceof Player) {
            World playerWorld = ((Player) sender).getWorld();
            worlds = Collections.singleton(TrainCarts.plugin.getPathProvider().getWorld(playerWorld));
        } else {
            worlds = TrainCarts.plugin.getPathProvider().getWorlds();
        }
        for (PathWorld world : worlds) {
            for (PathNode node : world.getNodes()) {
                if (!node.containsOnlySwitcher()) {
                    builder.green(node.getName());
                }
            }
        }
        builder.send(sender);
    }

    @CommandMethod("train list [statement]")
    @CommandDescription("Lists all the destination names that exist on the server")
    private void commandListTrains(
            final CommandSender sender,
            final @Argument("statement") @Greedy String statementText
    ) {
        // Trains
        int count = 0, moving = 0;
        for (MinecartGroup group : MinecartGroupStore.getGroups()) {
            count++;
            if (group.isMoving()) {
                moving++;
            }
            // Get properties: ensures that ALL trains are listed
            group.getProperties();
        }
        count += OfflineGroupManager.getStoredCountInLoadedWorlds();
        int minecartCount = 0;
        for (World world : WorldUtil.getWorlds()) {
            for (org.bukkit.entity.Entity e : WorldUtil.getEntities(world)) {
                if (e instanceof Minecart) {
                    minecartCount++;
                }
            }
        }
        MessageBuilder builder = new MessageBuilder();
        builder.green("There are ").yellow(count).green(" trains on this server (of which ");
        builder.yellow(moving).green(" are moving)");
        builder.newLine().green("There are ").yellow(minecartCount).green(" minecart entities");
        builder.send(sender);
        // Show additional information about owned trains to players
        listTrains(sender, statementText == null ? "" : statementText);
    }

    @CommandRequiresPermission(Permission.COMMAND_MESSAGE)
    @CommandMethod("train message <key>")
    @CommandDescription("Checks what value is assigned to a given message key")
    private void commandGetMessage(
            final CommandSender sender,
            final @Argument("key") String key
    ) {
        String value = TCConfig.messageShortcuts.get(key);
        if (value == null) {
            sender.sendMessage(ChatColor.RED + "No shortcut is set for key '" + key + "'");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Shortcut value of '" + key + "' = " + ChatColor.WHITE + value);
        }
    }

    @CommandRequiresPermission(Permission.COMMAND_MESSAGE)
    @CommandMethod("train message <key> <value>")
    @CommandDescription("Checks what value is assigned to a given message key")
    private void commandSetMessage(
            final CommandSender sender,
            final @Argument("key") String key,
            final @Argument("value") @Greedy String value
    ) {
        String conv_value = StringUtil.ampToColor(value);
        TCConfig.messageShortcuts.remove(key);
        TCConfig.messageShortcuts.add(key, conv_value);
        TrainCarts.plugin.saveShortcuts();
        sender.sendMessage(ChatColor.GREEN + "Shortcut '" + key + "' set to: " + ChatColor.WHITE + conv_value);
    }

    @CommandRequiresPermission(Permission.COMMAND_DESTROYALL)
    @CommandMethod("train destroyall|removeall")
    @CommandDescription("Destroys all trains server-wide")
    private void commandDestroyAll(
            final CommandSender sender
    ) {
        // Destroy all trains on the entire server
        int count = OfflineGroupManager.destroyAll();
        sender.sendMessage(ChatColor.RED.toString() + count + " (visible) trains have been destroyed!");
    }

    @CommandRequiresPermission(Permission.COMMAND_DESTROYALL)
    @CommandMethod("train destroyall|removeall <worldname>")
    @CommandDescription("Destroys all trains on a single world")
    private void commandDestroyAllOnWorld(
            final CommandSender sender,
            final @Argument("worldname") String worldName
    ) {
        // Destroy the trains on a single world
        String cname = worldName.toLowerCase();
        World w = Bukkit.getWorld(worldName);
        if (w == null) {
            Bukkit.getWorld(cname);
        }
        if (w == null) {
            for (World world : Bukkit.getServer().getWorlds()) {
                if (world.getName().toLowerCase().contains(cname)) {
                    w = world;
                    break;
                }
            }
        }
        if (w != null) {
            int count = OfflineGroupManager.destroyAll(w);
            sender.sendMessage(ChatColor.RED.toString() + count + " (visible) trains have been destroyed!");
        } else {
            sender.sendMessage(ChatColor.RED + "World not found!");
        }
    }

    @CommandRequiresPermission(Permission.COMMAND_GIVE_EDITOR)
    @CommandMethod("train menu set <value>")
    @CommandDescription("Updates a menu item in a TrainCarts editor map using commands")
    private void commandMenuSet(
            final Player sender,
            final @Argument("value") @Greedy String value
    ) {
        // Get editor instance
        MapDisplay display = MapDisplay.getHeldDisplay((Player) sender, AttachmentEditor.class);
        if (display == null) {
            display = MapDisplay.getHeldDisplay((Player) sender);
            if (display == null) {
                sender.sendMessage(ChatColor.RED + "You do not have an editor menu open");
                return;
            }
        }

        // Find focused widget
        MapWidget focused = display.getFocusedWidget();
        if (!(focused instanceof SetValueTarget)) {
            focused = display.getActivatedWidget();
        }
        if (!(focused instanceof SetValueTarget)) {
            sender.sendMessage(ChatColor.RED + "No suitable menu item is active!");
            return;
        }

        // Got a target, input the value into it
        SetValueTarget target = (SetValueTarget) focused;
        boolean success = target.acceptTextValue(value);
        String propname = target.getAcceptedPropertyName();
        if (success) {
            sender.sendMessage(ChatColor.GREEN + propname + " has been updated");
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to update " + propname + "!");
        }
    }

    @CommandRequiresPermission(Permission.COMMAND_REROUTE)
    @CommandMethod("train reroute")
    @CommandDescription("Recalculates all path finding information on the server")
    private void commandReroute(
            final CommandSender sender,
            final @Flag(value="lazy", description="Delays recalculating routes until a train needs it") boolean lazy
    ) {
        if (lazy) {
            PathNode.clearAll();
            sender.sendMessage(ChatColor.YELLOW + "All train routings will be recalculated when needed");
        } else {
            PathNode.reroute();
            sender.sendMessage(ChatColor.YELLOW + "All train routings will be recalculated");
        }
    }

    @CommandRequiresPermission(Permission.COMMAND_RELOAD)
    @CommandMethod("train globalconfig reload|load")
    @CommandDescription("Reloads one or more global TrainCarts configuration files from disk")
    private void commandReloadConfig(
            final CommandSender sender,
            final @Flag(value="config", description="Reload config.yml") boolean config,
            final @Flag(value="routes", description="Reload routes.yml") boolean routes,
            final @Flag(value="defaulttrainproperties", description="Reload DefaultTrainProperties.yml") boolean defaultTrainproperties,
            final @Flag(value="savedtrainproperties", description="Reload SavedTrainProperties.yml and modules") boolean savedTrainproperties
    ) {
        if (!config &&
            !routes &&
            !defaultTrainproperties &&
            !savedTrainproperties
        ) {
            sender.sendMessage(ChatColor.RED + "Please specify one or more configuration file to reload:");
            sender.sendMessage(ChatColor.RED + "/train globalconfig reload --config");
            sender.sendMessage(ChatColor.RED + "/train globalconfig reload --routes");
            sender.sendMessage(ChatColor.RED + "/train globalconfig reload --defaulttrainproperties");
            sender.sendMessage(ChatColor.RED + "/train globalconfig reload --savedtrainproperties");
            return;
        }

        if (config) {
            TrainCarts.plugin.loadConfig();
        }
        if (routes) {
            TrainCarts.plugin.getRouteManager().load();
        }
        if (defaultTrainproperties) {
            TrainProperties.loadDefaults();
        }
        if (savedTrainproperties) {
            TrainCarts.plugin.loadSavedTrains();
        }
        sender.sendMessage(ChatColor.YELLOW + "Configuration has been reloaded!");
    }

    @CommandRequiresPermission(Permission.COMMAND_SAVEALL)
    @CommandMethod("train globalconfig save")
    @CommandDescription("Forces a save of all configuration to disk")
    private void commandReloadConfig(
            final CommandSender sender
    ) {
        TrainCarts.plugin.save(false);
        sender.sendMessage(ChatColor.YELLOW + "TrainCarts' information has been saved to file.");
    }

    @Hidden
    @CommandRequiresPermission(Permission.COMMAND_UPGRADESAVED)
    @CommandMethod("train upgradesavedtrains")
    @CommandDescription("Upgrades all saved train properties to correct for position changes during v1.12.2")
    private void commandUpgradeSavedTrains(
            final CommandSender sender,
            final @Flag("undo") boolean undo
    ) {
        TrainCarts.plugin.getSavedTrains().upgradeSavedTrains(undo);
        if (undo) {
            sender.sendMessage(ChatColor.YELLOW + "All saved trains have been restored to use the old position calibration of Traincarts v1.12.2-v2 (UNDO)");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "All saved trains have been upgraded to use the new position calibration of Traincarts v1.12.2-v3");
        }
    }

    @CommandRequiresPermission(Permission.COMMAND_FIXBUGGED)
    @CommandMethod("train fixbugged")
    @CommandDescription("Forcibly removes minecarts and trackers that have glitched out")
    private void commandFixBugged(
            final CommandSender sender
    ) {
        for (World world : WorldUtil.getWorlds()) {
            OfflineGroupManager.removeBuggedMinecarts(world);
        }
        sender.sendMessage(ChatColor.YELLOW + "Bugged minecarts have been forcibly removed.");
    }

    @CommandMethod("train edit")
    @CommandDescription("Selects a train the player is looking at for editing")
    private void commandEditLookingAt(
            final Player player
    ) {
        // Create an inverted camera transformation of the player's view direction
        World playerWorld = player.getWorld();
        Matrix4x4 cameraTransform = new Matrix4x4();
        cameraTransform.translateRotate(player.getEyeLocation());
        cameraTransform.invert();

        // Go by all minecarts on the server, and pick those close in view on the same world
        // The transformed point is a projective view of the Minecart in the player's vision
        // X/Y is left-right/up-down and Z is depth after the transformation is applied
        MinecartMember<?> bestMember = null;
        Vector bestPos = null;
        double bestDistance = Double.MAX_VALUE;
        for (MinecartGroup group : MinecartGroup.getGroups().cloneAsIterable()) {
            if (group.getWorld() != playerWorld) continue;
            for (MinecartMember<?> member : group) {
                Vector pos = member.getEntity().loc.vector();
                cameraTransform.transformPoint(pos);

                // Behind the player
                if (pos.getZ() < 0.0) {
                    continue;
                }

                // Check if position is allowed
                double lim = Math.max(1.0, MathUtil.HALFROOTOFTWO * pos.getZ());
                if (Math.abs(pos.getX()) > lim || Math.abs(pos.getY()) > lim) {
                    continue;
                }

                // Pick lowest distance
                double distance = Math.sqrt(pos.getX() * pos.getX() + pos.getY() * pos.getY()) / lim;
                if (bestPos == null || distance < bestDistance) {
                    bestPos = pos;
                    bestDistance = distance;
                    bestMember = member;
                }
            }
        }

        if (bestMember != null && !bestMember.getProperties().hasOwnership(player)) {
            player.sendMessage(ChatColor.RED + "You do not own this train and can not edit it!");
        } else if (bestMember != null) {
            // Play a particle effect shooting upwards from the Minecart
            final Entity memberEntity = bestMember.getEntity().getEntity();
            new Task(TrainCarts.plugin) {
                final int batch_ctr = 5;
                double dy = 0.0;

                @Override
                public void run() {
                    for (int i = 0; i < batch_ctr; i++) {
                        if (dy > 50.0 || !player.isOnline() || memberEntity.isDead()) {
                            stop();
                            return;
                        }
                        Location loc = memberEntity.getLocation();
                        loc.add(0.0, dy, 0.0);
                        player.playEffect(loc, Effect.SMOKE, 4);
                        dy += 1.0;
                    }
                }
            }.start(1, 1);

            // Mark minecart as editing
            CartProperties.setEditing(player, bestMember.getProperties());
            player.sendMessage(ChatColor.GREEN + "You are now editing train '" + bestMember.getGroup().getProperties().getTrainName() + "'!");
        } else {
            player.sendMessage(ChatColor.RED + "You are not looking at any Minecart right now");
            player.sendMessage(ChatColor.RED + "Please enter the exact name of the train to edit");
            commandListTrains(player, null);
        }
    }

    @CommandMethod("train edit <trainname>")
    @CommandDescription("Forcibly removes minecarts and trackers that have glitched out")
    private void commandEditByName(
            final Player sender,
            final @Argument(value="trainname", suggestions="trainnames") String trainName
    ) {
        TrainProperties prop = TrainProperties.exists(trainName) ? TrainProperties.get(trainName) : null;
        if (prop != null && !prop.isEmpty()) {
            if (prop.hasOwnership((Player) sender)) {
                CartPropertiesStore.setEditing((Player) sender, prop.get(0));
                sender.sendMessage(ChatColor.GREEN + "You are now editing train '" + prop.getTrainName() + "'!");
            } else {
                sender.sendMessage(ChatColor.RED + "You do not own this train and can not edit it!");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Could not find a valid train named '" + trainName + "'!");
            commandListTrains(sender, null);
        }
    }

    @CommandRequiresPermission(Permission.COMMAND_CHANGETICK)
    @CommandMethod("train tick disable")
    @CommandDescription("Disables ticking of all trains, causing all physics to pause")
    private void commandTickDisable(
            final CommandSender sender
    ) {
        TCConfig.tickUpdateDivider = Integer.MAX_VALUE;
        sender.sendMessage(ChatColor.YELLOW + "Train tick updates have been globally " + ChatColor.RED + "disabled");
    }

    @CommandRequiresPermission(Permission.COMMAND_CHANGETICK)
    @CommandMethod("train tick enable")
    @CommandDescription("Enables ticking of all trains, causing all physics to resume")
    private void commandTickEnable(
            final CommandSender sender
    ) {
        TCConfig.tickUpdateDivider = 1;
        sender.sendMessage(ChatColor.YELLOW + "Train tick updates have been globally " + ChatColor.GREEN + "enabled");
    }

    @CommandRequiresPermission(Permission.COMMAND_CHANGETICK)
    @CommandMethod("train tick div")
    @CommandDescription("Checks what kind of tick divider configuration is configured")
    private void commandGetTickDivider(
            final CommandSender sender
    ) {
        if (TCConfig.tickUpdateDivider == Integer.MAX_VALUE) {
            sender.sendMessage(ChatColor.YELLOW + "Automatic train tick updates are globally disabled");
        } else {
            sender.sendMessage(ChatColor.GREEN + "The tick rate divider is currently set to " + ChatColor.YELLOW + TCConfig.tickUpdateDivider);
        }
    }

    @CommandRequiresPermission(Permission.COMMAND_CHANGETICK)
    @CommandMethod("train tick div reset")
    @CommandDescription("Resets any previous global tick divider, resuming physics as normal")
    private void commandResetTickDivider(
            final CommandSender sender
    ) {
        commandSetTickDivider(sender, 1);
    }

    @CommandRequiresPermission(Permission.COMMAND_CHANGETICK)
    @CommandMethod("train tick div <divider>")
    @CommandDescription("Configures a global tick divider, causing all physics to run more slowly")
    private void commandSetTickDivider(
            final CommandSender sender,
            final @Argument("divider") int divider
    ) {
        if (divider > 1) {
            TCConfig.tickUpdateDivider = divider;
            sender.sendMessage(ChatColor.GREEN + "The tick rate divider has been set to " + ChatColor.YELLOW + TCConfig.tickUpdateDivider);
        } else {
            TCConfig.tickUpdateDivider = 1;
            sender.sendMessage(ChatColor.GREEN + "The tick rate divider has been reset to the default");
        }
    }

    @CommandRequiresPermission(Permission.COMMAND_CHANGETICK)
    @CommandMethod("train tick")
    @CommandDescription("Performs a single update tick. Useful when automatic ticking is disabled or slowed down.")
    private void commandPerformTick(
            final CommandSender sender
    ) {
        commandPerformTick(sender, 1);
    }

    @CommandRequiresPermission(Permission.COMMAND_CHANGETICK)
    @CommandMethod("train tick <times>")
    @CommandDescription("Performs a burst of update ticks. Useful when automatic ticking is disabled or slowed down.")
    private void commandPerformTick(
            final CommandSender sender,
            final @Argument("times") @Range(min="1") int number
    ) {
        TCConfig.tickUpdateNow = number;
        if (number <= 1) {
            sender.sendMessage(ChatColor.GREEN + "Trains ticked once");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Trains ticked " + TCConfig.tickUpdateNow + " times");
        }
    }

    @CommandRequiresPermission(Permission.COMMAND_ISSUE)
    @CommandMethod("train issue")
    @CommandDescription("Shows helpful information for posting an issue ticket on our Github")
    private void commandIssueTicket(
            final CommandSender sender
    ) {
        if(sender instanceof Player){
            Player player = (Player)sender;

            ChatText chatText = ChatText.fromMessage(ChatColor.YELLOW.toString() + "Click one of the below options to open an issue on GitHub:");
            chatText.sendTo(player);
            try{
                String bugReport = "## Info" +
                        "\nPlease provide the following information:" +
                        "\n" +
                        "\n- BKCommonLib Version: " + CommonPlugin.getInstance().getDebugVersion() +
                        "\n- TrainCarts Version: " + TrainCarts.plugin.getDebugVersion() +
                        "\n- Server Type and Version: " + Bukkit.getVersion() +
                        "\n" +
                        "\n----" +
                        "\n## Bug" +
                        "\n" +
                        "\n### Description" +
                        "\n" +
                        "\n### Expected Behaviour" +
                        "\n" +
                        "\n### Actual Behaviour" +
                        "\n" +
                        "\n### Steps to reproduce" +
                        "\n" +
                        "\n### Additional Information" +
                        "\n*This issue was created using the `/train issue` command!*";
                
                String featureRequest = "## Feature Request" +
                        "\n" +
                        "\n### Description" +
                        "\n" +
                        "\n### Examples";
                
                chatText = ChatText.empty().appendClickableURL(ChatColor.RED.toString() + ChatColor.UNDERLINE.toString() + "Bug Report", 
                        "https://github.com/bergerhealer/TrainCarts/issues/new?body=" + URLEncoder.encode(bugReport, "UTF-8"),
                        "Click to open a Bug Report");
                chatText.sendTo(player);
                
                chatText = ChatText.empty().appendClickableURL(ChatColor.GREEN.toString() + ChatColor.UNDERLINE.toString() + "Feature Request",
                        "https://github.com/bergerhealer/TrainCarts/issues/new?body=" + URLEncoder.encode(featureRequest, "UTF-8"),
                        "Click to open a Feature Request");
                chatText.sendTo(player);
            }catch(UnsupportedEncodingException ex){
                chatText = ChatText.empty().appendClickableURL(ChatColor.RED.toString() + ChatColor.UNDERLINE.toString() + "Bug Report",
                        "https://github.com/bergerhealer/TrainCarts/issues/new?template=bug_report.md",
                        "Click to open a Bug Report");
                chatText.sendTo(player);
                
                chatText = ChatText.empty().appendClickableURL(ChatColor.GREEN.toString() + ChatColor.UNDERLINE.toString() + "Feature Request",
                        "https://github.com/bergerhealer/TrainCarts/issues/new?template=feature_request.md",
                        "Click to open a Feature Request");
                chatText.sendTo(player);
            }
        }else{
            MessageBuilder builder = new MessageBuilder();
            builder.white("Click one of the below URLs to open an issue on GitHub:");
            
            try{
                String bugReport = "## Info" +
                        "\nPlease provide the following information:" +
                        "\n" +
                        "\n- BKCommonLib Version: " + CommonPlugin.getInstance().getDebugVersion() +
                        "\n- TrainCarts Version: " + TrainCarts.plugin.getDebugVersion() +
                        "\n- Server Type and Version: " + Bukkit.getVersion() +
                        "\n" +
                        "\n----" +
                        "\n## Bug" +
                        "\n" +
                        "\n### Description" +
                        "\n" +
                        "\n### Expected Behaviour" +
                        "\n" +
                        "\n### Actual Behaviour" +
                        "\n" +
                        "\n### Steps to reproduce" +
                        "\n" +
                        "\n### Additional Information" +
                        "\n*This issue was created using the `/train issue` command!*";

                String featureRequest = "## Feature Request" +
                        "\n" +
                        "\n### Description" +
                        "\n" +
                        "\n### Examples";
                
                builder.white("Bug Report: https://github.com/bergerhealer/TrainCarts/issues/new?body=" + URLEncoder.encode(bugReport, "UTF-8"))
                       .append("Feature Request: https://github.com/bergerhealer/TrainCarts/issues/new?body=" + URLEncoder.encode(featureRequest, "UTF-8"));
            }catch(UnsupportedEncodingException ex){
                builder.white("Bug Report: https://github.com/bergerhealer/TrainCarts/issues/new?template=bug_report.md")
                       .append("Feature Request: https://github.com/bergerhealer/TrainCarts/issues/new?template=feature_request.md");
            }
            builder.send(sender);
        }
    }

    @Hidden
    @CommandRequiresPermission(Permission.COMMAND_GIVE_EDITOR)
    @CommandMethod("train editor")
    @CommandDescription("Gives a legacy editor map item (broken)")
    private void commandGiveEditor(
            final Player sender
    ) {
        sender.getInventory().addItem(TCMapControl.createTCMapItem());
        sender.sendMessage("Given editor map item (note: broken)");
    }

    @CommandRequiresPermission(Permission.COMMAND_GIVE_EDITOR)
    @CommandMethod("train attachments")
    @CommandDescription("Gives an attachment editor map item to the player")
    private void commandGiveAttachmentEditor(
            final Player sender
    ) {
        ItemStack item = MapDisplay.createMapItem(AttachmentEditor.class);
        ItemUtil.setDisplayName(item, "Traincarts Attachments Editor");
        CommonTagCompound tag = ItemUtil.getMetaTag(item, true);
        CommonTagCompound display = tag.createCompound("display");
        display.putValue("MapColor", 0xFF0000);
        sender.getInventory().addItem(item);
        sender.sendMessage(ChatColor.GREEN + "Given a Traincarts attachments editor");
    }

    @CommandRequiresPermission(Permission.COMMAND_USE_STORAGE_CHEST)
    @CommandMethod("train chest [spawnconfig]")
    @CommandDescription("Gives a new train-storing chest item, train information to store can be specified")
    private void commandGiveChestItem(
            final Player sender,
            final @Argument("spawnconfig") @Greedy String spawnConfig
    ) {
        // Create a new item and give it to the player
        ItemStack item = StoredTrainItemUtil.createItem();
        if (spawnConfig != null && !spawnConfig.isEmpty()) {
            StoredTrainItemUtil.store(item, spawnConfig);
        }
        sender.getInventory().addItem(item);
        Localization.CHEST_GIVE.message(sender);
    }

    /**
     * Updates the train storage chest item in the player's main hand. Throws
     * exceptions if this operation fails.
     * 
     * @param player
     * @param consumer Modifying function
     */
    private void updateChestItemInInventory(Player player, Consumer<ItemStack> consumer) {
        ItemStack item = HumanHand.getItemInMainHand(player);
        if (!StoredTrainItemUtil.isItem(item)) {
            throw new NoTrainStorageChestItemException();
        }

        item = ItemUtil.cloneItem(item);
        consumer.accept(item);
        HumanHand.setItemInMainHand(player, item);
        Localization.CHEST_UPDATE.message(player);
    }

    @CommandRequiresPermission(Permission.COMMAND_USE_STORAGE_CHEST)
    @CommandMethod("train chest set [spawnconfig]")
    @CommandDescription("Clears the train-storing chest item the player is currently holding")
    private void commandSetChestItem(
            final Player player,
            final @Argument("spawnconfig") @Greedy String spawnConfig
    ) {
        updateChestItemInInventory(player, item -> {
            StoredTrainItemUtil.store(item, spawnConfig==null ? "" : spawnConfig);
        });
    }

    @CommandRequiresPermission(Permission.COMMAND_USE_STORAGE_CHEST)
    @CommandMethod("train chest clear")
    @CommandDescription("Clears the train-storing chest item the player is currently holding")
    private void commandClearChestItem(
            final Player player
    ) {
        updateChestItemInInventory(player, StoredTrainItemUtil::clear);
    }

    @CommandRequiresPermission(Permission.COMMAND_USE_STORAGE_CHEST)
    @CommandMethod("train chest lock")
    @CommandDescription("Locks the train-storing chest item so it can not pick up trains by right-clicking")
    private void commandLockChestItem(
            final Player player
    ) {
        updateChestItemInInventory(player, item -> StoredTrainItemUtil.setLocked(item, true));
    }

    @CommandRequiresPermission(Permission.COMMAND_USE_STORAGE_CHEST)
    @CommandMethod("train chest unlock")
    @CommandDescription("Unlocks the train-storing chest item so it can pick up trains by right-clicking again")
    private void commandUnlockChestItem(
            final Player player
    ) {
        updateChestItemInInventory(player, item -> StoredTrainItemUtil.setLocked(item, false));
    }

    @CommandRequiresPermission(Permission.COMMAND_USE_STORAGE_CHEST)
    @CommandMethod("train chest name <name>")
    @CommandDescription("Sets a descriptive name for the train-storing chest item")
    private void commandNameChestItem(
            final Player player,
            final @Argument("name") String name
    ) {
        updateChestItemInInventory(player, item -> StoredTrainItemUtil.setName(item, name));
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @CommandMethod("train debug rails")
    @CommandDescription("Get a debug stick item to visually display what path tracks use")
    private void commandDebugRails(
            final Player player
    ) {
        giveDebugItem(player, "Rails", "TrainCarts Rails Debugger");
        player.sendMessage(ChatColor.GREEN + "Given a rails debug item. Right-click rails and see where a train would go.");
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @CommandMethod("train debug destination")
    @CommandDescription("Get a debug stick item to visually display the possible path finding routes")
    private void commandDebugDestinationAll(
            final Player player
    ) {
        giveDebugItem(player, "Destinations", "TrainCarts Destination Debugger");
        player.sendMessage(ChatColor.GREEN + "Given a destination debug item. " +
                "Right-click rails to see what destinations can be reached from there.");
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @CommandMethod("train debug destination <destination>")
    @CommandDescription("Get a debug stick item to visually display the route towards a destination")
    private void commandDebugDestinationName(
            final Player player,
            final @Argument("destination") String destination
    ) {
        giveDebugItem(player, "Destination " + destination, "TrainCarts Destination Debugger [" + destination + "]");
        player.sendMessage(ChatColor.GREEN + "Given a destination debug item. " +
                "Right-click rails to see whether and how a train would travel to " + destination + ".");
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @CommandMethod("train debug mutex")
    @CommandDescription("Displays the area of effect of all nearby mutex signs")
    private void commandDebugMutex(
            final Player player
    ) {
        DebugTool.showMutexZones(player);
        player.sendMessage(ChatColor.GREEN + "Displaying mutex zones near your position");
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @CommandMethod("train debug railtracker <enabled>")
    @CommandDescription("Sets whether the rail tracker debugging is currently enabled")
    private void commandDebugSetRailTracker(
            final CommandSender sender,
            final @Argument("enabled") boolean enabled
    ) {
        TCConfig.railTrackerDebugEnabled = enabled;
        commandDebugCheckRailTracker(sender);
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @CommandMethod("train debug railtracker")
    @CommandDescription("Checks whether the rail tracker debugging is currently enabled")
    private void commandDebugCheckRailTracker(
            final CommandSender sender
    ) {
        sender.sendMessage(ChatColor.GREEN + "Displaying tracked rail positions: " +
                (TCConfig.railTrackerDebugEnabled ? "ENABLED" : (ChatColor.RED + "DISABLED")));
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @CommandMethod("train debug wheeltracker <enabled>")
    @CommandDescription("Sets whether the rail tracker debugging is currently enabled")
    private void commandDebugSetWheelTracker(
            final CommandSender sender,
            final @Argument("enabled") boolean enabled
    ) {
        TCConfig.wheelTrackerDebugEnabled = enabled;
        commandDebugCheckWheelTracker(sender);
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @CommandMethod("train debug wheeltracker")
    @CommandDescription("Checks whether the wheel tracker debugging is currently enabled")
    private void commandDebugCheckWheelTracker(
            final CommandSender sender
    ) {
        sender.sendMessage(ChatColor.GREEN + "Displaying tracked wheel positions: " +
                (TCConfig.wheelTrackerDebugEnabled ? "ENABLED" : (ChatColor.RED + "DISABLED")));
    }

    public static void giveDebugItem(Player player, String debugMode, String debugTitle) {
        ItemStack item = ItemUtil.createItem(Material.STICK, 1);
        ItemUtil.getMetaTag(item, true).putValue("TrainCartsDebug", debugMode);
        ItemUtil.setDisplayName(item, debugTitle);

        // Update item in main hand, if it is a debug item
        ItemStack inMainHand = HumanHand.getItemInMainHand(player);
        if (inMainHand != null) {
            CommonTagCompound tag = ItemUtil.getMetaTag(inMainHand, false);
            if (tag != null && tag.containsKey("TrainCartsDebug")) {
                HumanHand.setItemInMainHand(player, item);
                return;
            }
        }

        // Give new item
        player.getInventory().addItem(item);
    }

    public static void listTrains(CommandSender sender, String statement) {
        MessageBuilder builder = new MessageBuilder();
        if (sender instanceof Player) {
            builder.yellow("You are the proud owner of the following trains:");
        } else {
            builder.yellow("The following trains exist on this server:");
        }
        builder.newLine().setSeparator(ChatColor.WHITE, " / ");
        boolean found = false;
        for (TrainProperties prop : TrainProperties.getAll()) {
            if (sender instanceof Player && !prop.hasOwnership((Player) sender)) {
                continue;
            }

            // Check if train is loaded, or stored in a loaded world
            if (!prop.hasHolder() && !OfflineGroupManager.containsInLoadedWorld(prop.getTrainName())) {
                continue;
            }

            if (prop.hasHolder() && statement.length() > 0) {
                MinecartGroup group = prop.getHolder();
                SignActionEvent event = new SignActionEvent((Block) null, group);
                if (!Statement.has(group, statement, event)) {
                    continue;
                }
            }
            found = true;
            if (prop.isLoaded()) {
                builder.green(prop.getTrainName());
            } else {
                builder.red(prop.getTrainName());
            }
        }
        if (found) {
            builder.send(sender);
        } else {
            Localization.EDIT_NONEFOUND.message(sender);
        }
    }
}
