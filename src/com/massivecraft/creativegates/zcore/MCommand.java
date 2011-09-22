package com.massivecraft.creativegates.zcore;

import java.util.*;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.massivecraft.creativegates.zcore.MCommand;
import com.massivecraft.creativegates.zcore.MPlugin;
import com.massivecraft.creativegates.zcore.util.TextUtil;


public abstract class MCommand<T extends MPlugin>
{
	public T p;
	
	// The sub-commands to this command
	public List<MCommand<?>> subCommands;
	
	// The different names this commands will react to  
	public List<String> aliases;
	
	// Information on the args
	public List<String> requiredArgs;
	public LinkedHashMap<String, String> optionalArgs;
	
	// Help info
	public String helpShort;
	public List<String> helpLong;
	
	// Some information on permissions
	public boolean senderMustBePlayer;
	public String permission;
	
	// Information available on execution of the command
	public CommandSender sender; // Will always be set
	public Player player; // Will only be set when the sender is a player
	public List<String> args; // Will contain the arguments, or and empty list if there are none.
	public List<MCommand<?>> commandChain; // The command chain used to execute this command
	
	public MCommand(T p)
	{
		this.p = p;
		
		this.permission = null;
		
		this.subCommands = new ArrayList<MCommand<?>>();
		this.aliases = new ArrayList<String>();
		
		this.requiredArgs = new ArrayList<String>();
		this.optionalArgs = new LinkedHashMap<String, String>();
		
		this.helpShort = "*Default helpShort*";
		this.helpLong = new ArrayList<String>();
	}
	
	// The commandChain is a list of the parent command chain used to get to this command.
	public void execute(CommandSender sender, List<String> args, List<MCommand<?>> commandChain)
	{
		// Set the execution-time specific variables
		this.sender = sender;
		if (sender instanceof Player)
		{
			this.player = (Player)sender;
		}
		else
		{
			this.player = null;
		}
		this.args = args;
		this.commandChain = commandChain;

		// Is there a matching sub command?
		if (args.size() > 0 )
		{
			for (MCommand<?> subCommand: this.subCommands)
			{
				if (subCommand.aliases.contains(args.get(0)))
				{
					args.remove(0);
					commandChain.add(this);
					subCommand.execute(sender, args, commandChain);
					return;
				}
			}
		}
		
		if ( ! validCall(this.sender, this.args))
		{
			return;
		}
		
		perform();
	}
	
	public void execute(CommandSender sender, List<String> args)
	{
		execute(sender, args, new ArrayList<MCommand<?>>());
	}
	
	// This is where the command action is performed.
	public abstract void perform();
	
	
	// -------------------------------------------- //
	// Call Validation
	// -------------------------------------------- //
	
	/**
	 * In this method we validate that all prerequisites to perform this command has been met.
	 */
	
	// TODO: There should be a boolean for silence 
	public boolean validCall(CommandSender sender, List<String> args)
	{
		if ( ! validSenderType(sender))
		{
			sender.sendMessage(p.txt.get("command.sender_must_me_player"));
			return false;
		}
		
		if ( ! validSenderPermissions(sender, true))
		{
			return false;
		}
		
		if ( ! validArgs(args, sender))
		{
			return false;
		}
		
		return true;
	}
	
	public boolean validSenderType(CommandSender sender)
	{
		if (this.senderMustBePlayer && ! (sender instanceof Player))
		{
			return false;
		}
		return true;
	}
	
	public boolean validSenderPermissions(CommandSender sender, boolean informSenderIfNot)
	{
		if (this.permission == null) return true;
		return p.perm.has(sender, this.permission, informSenderIfNot);
	}
	
	public boolean validArgs(List<String> args, CommandSender sender)
	{
		if (args.size() < this.requiredArgs.size())
		{
			if (sender != null)
			{
				sender.sendMessage(p.txt.get("command.to_few_args"));
				sender.sendMessage(this.getUseageTemplate());
			}
			return false;
		}
		
		if (args.size() > this.requiredArgs.size() + this.optionalArgs.size())
		{
			if (sender != null)
			{
				// Get the to many string slice
				List<String> theToMany = args.subList(this.requiredArgs.size() + this.optionalArgs.size(), args.size());
				sender.sendMessage(String.format(p.txt.get("command.to_many_args"), TextUtil.implode(theToMany, " ")));
				sender.sendMessage(this.getUseageTemplate());
			}
			return false;
		}
		return true;
	}
	public boolean validArgs(List<String> args)
	{
		return this.validArgs(args, null);
	}
	
	// -------------------------------------------- //
	// Help and Usage information
	// -------------------------------------------- //
	
	public String getUseageTemplate(List<MCommand<?>> commandChain, boolean addShortHelp)
	{
		StringBuilder ret = new StringBuilder();
		ret.append(p.txt.tags("<c>"));
		ret.append('/');
		
		for (MCommand<?> mc : commandChain)
		{
			ret.append(TextUtil.implode(mc.aliases, ","));
			ret.append(' ');
		}
		
		ret.append(TextUtil.implode(this.aliases, ","));
		
		List<String> args = new ArrayList<String>();
		
		for (String requiredArg : this.requiredArgs)
		{
			args.add("<"+requiredArg+">");
		}
		
		for (Entry<String, String> optionalArg : this.optionalArgs.entrySet())
		{
			String val = optionalArg.getValue();
			if (val == null)
			{
				val = "";
			}
			else
			{
				val = "="+val;
			}
			args.add("["+optionalArg.getKey()+val+"]");
		}
		
		if (args.size() > 0)
		{
			ret.append(p.txt.tags("<p> "));
			ret.append(TextUtil.implode(args, " "));
		}
		
		if (addShortHelp)
		{
			ret.append(p.txt.tags(" <i>"));
			ret.append(this.helpShort);
		}
		
		return ret.toString();
	}
	
	public String getUseageTemplate(boolean addShortHelp)
	{
		return getUseageTemplate(this.commandChain, addShortHelp);
	}
	
	public String getUseageTemplate()
	{
		return getUseageTemplate(false);
	}
	
	// -------------------------------------------- //
	// Message Sending Helpers
	// -------------------------------------------- //
	
	public void msg(String msg, boolean parseColors)
	{
		if (parseColors)
		{
			sender.sendMessage(p.txt.tags(msg));
			return;
		}
		sender.sendMessage(msg);
	}
	
	public void msg(String msg)
	{
		this.msg(msg, false);
	}
	
	public void msg(List<String> msgs, boolean parseColors)
	{
		for(String msg : msgs)
		{
			this.msg(msg, parseColors);
		}
	}
	
	public void msg(List<String> msgs)
	{
		msg(msgs, false);
	}
	
	// -------------------------------------------- //
	// Argument Readers
	// -------------------------------------------- //
	
	// STRING
	public String argAsString(int idx, String def)
	{
		if (this.args.size() < idx+1)
		{
			return def;
		}
		return this.args.get(idx);
	}
	public String argAsString(int idx)
	{
		return this.argAsString(idx, null);
	}
	
	// INT
	public int argAsInt(int idx, int def)
	{
		String str = this.argAsString(idx);
		if (str == null) return def;
		try
		{
			int ret = Integer.parseInt(str);
			return ret;
		}
		catch (Exception e)
		{
			return def;
		}
	}
	public int argAsInt(int idx)
	{
		return this.argAsInt(idx, -1);
	}
	
	// PLAYER
	public Player argAsPlayer(int idx, Player def)
	{
		String name = this.argAsString(idx);
		if (name == null) return def;
		return Bukkit.getServer().getPlayer(name);
	}
	public Player argAsPlayer(int idx)
	{
		return this.argAsPlayer(idx, null);
	}
}
