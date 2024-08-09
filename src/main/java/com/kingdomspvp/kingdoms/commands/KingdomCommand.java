package com.kingdomspvp.kingdoms.commands;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public abstract class KingdomCommand {
    protected List<String> aliases;
    protected List<String> requiredArgs;
    protected LinkedHashMap<String, String> optionalArgs;
    protected String helpShort;
    protected List<String> helpLong;
    protected List<KingdomCommand> subCommands;

    public KingdomCommand() {
        this.aliases = new ArrayList<>();
        this.requiredArgs = new ArrayList<>();
        this.optionalArgs = new LinkedHashMap<>();
        this.helpShort = null;
        this.helpLong = new ArrayList<>();
        this.subCommands = new ArrayList<>();
    }

    public abstract void perform(KingdomCommandContext context);

    public void execute(KingdomCommandContext context) {
        if (!context.args.isEmpty()) {
            String subCommandName = context.args.get(0).toLowerCase();
            for (KingdomCommand subCommand : this.subCommands) {
                if (subCommand.aliases.contains(subCommandName)) {
                    context.args.remove(0); // Suppression du premier argument
                    subCommand.execute(context);
                    return;
                }
            }
        }

        if (!validCall(context)) {
            return;
        }

        perform(context);
    }

    public boolean validCall(KingdomCommandContext context) {
        return validArgs(context);
    }

    public boolean validArgs(KingdomCommandContext context) {
        if ((context.args.size() < this.requiredArgs.size()) || (context.args.size() > this.requiredArgs.size() + this.optionalArgs.size())) {
            context.sender.sendMessage(getUsageTemplate());
            return false;
        }
        return true;
    }

    public void addSubCommand(KingdomCommand subCommand) {
        this.subCommands.add(subCommand);
    }

    public String getHelpShort() {
        return this.helpShort != null ? this.helpShort : getUsageTemplate();
    }

    public void setHelpShort(String val) {
        this.helpShort = val;
    }

    public String getUsageTemplate() {
        StringBuilder ret = new StringBuilder(ChatColor.GREEN + "/k ");
        ret.append(String.join(", ", this.aliases));
        ret.append(ChatColor.WHITE);

        List<String> args = new ArrayList<>();

        for (String requiredArg : this.requiredArgs) {
            args.add("<" + requiredArg + ">");
        }

        for (String optionalArg : this.optionalArgs.keySet()) {
            args.add("[" + optionalArg + this.optionalArgs.get(optionalArg) + "]");
        }

        if (args.size() > 0) {
            ret.append(" ");
            ret.append(String.join(" ", args));
        }

        if (this.helpShort != null) {
            ret.append("§l§8 - §7");
            ret.append(this.helpShort);
        }

        return ret.toString();
    }

    public abstract String getUsageTranslation();

    public String getHelpMessage() {
        StringBuilder message = new StringBuilder();
        message.append("§a/k ").append(String.join(", ", aliases));
        if (!requiredArgs.isEmpty() || !optionalArgs.isEmpty()) {
            message.append(" ");
        }
        for (String arg : requiredArgs) {
            message.append("§f<").append(arg).append("> ");
        }
        for (String arg : optionalArgs.keySet()) {
            message.append("§f[").append(arg).append("] ");
        }
        message.append("§8- §7").append(helpShort);
        return message.toString();
    }
}
