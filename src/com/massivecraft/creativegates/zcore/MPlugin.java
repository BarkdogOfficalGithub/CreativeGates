package com.massivecraft.creativegates.zcore;

import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.massivecraft.creativegates.zcore.persist.EM;
import com.massivecraft.creativegates.zcore.persist.SaveTask;
import com.massivecraft.creativegates.zcore.util.LibLoader;
import com.massivecraft.creativegates.zcore.util.PermUtil;
import com.massivecraft.creativegates.zcore.util.Persist;
import com.massivecraft.creativegates.zcore.util.TextUtil;


public abstract class MPlugin extends JavaPlugin
{
	// Some utils
	public Persist persist;
	public TextUtil txt;
	public LibLoader lib;
	public PermUtil perm;
	
	// Persist related
	public Gson gson;	
	private Integer saveTask = null;
	
	// Listeners
	private MPluginSecretPlayerListener mPluginSecretPlayerListener; 
	private MPluginSecretServerListener mPluginSecretServerListener;
	
	// Our stored base commands
	private List<MCommand<?>> baseCommands = new ArrayList<MCommand<?>>();
	public List<MCommand<?>> getBaseCommands() { return this.baseCommands; }

	// -------------------------------------------- //
	// ENABLE
	// -------------------------------------------- //
	private long timeEnableStart;
	public boolean preEnable()
	{
		log("=== ENABLE START ===");
		timeEnableStart = System.currentTimeMillis();
		
		// Ensure basefolder exists!
		this.getDataFolder().mkdirs();
		
		// Create Utility Instances
		this.perm = new PermUtil(this);
		this.persist = new Persist(this);
		this.lib = new LibLoader(this);
		
		if ( ! lib.require("gson.jar", "http://search.maven.org/remotecontent?filepath=com/google/code/gson/gson/1.7.1/gson-1.7.1.jar")) return false;
		this.gson = this.getGsonBuilder().create();
		
		initTXT();
		
		// Create and register listeners
		this.mPluginSecretPlayerListener = new MPluginSecretPlayerListener(this);
		this.mPluginSecretServerListener = new MPluginSecretServerListener(this);
		PluginManager pm = this.getServer().getPluginManager();
		pm.registerEvent(Event.Type.PLAYER_COMMAND_PREPROCESS, this.mPluginSecretPlayerListener, Event.Priority.Lowest, this);
		pm.registerEvent(Event.Type.SERVER_COMMAND, this.mPluginSecretServerListener, Event.Priority.Lowest, this);
		
		// Register recurring tasks
		long saveTicks = 20 * 60 * 30; // Approximately every 30 min
		if (saveTask == null)
		{
			saveTask = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(this, new SaveTask(), saveTicks, saveTicks);
		}
		
		return true;
	}
	
	public void postEnable()
	{
		log("=== ENABLE DONE (Took "+(System.currentTimeMillis()-timeEnableStart)+"ms) ===");
	}
	
	public void onDisable()
	{
		if (saveTask != null)
		{
			this.getServer().getScheduler().cancelTask(saveTask);
			saveTask = null;
		}
		EM.saveAllToDisc();
		log("Disabled");
	}
	
	public void suicide()
	{
		log("Now I suicide!");
		this.getServer().getPluginManager().disablePlugin(this);
	}

	// -------------------------------------------- //
	// Some inits...
	// You are supposed to override these in the plugin if you aren't satisfied with the defaults
	// The goal is that you always will be satisfied though.
	// -------------------------------------------- //

	public GsonBuilder getGsonBuilder()
	{
		return new GsonBuilder()
		.setPrettyPrinting()
		.disableHtmlEscaping()
		.serializeNulls()
		.excludeFieldsWithModifiers(Modifier.TRANSIENT, Modifier.VOLATILE);
	}
	
	// -------------------------------------------- //
	// LANG AND TAGS
	// -------------------------------------------- //
	
	// These are not supposed to be used directly.
	// They are loaded and used through the TextUtil instance for the plugin.
	public Map<String, String> tags = new LinkedHashMap<String, String>();
	public Map<String, String> lang = new LinkedHashMap<String, String>();
	
	public void addLang()
	{
		this.lang.put("perm.forbidden", "<b>You don't have permission to %s.");
		this.lang.put("perm.dothat", "do that");
		this.lang.put("command.sender_must_me_player", "<b>This command can only be used by ingame players.");
		this.lang.put("command.to_few_args", "<b>To few arguments. <i>Use like this:");
		this.lang.put("command.to_many_args", "<b>Strange argument \"<p>%s<b>\". <i>Use like this:");
	}
	
	public void addTags()
	{
		this.tags.put("black", "§0");
		this.tags.put("navy", "§1");
		this.tags.put("green", "§2");
		this.tags.put("teal", "§3");
		this.tags.put("red", "§4");
		this.tags.put("purple", "§5");
		this.tags.put("gold", "§6");
		this.tags.put("silver", "§7");
		this.tags.put("gray", "§8");
		this.tags.put("blue", "§9");
		this.tags.put("white", "§f");
		this.tags.put("lime", "§a");
		this.tags.put("aqua", "§b");
		this.tags.put("rose", "§c");
		this.tags.put("pink", "§d");
		this.tags.put("yellow", "§e");
		
		this.tags.put("l", "§2"); // logo
		this.tags.put("a", "§6"); // art
		this.tags.put("n", "§7"); // notice
		this.tags.put("i", "§e"); // info
		this.tags.put("g", "§a"); // good
		this.tags.put("b", "§c"); // bad
		this.tags.put("h", "§d"); // highligh
		this.tags.put("c", "§b"); // command
		this.tags.put("p", "§3"); // parameter
	}
	
	public void initTXT()
	{
		this.addLang();
		this.addTags();
		
		Type type = new TypeToken<Map<String, String>>(){}.getType();
		
		Map<String, String> langFromFile = this.persist.load(type, "lang");
		if (langFromFile != null) this.lang.putAll(langFromFile);
		this.persist.save(this.lang, "lang");
		
		Map<String, String> tagsFromFile = this.persist.load(type, "tags");
		if (tagsFromFile != null) this.tags.putAll(tagsFromFile);
		this.persist.save(this.tags, "tags");
		
		this.txt = new TextUtil(this.tags, this.lang);
	}
	
	
	// -------------------------------------------- //
	// COMMAND HANDLING
	// -------------------------------------------- //

	public boolean handleCommand(String label, List<String> args, CommandSender player)
	{
		for (MCommand<?> command : this.getBaseCommands())
		{
			if (command.aliases.contains(label))
			{
				command.execute(player, args);
				return true;
			}
		}
		return false;
	}
	
	
	// -------------------------------------------- //
	// LOGGING
	// -------------------------------------------- //
	public void log(Object msg)
	{
		log(Level.INFO, msg);
	}
	
	public void log(Level level, Object msg)
	{
		Logger.getLogger("Minecraft").log(level, "["+this.getDescription().getFullName()+"] "+msg);
	}
}
