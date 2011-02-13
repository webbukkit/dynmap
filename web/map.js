//if (!console) console = { log: function() {} }; 

var maptypes = {};

function splitArgs(s) {
	var r = s.split(' ');
	delete arguments[0];
	var obj = {};
	var index = 0;
	$.each(arguments, function(argumentIndex, argument) {
		if (!argumentIndex) return;
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
	}
};

function MinecraftTimeOfDay(element) { this.element = element; }
MinecraftTimeOfDay.prototype = {
	element: null,
	elementsun: null,
	elementmoon: null,
	create: function(element) {
		if (!element) element = $('<div/>');
		this.element = element;
		return element;
	},
	initialize: function(elementsun, elementmoon) {
		if (!elementsun) elementsun = $('<div/>');
		this.elementsun = elementsun;
		this.elementsun.appendTo(this.element);
		if (!elementmoon) elementmoon = $('<div/>');
		this.elementmoon = elementmoon;
		this.elementmoon.appendTo(this.elementsun);
		this.element.height(60);
		this.element.addClass('timeofday');
		this.elementsun.height(60);
		this.elementsun.addClass('timeofday');
		this.elementsun.addClass('sun');
		this.elementmoon.height(60);
		this.elementmoon.addClass('timeofday');
		this.elementmoon.addClass('moon');
		this.elementmoon.html("&nbsp;&rlm;&nbsp;");
		this.elementsun.css("background-position", (-120) + "px " + (-120) + "px");
		this.elementmoon.css("background-position", (-120) + "px " + (-120) + "px");
	},
	setTime: function(time) {
		var sunangle;
		
		if(time > 23100 || time < 12900)
		{
			//day mode
			var movedtime = time + 900;
			movedtime = (movedtime >= 24000) ? movedtime - 24000 : movedtime;
			//Now we have 0 -> 13800 for the day period
			//Devide by 13800*2=27600 instead of 24000 to compress day
		    sunangle = ((movedtime)/27600 * 2 * Math.PI);
		}
		else
		{
			//night mode
			var movedtime = time - 12900;
			//Now we have 0 -> 10200 for the night period
			//Devide by 10200*2=20400 instead of 24000 to expand night
		    sunangle = Math.PI + ((movedtime)/20400 * 2 * Math.PI);
		}
		
		var moonangle = sunangle + Math.PI;
		
		this.elementsun.css("background-position", (-50 * Math.cos(sunangle)) + "px " + (-50 * Math.sin(sunangle)) + "px");
		this.elementmoon.css("background-position", (-50 * Math.cos(moonangle)) + "px " + (-50 * Math.sin(moonangle)) + "px");
	}
};

function MinecraftCompass(element) { this.element = element; }
MinecraftCompass.prototype = {
	element: null,
	create: function(element) {
		if (!element) element = $('<div/>');
		this.element = element;
		return element;
	},
	initialize: function() {
		this.element.html("&nbsp;&rlm;&nbsp;");
		this.element.height(120);
	}
};

function DynMap(options) {
	var me = this;
	me.options = options;
	$.getJSON(me.options.updateUrl + 'configuration', function(configuration) {
		me.configure(configuration);
		me.initialize();
	})
}
DynMap.prototype = {
	registeredTiles: new Array(),
	markers: new Array(),
	chatPopups: new Array(),
	lasttimestamp: '0',
	followingPlayer: '',
	configure: function(configuration) {
		var me = this;
		$.extend(me.options, configuration);
		if (!me.options.maps) me.options.maps = {};
		$.each(me.options.shownmaps, function(index, mapentry) {
			me.options.maps[mapentry.name] = maptypes[mapentry.type](mapentry);
		});
	},
	initialize: function() {
		var me = this;
		
		var container = $(me.options.container);
		
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

		google.maps.event.addListener(map, 'dragstart', function(mEvent) {
			me.followPlayer('');
		});
		// TODO: Enable hash-links.
		/*google.maps.event.addListener(map, 'zoom_changed', function() {
			me.updateLink();
		});
		google.maps.event.addListener(map, 'center_changed', function() {
			me.updateLink();
		});*/

		// The sidebar
		var sidebar = me.sidebar = $('<div/>')
			.addClass('sidebar')
			.appendTo(container);
		
		// The map list.
		var maplist = me.maplist = $('<div/>')
			.addClass('maplist')
			.appendTo(sidebar);
			
		$.each(me.options.maps, function(name, mapType){
			mapType.dynmap = me;
			map.mapTypes.set(name, mapType);
			
			var mapButton;
			$('<div/>')
				.addClass('maprow')
				.append(mapButton = $('<input/>')
					.addClass('maptype_' + name)
					.attr({
						type: 'radio',
						name: 'map',
						id: 'maptypebutton_' + name 
					})
					.attr('checked', me.options.defaultmap == name ? 'checked' : null)
					)
				.append($('<label/>')
						.attr('for', 'maptypebutton_' + name)
						.text(name)
						)
				.click(function() {
						$('.mapbutton', maplist).removeAttr('checked');
						map.setMapTypeId(name);
						mapButton.attr('checked', 'checked');
					})
				.data('maptype', mapType)
				.appendTo(maplist);
		});
		map.setMapTypeId(me.options.defaultmap);
		
		// The Player List
		var playerlist = me.playerlist = $('<div/>')
			.addClass('playerlist')
			.appendTo(sidebar);
		
		// The TimeOfDay
		var timeofday = me.timeofday = new MinecraftTimeOfDay(
				$('<div/>')
				.appendTo(sidebar)
		);
		timeofday.initialize();
		
		// The Compass
		var compass = me.compass = new MinecraftCompass(
				$('<div/>')
					.addClass('compass')
					.appendTo(sidebar)
		);
		compass.initialize();
		
		// TODO: Enable hash-links.
		/*
		var link;
		var linkbox = me.linkbox = $('<div/>')
			.addClass('linkbox')
			.append(link=$('<input type="text" />'))
			.data('link', link)
			.appendTo(container);*/
		
		var alertbox = me.alertbox = $('<div/>')
			.addClass('alertbox')
			.appendTo(container);
		
		setTimeout(function() { me.update(); }, me.options.updaterate);
	},
	update: function() {
		var me = this;
		
		// TODO: is there a better place for this?
		this.cleanPopups();
		
		$.getJSON(me.options.updateUrl + me.lasttimestamp, function(update) {
				me.alertbox.hide();
			
				me.lasttimestamp = update.timestamp;
				me.timeofday.setTime(update.servertime);

				var typeVisibleMap = {};
				var newmarkers = {};
				
				$.each(update.players, function(index, player) {
					var mi = {
						id: 'player_' + player.name,
						text: player.name,
						type: 'player',
						position: me.map.getProjection().fromWorldToLatLng(parseFloat(player.x), parseFloat(player.y), parseFloat(player.z)),
						visible: true
					};

					me.updateMarker(mi);
					newmarkers[mi.id] = mi;
				});
				
				$.each(update.updates, function(index, update) {
					swtch(update.type, {
						tile: function() {
							me.onTileUpdated(update.name);
						},
						chat: function() {
						    if (!me.options.showchatballoons)
						    	return;
							me.onPlayerChat(update.playerName, update.message);
						}
					}, function(type) {
						console.log('Unknown type ', value, '!');
					});
				});
	 
				for(var m in me.markers) {
					var marker = me.markers[m];
					if(!(m in newmarkers)) {
						marker.remove(null);
						if (marker.playerRow) {
							marker.playerRow.remove();
						}
						delete me.markers[m];
					}
				}
				setTimeout(function() { me.update(); }, me.options.updaterate);
			}, function(request, statusText, ex) {
				me.alertbox
					.text('Could not update map')
					.show();
				setTimeout(function() { me.update(); }, me.options.updaterate);
			}
		);
	},
	cleanPopups: function() {
		var POPUP_LIFE = 8000;
		var d = new Date();
		var now = d.getTime();
		for (var popupIndex in this.chatPopups)
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
		var markers = me.markers;
		var chatPopups = this.chatPopups;
		var map = me.map;
		var mid = "player_" + playerName;
		var playerMarker = markers[mid];
		if (playerMarker)
		{
			var popup = chatPopups[playerName];
			if (!popup)
				popup = { lines: [ message ] };
			else
				popup.lines[popup.lines.length] = message;

			var MAX_LINES = 5;
			if (popup.lines.length > MAX_LINES)
			{
				popup.lines = popup.lines.slice(1);
			}
			htmlMessage = '<div id="content"><b>' + playerName + "</b><br/><br/>"
			for (var line in popup.lines)
			{
				htmlMessage = htmlMessage + popup.lines[line] + "<br/>";
			}
			htmlMessage = htmlMessage + "</div>"
			var now = new Date();
			popup.popupTime = now.getTime();
			if (!popup.infoWindow) {
				popup.infoWindow = new google.maps.InfoWindow({
					disableAutoPan: me.options.focuschatballoons || false,
				    content: htmlMessage
				});
			} else {
				popup.infoWindow.setContent(htmlMessage);
			}
			popup.infoWindow.open(map, playerMarker);
			this.chatPopups[playerName] = popup;
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
	updateMarker: function(mi) {
		var me = this;
		var markers = me.markers;
		var map = me.map;
		
		if(mi.id in markers) {
			var m = markers[mi.id];
			m.toggle(mi.visible);
			m.setPosition(mi.position);
		} else {
			var contentfun = function(div,mi) {
				$(div)
					.addClass('Marker')
					.addClass(mi.type + 'Marker')
					.append($('<img/>').attr({src: mi.type + '.png'}))
					.append($('<span/>').text(mi.text));
			};
			if (mi.type == 'player') {
				contentfun = function(div, mi) {
					var playerImage;
					$(div)
						.addClass('Marker')
						.addClass('playerMarker')
						.append(playerImage = $('<img/>')
								.attr({ src: 'player.png' }))
						.append($('<span/>')
							.addClass('playerName')
							.text(mi.text))
					if (me.options.showplayerfacesonmap) {
						getMinecraftHead(mi.text, 32, function(head) {
							$(head)
								.addClass('playerIcon')
								.prependTo(div);
							playerImage.remove();
						});
					}
				};
			}
			var marker = new CustomMarker(mi.position, map, contentfun, mi);
			marker.markerType = mi.type;
			
			markers[mi.id] = marker;

			if (mi.type == 'player') {
				marker.playerRow = $('<div/>')
					.attr({ id: 'playerrow_' + mi.text })
					.addClass('playerrow')
					.append(marker.followButton = $('<input/>')
						.attr({	type: 'checkbox',
							name: 'followPlayer',
							checked: false,
							value: mi.text
							})
						.addClass('followButton')
						.click(function(e) {
							me.followPlayer(mi.id != me.followingPlayer ? mi.id : '');
						}))
					.append(marker.playerIconContainer = $('<span/>'))
					.append($('<a/>')
						.text(mi.text)
						.attr({ href: '#' })
						.click(function(e) { map.panTo(markers[mi.id].getPosition()); })
						);

				if (me.options.showplayerfacesinmenu) {
					getMinecraftHead(mi.text, 16, function(head) {
						marker.playerRow.icon = $(head)
							.addClass('playerIcon')
							.appendTo(marker.playerIconContainer);
					});
				}
				
				me.playerlist.append(marker.playerRow);
			}
		}
		
		if(mi.id == me.followingPlayer) {
			map.panTo(markers[mi.id].getPosition());
		}
	},
	followPlayer: function(name) {
		var me = this;
		$('.followButton', me.playerlist).removeAttr('checked');
		
		var m = me.markers[name];
		if(m) {
			$(m.followButton).attr('checked', 'checked');
			me.map.panTo(m.getPosition());
		}
		this.followingPlayer = name;
	},
	getTileUrl: function(tileName, always) {
		var me = this;
		var tile = me.registeredTiles[tileName];
		
		if(tile) {
			return me.options.tileUrl + tileName + '.png?' + tile.lastseen;
		} else {
			return me.options.tileUrl + tileName + '.png?0';
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
}