"use strict";
//if (!console) console = { log: function() {} }; 

var maptypes = {};
var clocks = {};

function splitArgs(s) {
	var r = s.split(' ');
	delete arguments[0];
	var obj = {};
	var index = 0;
	$.each(arguments, function(argumentIndex, argument) {
		if (!argumentIndex) { return; }
		var value = r[argumentIndex-1];
		obj[argument] = value;
	});
	return obj;
}

function swtch(value, options, defaultOption) {
	return (options[value] || defaultOption)(value);
}

function DynMapType() { }
DynMapType.prototype = {
	onTileUpdated: function(tile, tileName) {
		var src = this.dynmap.getTileUrl(tileName);
		tile.attr('src', src);
		tile.show();
	},
	updateTileSize: function(zoom) {}
};

function Location(world, x, y, z) {
	this.world = world;
	this.x = x;
	this.y = y;
	this.z = z;
}

function DynMap(options) {
	var me = this;
	me.options = options;
	$.getJSON(me.options.updateUrl + 'configuration', function(configuration) {
		me.configure(configuration);
		me.initialize();
	});
}
DynMap.prototype = {
	worlds: {},
	registeredTiles: [],
	players: {},
	chatPopups: [],
	lasttimestamp: '0',
	followingPlayer: '',
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
				
				world.defaultmap = world.defaultmap || map;
			});
			me.defaultworld = me.defaultworld || world;
		});
	},
	initialize: function() {
		var me = this;
		
		var container = $(me.options.container);
		container.addClass('dynmap');
		
		var mapContainer;
		(mapContainer = $('<div/>'))
			.addClass('map')
			.appendTo(container);
		
		var map = this.map = new google.maps.Map(mapContainer.get(0), {
			zoom: 1,
			center: new google.maps.LatLng(0, 1),
			navigationControl: true,
			navigationControlOptions: {
				style: google.maps.NavigationControlStyle.DEFAULT
			},
			scaleControl: false,
			mapTypeControl: false,
			streetViewControl: false,
			backgroundColor: 'none'
		});
		
		map.zoom_changed = function() {
			me.maptype.updateTileSize(me.map.zoom);
		};

		google.maps.event.addListener(map, 'dragstart', function(mEvent) {
			me.followPlayer(null);
		});
		// TODO: Enable hash-links.
		/*google.maps.event.addListener(map, 'zoom_changed', function() {
			me.updateLink();
		});
		google.maps.event.addListener(map, 'center_changed', function() {
			me.updateLink();
		});*/

		// Sidebar
		var sidebar = me.sidebar = $('<div/>')
			.addClass('sidebar')
			.appendTo(container);
		
		var panel = $('<div/>')
			.addClass('panel')
			.appendTo(sidebar);
		
		// Pin button.
		var pinbutton = $('<div/>')
			.addClass('pin')
			.click(function() {
				sidebar.toggleClass('pinned');
			})
			.appendTo(panel);
		
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
			
			$.each(world.maps, function(index, map) {
				me.map.mapTypes.set(map.world.name + '.' + map.name, map);
				
				map.element = $('<li/>')
					.addClass('map')
					.append($('<a/>')
							.attr({ title: map.title, href: '#' })
							.addClass('maptype')
							.css({ backgroundImage: 'url(' + (map.icon || 'block_' + map.name + '.png') + ')' })
							.text(map.title)
							)
					.click(function() {
						me.selectMap(map);
					})
					.data('map', map)
					.appendTo(maplist);
			});
		});

		// The clock
		var largeclock = $('<div/>')
			.addClass('largeclock')
			.appendTo(container);
		var clock = me.clock = clocks['timeofday'](
			$('<div/>')
			.appendTo(largeclock)
		);
		var clockdigital = me.clockdigital = clocks['digital'](
			$('<div/>')
			.appendTo(largeclock)
		);
		
		// The Player List
		var playerlist;
		$('<fieldset/>')
			.append($('<legend/>').text('Players'))
			.append(me.playerlist = playerlist = $('<ul/>').addClass('playerlist'))
			.appendTo(panel);
		
		// The Compass
		var compass = $('<div/>')
			.addClass('compass')
			.appendTo(container);
		
		// The chat
		if (me.options.showchat === 'modal') {
			var chat = me.chat = $('<div/>')
				.addClass('chat')
				.appendTo(container);
			var messagelist = me.messagelist = $('<div/>')
				.addClass('messagelist')
				.appendTo(chat);
			var chatinput = me.chatinput = $('<input/>')
				.addClass('chatinput')
				.attr({
					id: 'chatinput',
					type: 'text',
					value: ''
				})
				.keydown(function(event) {
					if (event.keyCode == '13') {
						event.preventDefault();
						sendChat(chatinput.val());
						chatinput.val('');
					}
				})
				.appendTo(chat);
		}
		
		// TODO: Enable hash-links.
		/*
		var link;
		var linkbox = me.linkbox = $('<div/>')
			.addClass('linkbox')
			.append(link=$('<input type="text" />'))
			.data('link', link)
			.appendTo(container);*/
		
		$('<div/>')
			.addClass('hitbar')
			.appendTo(panel);
		
		var alertbox = me.alertbox = $('<div/>')
			.addClass('alertbox')
			.appendTo(container);
		
		me.selectMap(me.defaultworld.defaultmap);
		
		setTimeout(function() { me.update(); }, me.options.updaterate);
	},
	selectMap: function(map, completed) {
		if (!map) { throw "Cannot select map " + map; }
		var me = this;
		me.map.setMapTypeId('none');
		me.world = map.world;
		me.maptype = map;
		me.maptype.updateTileSize(me.map.zoom);
		window.setTimeout(function() {
			me.map.setMapTypeId(map.world.name + '.' + map.name);
			if (completed) { completed(); }
		}, 1);
		$('.map', me.worldlist).removeClass('selected');
		$(map.element).addClass('selected');
	},
	selectWorld: function(world, completed) {
		var me = this;
		if (typeof(world) === 'String') { world = me.worlds[world]; }
		if (me.world === world) {
			if (completed) { completed(); }
			return;
		}
		me.selectMap(world.defaultmap, completed);
	},
	panTo: function(location, completed) {
		var me = this;
		me.selectWorld(location.world, function() {
			var position = me.map.getProjection().fromWorldToLatLng(location.x, location.y, location.z);
			me.map.panTo(position);
		});
	},
	update: function() {
		var me = this;
		
		// TODO: is there a better place for this?
		this.cleanPopups();
		
		$.getJSON(me.options.updateUrl + "world/" + me.world.name + "/" + me.lasttimestamp, function(update) {
				me.alertbox.hide();
			
				if (!me.options.jsonfile)
					me.lasttimestamp = update.timestamp;
				
				me.clock.setTime(update.servertime);
				me.clockdigital.setTime(update.servertime);

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
					swtch(update.type, {
						tile: function() {
							
							if(me.lasttimestamp <= update.timestamp || !me.options.jsonfile)
								me.onTileUpdated(update.name);
						},
						chat: function() {
							if (!me.options.showchat) {
								return;
							}
							if(me.lasttimestamp <= update.timestamp  || !me.options.jsonfile)
								me.onPlayerChat(update.playerName, update.message);
						},
						webchat: function() {
							if (!me.options.showchat) {
								return;
							}
							
							if(me.lasttimestamp <= update.timestamp  || !me.options.jsonfile)
								me.onPlayerChat('[WEB]' + update.playerName, update.message);
						}
					}, function(type) {
						console.log('Unknown type ', type, '!');
					});
					/* remove older messages from chat*/
					//var timestamp = event.timeStamp;
					//var divs = $('div[rel]');
					//divs.filter(function(i){return parseInt(divs[i].attr('rel')) > timestamp+me.options.messagettl;}).remove();
				});
				setTimeout(function() { me.update(); }, me.options.updaterate);
			}, function(request, statusText, ex) {
				me.alertbox
					.text('Could not update map')
					.show();
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
			return me.options.tileUrl + me.world.name + '/' + tileName + '?0';
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
	cleanPopups: function() {
		var POPUP_LIFE = 8000;
		var d = new Date();
		var now = d.getTime();
		var popupIndex;
		for (popupIndex in this.chatPopups)
		{
			var popup = this.chatPopups[popupIndex];
			if (now - popup.popupTime > POPUP_LIFE)
			{
				popup.infoWindow.close();
				popup.infoWindow = null;
				delete this.chatPopups[popupIndex];
			}
		}
	},
	onPlayerChat: function(playerName, message) {
		var me = this;
		var chatPopups = this.chatPopups;
		var map = me.map;
		var player = me.players[playerName];
		var playerMarker = player && player.marker;
		if (me.options.showchat === 'balloons') {
			if (playerMarker)
			{
				var popup = chatPopups[playerName];
				if (!popup) {
					popup = { lines: [ message ] };
				} else {
					popup.lines[popup.lines.length] = message;
				}
	
				var MAX_LINES = 5;
				if (popup.lines.length > MAX_LINES)
				{
					popup.lines = popup.lines.slice(1);
				}
				var htmlMessage = '<div id="content"><b>' + playerName + "</b><br/><br/>";
				var line;
				for (line in popup.lines)
				{
					htmlMessage = htmlMessage + popup.lines[line] + "<br/>";
				}
				htmlMessage = htmlMessage + "</div>";
				var now = new Date();
				popup.popupTime = now.getTime();
				if (!popup.infoWindow) {
					popup.infoWindow = new google.maps.InfoWindow({
						disableAutoPan: !(me.options.focuschatballoons || false),
					    content: htmlMessage
					});
				} else {
					popup.infoWindow.setContent(htmlMessage);
				}
				popup.infoWindow.open(map, playerMarker);
				this.chatPopups[playerName] = popup;
			}
		} else if (me.options.showchat === 'modal') {
			var messagelist = me.messagelist;

			var messageRow = $('<div/>')
				.addClass('messagerow');

			var playerIconContainer = $('<span/>')
				.addClass('messageicon');

			if (me.options.showplayerfacesinmenu) {
				getMinecraftHead(playerName, 16, function(head) {
					messageRow.icon = $(head)
						.addClass('playerIcon')
						.appendTo(playerIconContainer);
				});
			}

			if (playerName !== 'Server') {
				var playerWorldContainer = $('<span/>')
				 .addClass('messagetext')
				 .text('['+me.world+']');
	
				var playerGroupContainer = $('<span/>')
				 .addClass('messagetext')
				 .text('[Group]');
			}

			var playerNameContainer = $('<span/>')
				.addClass('messagetext')
				.text(' '+playerName+': ');

			var playerMessageContainer = $('<span/>')
				.addClass('messagetext')
				.text(message);

			messageRow.append(playerIconContainer,playerNameContainer,playerMessageContainer);
			//messageRow.append(playerIconContainer,playerWorldContainer,playerGroupContainer,playerNameContainer,playerMessageContainer);
			setTimeout(function() { messageRow.remove(); }, me.options.messagettl);
			messagelist.append(messageRow);
			
			me.messagelist.show();
			//var scrollHeight = jQuery(me.messagelist).attr('scrollHeight');
			var scrollHeight = me.messagelist[0].scrollHeight;
			messagelist.scrollTop(scrollHeight);
		}
	},
	onTileUpdated: function(tileName) {
		var me = this;
		var tile = this.registeredTiles[tileName];
		
		if (tile) {
			tile.lastseen = this.lasttimestamp;
			tile.mapType.onTileUpdated(tile.tileElement, tileName);
		}
	},
	addPlayer: function(update) {
		var me = this;
		var player = me.players[update.name] = {
				name: update.name,
				location: new Location(me.worlds[update.world], parseFloat(update.x), parseFloat(update.y), parseFloat(update.z))
		};
		var location = player.location;
		// Create the player-marker.
		var markerPosition = me.map.getProjection().fromWorldToLatLng(location.x, location.y, location.z);
		player.marker = new CustomMarker(markerPosition, me.map, function(div) {
			var playerImage;
			$(div)
				.addClass('Marker')
				.addClass('playerMarker')
				.append(playerImage = $('<img/>')
						.attr({ src: 'player.png' }))
				.append($('<span/>')
					.addClass('playerName')
					.text(player.name));
			
			if (me.options.showplayerfacesonmap) {
				getMinecraftHead(player.name, 32, function(head) {
					$(head)
						.addClass('playericon')
						.prependTo(div);
					playerImage.remove();
				});
			}
		});
		
		// Create the player-menu-item.
		var playerIconContainer;
		var menuitem = player.menuitem = $('<li/>')
			.addClass('player')
			.append(playerIconContainer = $('<span/>')
					.addClass('playerIcon')
					.append($('<img/>').attr({ src: 'player_face.png' }))
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
			.click(function(e) { me.panTo(player.location); })
			.appendTo(me.playerlist);
		if (me.options.showplayerfacesinmenu) {
			getMinecraftHead(player.name, 16, function(head) {
				$('img', playerIconContainer).remove();
				$(head).appendTo(playerIconContainer);
			});
		}
	},
	updatePlayer: function(player, update) {
		var me = this;
		var location = player.location = new Location(me.worlds[update.world], parseFloat(update.x), parseFloat(update.y), parseFloat(update.z));
		
		// Update the marker.
		var markerPosition = me.map.getProjection().fromWorldToLatLng(location.x, location.y, location.z);
		player.marker.toggle(me.world === location.world);
		player.marker.setPosition(markerPosition);
		
		// Update menuitem.
		player.menuitem.toggleClass('otherworld', me.world !== location.world);
		
		if (player === me.followingPlayer) {
			// Follow the updated player.
			me.panTo(player.location);
		}
	},
	removePlayer: function(player) {
		var me = this;
		
		delete me.players[player.name];
		
		// Remove the marker.
		player.marker.remove();
		
		// Remove menu item.
		player.menuitem.remove();
	},
	followPlayer: function(player) {
		var me = this;
		$('.following', me.playerlist).removeClass('following');
		
		if(player) {
			$(player.menuitem).addClass('following');
			me.panTo(player.location);
		}
		this.followingPlayer = player;
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
