package org.dynmap.bukkit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bukkit.event.CustomEventListener;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityListener;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.event.world.SpawnChangeEvent;
import org.bukkit.event.world.WorldListener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.dynmap.Log;

public class BukkitEventProcessor {
    private Plugin plugin;
    private PluginManager pm;

    private HashMap<Event.Type, List<Listener>> event_handlers = new HashMap<Event.Type, List<Listener>>();

    public BukkitEventProcessor(Plugin plugin) {
        this.plugin = plugin;
        this.pm = plugin.getServer().getPluginManager();
    }
    
    public void cleanup() {
        /* Clean up all registered handlers */
        for(Event.Type t : event_handlers.keySet()) {
            List<Listener> ll = event_handlers.get(t);
            ll.clear(); /* Empty list - we use presence of list to remember that we've registered with Bukkit */
        }
        pm = null;
        plugin = null;
    }
    
    private BlockListener ourBlockEventHandler = new BlockListener() {
        
        @Override
        public void onBlockPlace(BlockPlaceEvent event) {
            if(event.isCancelled())
                return;
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((BlockListener)l).onBlockPlace(event);
                }
            }
        }

        @Override
        public void onBlockBreak(BlockBreakEvent event) {
            if(event.isCancelled())
                return;
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((BlockListener)l).onBlockBreak(event);
                }
            }
        }

        @Override
        public void onLeavesDecay(LeavesDecayEvent event) {
            if(event.isCancelled())
                return;
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((BlockListener)l).onLeavesDecay(event);
                }
            }
        }
        
        @Override
        public void onBlockBurn(BlockBurnEvent event) {
            if(event.isCancelled())
                return;
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((BlockListener)l).onBlockBurn(event);
                }
            }
        }
        
        @Override
        public void onBlockForm(BlockFormEvent event) {
            if(event.isCancelled())
                return;
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((BlockListener)l).onBlockForm(event);
                }
            }
        }
        @Override
        public void onBlockFade(BlockFadeEvent event) {
            if(event.isCancelled())
                return;
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((BlockListener)l).onBlockFade(event);
                }
            }
        }
        @Override
        public void onBlockSpread(BlockSpreadEvent event) {
            if(event.isCancelled())
                return;
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((BlockListener)l).onBlockSpread(event);
                }
            }
        }
        @Override
        public void onBlockFromTo(BlockFromToEvent event) {
            if(event.isCancelled())
                return;
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((BlockListener)l).onBlockFromTo(event);
                }
            }
        }
        @Override
        public void onBlockPhysics(BlockPhysicsEvent event) {
            if(event.isCancelled())
                return;
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((BlockListener)l).onBlockPhysics(event);
                }
            }
        }
        @Override
        public void onBlockPistonRetract(BlockPistonRetractEvent event) {
            if(event.isCancelled())
                return;
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((BlockListener)l).onBlockPistonRetract(event);
                }
            }
        }
        @Override
        public void onBlockPistonExtend(BlockPistonExtendEvent event) {
            if(event.isCancelled())
                return;
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((BlockListener)l).onBlockPistonExtend(event);
                }
            }
        }
        @Override
        public void onSignChange(SignChangeEvent event) {
            if(event.isCancelled())
                return;
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((BlockListener)l).onSignChange(event);
                }
            }
        }
    };
    private PlayerListener ourPlayerEventHandler = new PlayerListener() {
        @Override
        public void onPlayerJoin(PlayerJoinEvent event) {
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((PlayerListener)l).onPlayerJoin(event);
                }
            }
        }
        @Override
        public void onPlayerLogin(PlayerLoginEvent event) {
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((PlayerListener)l).onPlayerLogin(event);
                }
            }
        }

        @Override
        public void onPlayerMove(PlayerMoveEvent event) {
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((PlayerListener)l).onPlayerMove(event);
                }
            }
        }
        
        @Override
        public void onPlayerQuit(PlayerQuitEvent event) {
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((PlayerListener)l).onPlayerQuit(event);
                }
            }
        }

        @Override
        public void onPlayerBedLeave(PlayerBedLeaveEvent event) {
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((PlayerListener)l).onPlayerBedLeave(event);
                }
            }
        }

        @Override
        public void onPlayerChat(PlayerChatEvent event) {
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((PlayerListener)l).onPlayerChat(event);
                }
            }
        }
    };

    private WorldListener ourWorldEventHandler = new WorldListener() {
        @Override
        public void onWorldLoad(WorldLoadEvent event) {
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((WorldListener)l).onWorldLoad(event);
                }
            }
        }
        @Override
        public void onWorldUnload(WorldUnloadEvent event) {
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((WorldListener)l).onWorldUnload(event);
                }
            }
        }
        @Override
        public void onChunkLoad(ChunkLoadEvent event) {
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((WorldListener)l).onChunkLoad(event);
                }
            }
        }
        @Override
        public void onChunkPopulate(ChunkPopulateEvent event) {
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((WorldListener)l).onChunkPopulate(event);
                }
            }
        }
        @Override
        public void onSpawnChange(SpawnChangeEvent event) {
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((WorldListener)l).onSpawnChange(event);
                }
            }
        }
    };
    
    private CustomEventListener ourCustomEventHandler = new CustomEventListener() {
        @Override
        public void onCustomEvent(Event event) {
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((CustomEventListener)l).onCustomEvent(event);
                }
            }
        }
    };
    
    private EntityListener ourEntityEventHandler = new EntityListener() {
        @Override
        public void onEntityExplode(EntityExplodeEvent event) {
            if(event.isCancelled())
                return;
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((EntityListener)l).onEntityExplode(event);
                }
            }
        }
    };
    
    /**
     * Register event listener - this will be cleaned up properly on a /dynmap reload, unlike
     * registering with Bukkit directly
     */
    public void registerEvent(Event.Type type, Listener listener) {
        List<Listener> ll = event_handlers.get(type);
        if(ll == null) {
            switch(type) {  /* See if it is a type we're brokering */
                case PLAYER_LOGIN:
                case PLAYER_CHAT:
                case PLAYER_JOIN:
                case PLAYER_QUIT:
                case PLAYER_MOVE:
                case PLAYER_BED_LEAVE:
                    pm.registerEvent(type, ourPlayerEventHandler, Event.Priority.Monitor, plugin);
                    break;
                case BLOCK_PLACE:
                case BLOCK_BREAK:
                case LEAVES_DECAY:
                case BLOCK_BURN:
                case BLOCK_FORM:
                case BLOCK_FADE:
                case BLOCK_SPREAD:
                case BLOCK_FROMTO:
                case BLOCK_PHYSICS:
                case BLOCK_PISTON_EXTEND:
                case BLOCK_PISTON_RETRACT:
                    pm.registerEvent(type, ourBlockEventHandler, Event.Priority.Monitor, plugin);
                    break;
                case SIGN_CHANGE:
                    pm.registerEvent(type, ourBlockEventHandler, Event.Priority.Low, plugin);
                    break;
                case WORLD_LOAD:
                case WORLD_UNLOAD:
                case CHUNK_LOAD:
                case CHUNK_POPULATED:
                case SPAWN_CHANGE:
                    pm.registerEvent(type, ourWorldEventHandler, Event.Priority.Monitor, plugin);
                    break;
                case CUSTOM_EVENT:
                    pm.registerEvent(type, ourCustomEventHandler, Event.Priority.Monitor, plugin);
                    break;
                case ENTITY_EXPLODE:
                    pm.registerEvent(type, ourEntityEventHandler, Event.Priority.Monitor, plugin);
                    break;
                default:
                    Log.severe("registerEvent() in DynmapPlugin does not handle " + type);
                    return;
            }
            ll = new ArrayList<Listener>();
            event_handlers.put(type, ll);   /* Add list for this event */
        }
        ll.add(listener);
    }
}
