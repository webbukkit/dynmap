if (!console) console = { log: function() {} }; 

function splitArgs(s) {
	var r = s.split(' ');
	delete arguments[0];
	var obj = {};
	var index = 0;
	for(var argumentIndex in arguments) {
		var argument = arguments[argumentIndex];
		var value = r[argumentIndex-1];
		obj[argument] = value;
	}
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

function MinecraftClock(element) { this.element = element; }
MinecraftClock.prototype = {
	element: null,
	timeout: null,
	time: null,
	create: function(element) {
		if (!element) element = $('<div/>');
		this.element = element;
		return element;
	},
	setTime: function(time) {
		if (this.timeout != null) {
			window.clearTimeout(this.timeout);
			this.timeout = null;
		}
		this.time = time;
		this.element
			.addClass(time.day ? 'day' : 'night')
			.removeClass(time.night ? 'day' : 'night')
			.text(this.formatTime(time));
		
		if (this.timeout == null) {
			var me = this;
			this.timeout = window.setTimeout(function() {
				me.timeout = null;
				me.setTime(getMinecraftTime(me.time.servertime+(1000/60)));
			}, 700);
		}
	},
	formatTime: function(time) {
		var formatDigits = function(n, digits) {
			var s = n.toString();
			while (s.length < digits) {
				s = '0' + s;
			}
			return s;
		}
		return formatDigits(time.hours, 2) + ':' + formatDigits(time.minutes, 2);
	}
};

function DynMap(options) {
	this.options = options;
	this.initialize();
}
DynMap.prototype = {
	registeredTiles: new Array(),
	clock: null,
	markers: new Array(),
	lasttimestamp: '0',
	followingPlayer: '',
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
			backgroundColor: '#000'
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
					.attr('checked', me.options.defaultMap == name ? 'checked' : null)
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
		map.setMapTypeId(me.options.defaultMap);
		
		// The Player List
		var playerlist = me.playerlist = $('<div/>')
			.addClass('playerlist')
			.appendTo(sidebar);
		
		// The Clock
		var clock = me.clock = new MinecraftClock(
				$('<div/>')
					.addClass('clock')
					.appendTo(sidebar)
		);
		
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
		
		setTimeout(function() { me.update(); }, me.options.updateRate);
	},
	update: function() {
		var me = this;
		$.ajax({
			url: me.options.updateUrl + me.lasttimestamp,
			success: function(res) {
				if (!res) {
					me.alertbox
						.text('Invalid response')
						.show();
				}
			
				me.alertbox.hide();
				var rows = res.split('\n');
	 			var row = splitArgs(rows[0], 'timestamp', 'servertime');
				delete rows[0];
				
				me.lasttimestamp = row.timestamp;
				me.clock.setTime(getMinecraftTime(row.servertime));

				var typeVisibleMap = {};
				var newmarkers = {};
				
				for(var rowIndex in rows) {
					var line = rows[rowIndex];
					row = splitArgs(line, 'type', 'name', 'posx', 'posy', 'posz');

					if (!row.type) continue;
					
					swtch(row.type, {
						tile: function() {
							me.onTileUpdated(row.name);
						}
					}, function() {
						var mi = {
							id: row.type + '_' + row.name,
							text: row.name,
							type: row.type,
							position: me.map.getProjection().fromWorldToLatLng(parseFloat(row.posx), parseFloat(row.posy), parseFloat(row.posz)),
							visible: true
						};

						me.updateMarker(mi);
						newmarkers[mi.id] = mi;
					});
				}
	 
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
				setTimeout(function() { me.update(); }, me.options.updateRate);
			},
		error: function(request, statusText, ex) {
				me.alertbox
					.text('Could not update map')
					.show();
				setTimeout(function() { me.update(); }, me.options.updateRate);
			}
		});
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
					$(div)
						.addClass('Marker')
						.addClass('playerMarker')
						.append($('<span/>')
							.addClass('playerName')
							.text(mi.text));
					
					getMinecraftHead(mi.text, 32, function(head) {
						$(head)
							.addClass('playerIcon')
							.prependTo(div);
					});
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

				getMinecraftHead(mi.text, 16, function(head) {
					marker.playerRow.icon = $(head)
						.addClass('playerIcon')
						.appendTo(marker.playerIconContainer);
				});
				
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