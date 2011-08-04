"use strict";
//if (!console) console = { log: function() {} }; 

var componentconstructors = {};
var maptypes = {};

componentconstructors['testcomponent'] = function(dynmap, configuration) {
	console.log('initialize');
	$(dynmap).bind('worldchanged', function() { console.log('worldchanged'); });
	$(dynmap).bind('mapchanging', function() { console.log('mapchanging'); });
	$(dynmap).bind('mapchanged', function() { console.log('mapchanged'); });
	$(dynmap).bind('zoomchanged', function() { console.log('zoomchanged'); });
	$(dynmap).bind('worldupdating', function() { console.log('worldupdating'); });
	$(dynmap).bind('worldupdate', function() { console.log('worldupdate'); });
	$(dynmap).bind('worldupdated', function() { console.log('worldupdated'); });
	$(dynmap).bind('worldupdatefailed', function() { console.log('worldupdatefailed'); });
	$(dynmap).bind('playeradded', function() { console.log('playeradded'); });
	$(dynmap).bind('playerremoved', function() { console.log('playerremoved'); });
	$(dynmap).bind('playerupdated', function() { console.log('playerupdated'); });
};

function DynMap(options) {
	var me = this;
	me.options = options;
	$.getJSON(me.options.url.configuration, function(configuration) {
		me.configure(configuration);
		me.initialize();
	}, function(status, statusMessage) {
		alert('Could not retrieve configuration: ' + statusMessage);
	});
}
DynMap.prototype = {
	components: [],
	worlds: {},
	registeredTiles: [],
	players: {},
	lasttimestamp: new Date().getUTCMilliseconds(), /* Pseudorandom - prevent cached '?0' */
    servertime: 0,
    serverday: false,
    inittime: new Date().getTime(),
	followingPlayer: '',
	missedupdates: 0,
	canvassupport: !!document.createElement('canvas').getContext,
	formatUrl: function(name, options) {
		var url = this.options.url[name];
		$.each(options, function(n,v) {
			url = url.replace("{" + n + "}", v);
		});
		return url;
	},
	configure: function(configuration) {
		var me = this;
		$.extend(me.options, configuration);
		
		$.each(me.options.worlds, function(index, worldentry) {
			var world = me.worlds[worldentry.name] = $.extend({}, worldentry, {
				maps: {}
			});
			
			$.each(worldentry.maps, function(index, mapentry) {
				var map = $.extend({}, mapentry, {
					world: world,
					dynmap: me
				});
				map = world.maps[mapentry.name] = maptypes[mapentry.type](map);
				if(me.options.defaultmap && me.options.defaultmap == mapentry.name)
					world.defaultmap = map;				
				world.defaultmap = world.defaultmap || map;
			});
			me.defaultworld = me.defaultworld || world;
		});
		var urlarg = me.getParameterByName('worldname');
		if(urlarg != "") {
		    me.defaultworld = me.worlds[urlarg] || me.defaultworld;
		}
		urlarg = me.getParameterByName('mapname');
		if(urlarg != "") {
			me.defaultworld.defaultmap = me.defaultworld.maps[urlarg] || me.defaultworld.defaultmap;
		}
		urlarg = me.getIntParameterByName('x');
		if(urlarg != null)
			me.defaultworld.center.x = urlarg;
		urlarg = me.getIntParameterByName('y');
		if(urlarg != null)
			me.defaultworld.center.y = urlarg;
		urlarg = me.getIntParameterByName('z');
		if(urlarg != null)
			me.defaultworld.center.z = urlarg;
	},
	initialize: function() {
		var me = this;
		
		var container = $(me.options.container);
		container.addClass('dynmap');
		
		var mapContainer;
		(mapContainer = $('<div/>'))
			.addClass('map')
			.appendTo(container);

		var urlzoom = me.getIntParameterByName('zoom');
		if(urlzoom != null)
			me.options.defaultzoom = urlzoom;
			
		if(typeof me.options.defaultzoom == 'undefined')
			me.options.defaultzoom = 1;
		
		var map = this.map = new L.Map(mapContainer.get(0), {
			zoom: me.options.defaultzoom,
			center: new L.LatLng(0, 0),
			zoomAnimation: true,
			attributionControl: false,
			crs: L.Util.extend({}, L.CRS, {
				code: 'simple',
				projection: {
						project: function(latlng) {
							return new L.Point(latlng.lat, latlng.lng);
						},
						unproject: function(point, unbounded) {
							return new L.LatLng(point.x, point.y, true);
						}
					},
				transformation: new L.Transformation(1, 0, 1, 0)
			}),
			scale: function(zoom) {
				return (1 << zoom);
			}
		});
		
		map.zoom_changed = function() {
			me.maptype.updateTileSize(me.map.zoom);
			$(me).trigger('zoomchanged');
		};
		
		if(me.canvassupport == false)
			me.options.showplayerfacesinmenu = false;
			
		/*google.maps.event.addListener(map, 'dragstart', function(mEvent) {
			me.followPlayer(null);
		});*/

		// Sidebar
		var panel;
		var sidebar;
		var pinbutton;
		if(!me.options.sidebaropened) {
			sidebar = me.sidebar = $('<div/>')
				.addClass('sidebar')
				.appendTo(container);

			panel = $('<div/>')
				.addClass('panel')
				.appendTo(sidebar);
		
			// Pin button.
			pinbutton = $('<div/>')
				.addClass('pin')
				.click(function() {
					sidebar.toggleClass('pinned');
				})
				.appendTo(panel);
		}
		else {
			sidebar = me.sidebar = $('<div/>')
				.addClass('sidebar pinned')
				.appendTo(container);

			panel = $('<div/>')
				.addClass('panel')
				.appendTo(sidebar);
		}
        
		// Worlds
		var worldlist;
		$('<fieldset/>')
			.append($('<legend/>').text('Map Types'))
			.append(me.worldlist = worldlist = $('<ul/>').addClass('worldlist'))
			.appendTo(panel);
		
		$.each(me.worlds, function(index, world) {
			var maplist; 
			world.element = $('<li/>')
				.addClass('world')
				.text(world.title)
				.append(maplist = $('<ul/>')
						.addClass('maplist')
						)
				.data('world', world)
				.appendTo(worldlist);
			
			$.each(world.maps, function(mapindex, map) {
				//me.map.mapTypes.set(map.world.name + '.' + map.name, map);
				
				map.element = $('<li/>')
					.addClass('map')
					.append($('<a/>')
							.attr({ title: map.options.title, href: '#' })
							.addClass('maptype')
							.css({ backgroundImage: 'url(' + (map.options.icon || ('images/block_' + mapindex + '.png')) + ')' })
							.text(map.options.title)
							)
					.click(function() {
						me.selectMap(map);
					})
					.data('map', map)
					.appendTo(maplist);
			});
		});
		
		// The scrollbuttons
		// we need to show/hide them depending: if (me.playerlist.scrollHeight() > me.playerlist.innerHeight()) or something.
		var upbtn = $('<div/>')
		.addClass('scrollup')
		.bind('mousedown mouseup', function(event){ 
		    if(event.type == 'mousedown'){
				playerlist.animate({"scrollTop": "-=300px"}, 3000, 'linear');
		    }else{
		        playerlist.stop(); 
		    }
		});
		var downbtn = $('<div/>')
		.addClass('scrolldown')
		.bind('mousedown mouseup', function(event){ 
		    if(event.type == 'mousedown'){ 
				playerlist.animate({"scrollTop": "+=300px"}, 3000, 'linear');
		    }else{ 
		        playerlist.stop(); 
		    }
		});
		
		// The Player List
		var playerlist;
		$('<fieldset/>')
			.append($('<legend/>').text('Players'))
			.append(upbtn)
			.append(me.playerlist = playerlist = $('<ul/>').addClass('playerlist')
				.bind('mousewheel', function(event, delta){ 
					this.scrollTop -= (delta * 10);
					event.preventDefault();
				})
			)
			.append(downbtn)
			.appendTo(panel);
		
		var updateHeight = function() {
			playerlist.height(sidebar.innerHeight() - (playerlist.offset().top - worldlist.offset().top) - 64); // here we need a fix to avoid the static value, but it works fine this way :P
			var scrollable = playerlist.scrollHeight() < playerlist.height();
			upbtn.toggle(scrollable);
			downbtn.toggle(scrollable);
		};
		updateHeight();
		$(window).resize(updateHeight);
		$(dynmap).bind('playeradded', function() {
			updateHeight();
		});
		$(dynmap).bind('playerremoved', function() {
			updateHeight();
		});
		// The Compass
		var compass = $('<div/>')
			.addClass('compass')
			.appendTo(container);
		
		// TODO: Enable hash-links.
		/*
		var link;
		var linkbox = me.linkbox = $('<div/>')
			.addClass('linkbox')
			.append(link=$('<input type="text" />'))
			.data('link', link)
			.appendTo(container);*/
        if(!me.options.sidebaropened) {
            $('<div/>')
                .addClass('hitbar')
                .appendTo(panel);
		}
        
		var alertbox = me.alertbox = $('<div/>')
			.addClass('alertbox')
			.hide()
			.appendTo(container);
		
		me.selectMap(me.defaultworld.defaultmap);
		
		var componentstoload = me.options.components.length;
		$.each(me.options.components, function(index, configuration) {
			loadjs('js/' + configuration.type + '.js', function() {
				var componentconstructor = componentconstructors[configuration.type];
				if (componentconstructor) {
					me.components.push(new componentconstructor(me, configuration));
				} else {
					// Could not load component. We'll ignore this for the moment.
				}
				componentstoload--;
				if (componentstoload == 0) {
					// Actually start updating once all components are loaded.
					setTimeout(function() { me.update(); }, me.options.updaterate);
				}
			});
		});
	},
	getProjection: function() { return this.maptype.getProjection(); },
	selectMapAndPan: function(map, location, completed) {
		if (!map) { throw "Cannot select map " + map; }
		var me = this;
		
		if (me.maptype === map) {
			return;
		}
		$(me).trigger('mapchanging');
		var mapWorld = map.options.world;
		if (me.maptype) {
			$('.compass').removeClass('compass_' + me.maptype.options.compassview);
			$('.compass').removeClass('compass_' + me.maptype.options.name);
		}
		$('.compass').addClass('compass_' + map.options.compassview);
		$('.compass').addClass('compass_' + map.options.name);
		var worldChanged = me.world !== map.options.world;
		var projectionChanged = (me.maptype && me.maptype.getProjection()) !== (map && map.projection);

		var prevzoom = me.map.getZoom(); 					

		if (me.maptype) {
			me.map.removeLayer(me.maptype);
		}
		
		var prevmap = me.maptype;
	
		me.world = mapWorld;
		me.maptype = map;

		if(me.maptype.options.maxZoom < prevzoom)
			prevzoom = me.maptype.options.maxZoom;
		me.map.options.maxZoom = me.maptype.options.maxZoom;
		me.map.options.minZoom = me.maptype.options.minZoom;
				
		if (projectionChanged || worldChanged || location) {
			var centerPoint;
			if(location) {
				centerPoint = me.getProjection().fromLocationToLatLng(location);
			}
			else if(worldChanged) {
				var centerLocation = $.extend({ x: 0, y: 64, z: 0 }, mapWorld.center);
				centerPoint = me.getProjection().fromLocationToLatLng(centerLocation);
			}
			else {
				var prevloc = null;
				if(prevmap != null)
					prevloc = prevmap.getProjection().fromLatLngToLocation(me.map.getCenter(), 64);
				if(prevloc != null)
					centerPoint = me.getProjection().fromLocationToLatLng(prevloc);
				else
					centerPoint = me.map.getCenter();
			}
			me.map.setView(centerPoint, prevzoom, true);
		}
		else {			
			me.map.setZoom(prevzoom);
		}
		me.map.addLayer(me.maptype);
				
		if (worldChanged) {
			$(me).trigger('worldchanged');
		}
		$(me).trigger('mapchanged');

		$('.map', me.worldlist).removeClass('selected');
		$(map.element).addClass('selected');
		me.updateBackground();
		
		
		if (completed) {
			completed();
		}
	},
	selectMap: function(map, completed) {
		this.selectMapAndPan(map, null, completed);
	},
	selectWorldAndPan: function(world, location, completed) {
		var me = this;
		if (typeof(world) === 'String') { world = me.worlds[world]; }
		if (me.world === world) {
			if(location) {
				var latlng = me.maptype.getProjection().fromLocationToLatLng(location);
				me.panToLatLng(latlng, completed);
			}
			else {
				if (completed) { completed(); }
			}
			return;
		}
		me.selectMapAndPan(world.defaultmap, location, completed);
	},
	selectWorld: function(world, completed) {
		this.selectWorldAndPan(world, null, completed);
	},
	panToLocation: function(location, completed) {
		var me = this;
		
		if (location.world) {
			me.selectWorldAndPan(location.world, location, function() {
				if(completed) completed();
			});
		} else {
			var latlng = me.maptype.getProjection().fromLocationToLatLng(location);
			me.panToLatLng(latlng, completed);
		}
	},
	panToLayerPoint: function(point, completed) {
		var me = this;
		var latlng = me.map.layerPointToLatLng(point);
		me.map.panToLatLng(latlng);
		if (completed) {
			completed();
		}
	},
	panToLatLng: function(latlng, completed) {
		this.map.panTo(latlng);
		if (completed) {
			completed();
		}
	},
	update: function() {
		var me = this;

		$(me).trigger('worldupdating');
		$.getJSON(me.formatUrl('update', { world: me.world.name, timestamp: me.lasttimestamp }), function(update) {
				if (!update) {
					setTimeout(function() { me.update(); }, me.options.updaterate);
					return;
				}
				me.alertbox.hide();
				
				if (!me.options.jsonfile) {
					me.lasttimestamp = update.timestamp;
				}

				me.servertime = update.servertime;                
				var newserverday = (me.servertime > 23100 || me.servertime < 12900);
				if(me.serverday != newserverday) {
					me.serverday = newserverday;
					
					me.updateBackground();				
					if(me.maptype.options.nightandday) {
						// Readd map.
						me.map.removeLayer(me.maptype);
						me.map.addLayer(me.maptype);
					}
				}
                    
				var newplayers = {};
				$.each(update.players, function(index, playerUpdate) {
					var name = playerUpdate.name;
					var player = me.players[name];
					if (player) {
						me.updatePlayer(player, playerUpdate);
					} else {
						me.addPlayer(playerUpdate);
					}
					newplayers[name] = player;
				});
				var name;
				for(name in me.players) {
					var player = me.players[name];
					if(!(name in newplayers)) {
						me.removePlayer(player);
					}
				}
				
				$.each(update.updates, function(index, update) {
					// Only handle updates that are actually new.
					if(!me.options.jsonfile || me.lasttimestamp <= update.timestamp) {
						$(me).trigger('worldupdate', [ update ]);
						
						swtch(update.type, {
							tile: function() {
								me.onTileUpdated(update.name,update.timestamp);
							},
							playerjoin: function() {
								$(me).trigger('playerjoin', [ update.playerName ]);
							},
							playerquit: function() {
								$(me).trigger('playerquit', [ update.playerName ]);
							}
						});
					}
					/* remove older messages from chat*/
					//var timestamp = event.timeStamp;
					//var divs = $('div[rel]');
					//divs.filter(function(i){return parseInt(divs[i].attr('rel')) > timestamp+me.options.messagettl;}).remove();
				});
				
				$(me).trigger('worldupdated', [ update ]);
				
				me.lasttimestamp = update.timestamp;
				me.missedupdates = 0;
				setTimeout(function() { me.update(); }, me.options.updaterate);
			}, function(status, statusText, request) {
				me.missedupdates++;
				if(me.missedupdates > 2) {
					me.alertbox
						.text('Could not update map: ' + (statusText || 'Could not connect to server'))
						.show();
					$(me).trigger('worldupdatefailed');
				}
				setTimeout(function() { me.update(); }, me.options.updaterate);
			}
		);
	},
	getTileUrl: function(tileName, always) {
		var me = this;
		var tile = me.registeredTiles[tileName];
		
		if(tile) {
			return me.options.tileUrl + me.world.name + '/' + tileName + '?' + tile.lastseen;
		} else {
			return me.options.tileUrl + me.world.name + '/' + tileName + '?' + me.inittime; /* Browser cache fix on reload */
		}
	},
	registerTile: function(mapType, tileName, tile) {
		this.registeredTiles[tileName] = {
				tileElement: tile,
				mapType: mapType,
				lastseen: '0'
		};
	},
	unregisterTile: function(mapType, tileName) {
		delete this.registeredTiles[tileName];
	},
	onTileUpdated: function(tileName,timestamp) {
		var me = this;
		var tile = this.registeredTiles[tileName];
		
		if (tile) {
			tile.lastseen = timestamp;
			tile.mapType.onTileUpdated(tile.tileElement, tileName);
		}
		me.maptype.updateNamedTile(tileName);
	},
	addPlayer: function(update) {
		var me = this;
		var player = me.players[update.name] = {
				name: update.name,
				location: new Location(me.worlds[update.world], parseFloat(update.x), parseFloat(update.y), parseFloat(update.z)),
				health: update.health,
				armor: update.armor,
				account: update.account
		};
		
		$(me).trigger('playeradded', [ player ]);
		
		// Create the player-menu-item.
		var playerIconContainer;
		var menuitem = player.menuitem = $('<li/>')
			.addClass('player')
			.append(playerIconContainer = $('<span/>')
					.addClass('playerIcon')
					.append($('<img/>').attr({ src: 'images/player_face.png' }))
					.attr({ title: 'Follow ' + player.name })
					.click(function() {
						var follow = player !== me.followingPlayer;
						me.followPlayer(follow ? player : null);
					})
					)
			.append($('<a/>')
					.attr({
						href: '#',
						title: 'Center on ' + player.name
						})
					.text(player.name)
					)
			.click(function(e) {
				if (me.followingPlayer !== player) {
					me.followPlayer(null);
				}
				me.panToLocation(player.location);
			})
			.appendTo(me.playerlist);
		if (me.options.showplayerfacesinmenu) {
			getMinecraftHead(player.account, 16, function(head) {
				$('img', playerIconContainer).remove();
				$(head).appendTo(playerIconContainer);
			});
		}
	},
	updatePlayer: function(player, update) {
		var me = this;
		var location = player.location = new Location(me.worlds[update.world], parseFloat(update.x), parseFloat(update.y), parseFloat(update.z));
		player.health = update.health;
		player.armor = update.armor;
		
		$(me).trigger('playerupdated', [ player ]);
		
		// Update menuitem.
		player.menuitem.toggleClass('otherworld', me.world !== location.world);
		
		if (player === me.followingPlayer) {
			// Follow the updated player.
			me.panToLocation(player.location);
		}
	},
	removePlayer: function(player) {
		var me = this;
		
		delete me.players[player.name];
		
		$(me).trigger('playerremoved', [ player ]);
		
		// Remove menu item.
		player.menuitem.remove();
	},
	followPlayer: function(player) {
		var me = this;
		$('.following', me.playerlist).removeClass('following');
		
		if(player) {
			$(player.menuitem).addClass('following');
			me.panToLocation(player.location);
		}
		this.followingPlayer = player;
	},
	updateBackground: function() {
		var me = this;
		var col = "#000000";
		if(me.serverday) {
			if(me.maptype.options.backgroundday)
				col = me.maptype.options.backgroundday;
			else if(me.maptype.options.background)
				col = me.maptype.options.background;
		}
		else {
			if(me.maptype.options.backgroundnight)
				col = me.maptype.options.backgroundnight;
			else if(me.maptype.options.background)
				col = me.maptype.options.background;
		}
		$('.map').css('background', col);
		$('.leaflet-tile').css('background', col);
	},
	getParameterByName: function(name) {
		name = name.replace(/[\[]/,"\\\[").replace(/[\]]/,"\\\]");
		var regexS = "[\\?&]"+name+"=([^&#]*)";
		var regex = new RegExp( regexS );
		var results = regex.exec( window.location.href );
		if( results == null )
			return "";
		else
			return decodeURIComponent(results[1].replace(/\+/g, " "));
	},
	getIntParameterByName: function(name) {
		var v = this.getParameterByName(name);
		if(v != "") {
			v = parseInt(v, 10);
			if(v != NaN) {
				return v;
				}
		}
		return null;
	}
	// TODO: Enable hash-links.
/*	updateLink: function() {
		var me = this;
		var url = location.href.match(/^[^#]+/);
		
		var a=url
			+ "#lat=" + me.map.getCenter().lat().toFixed(6)
			+ "&lng=" + me.map.getCenter().lng().toFixed(6)
			+ "&zoom=" + me.map.getZoom();
			me.linkbox.data('link').val(a);
	}*/
};
