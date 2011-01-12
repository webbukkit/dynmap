if (!console) console = { log: function() {} }; 

function DynMapType() { }
DynMapType.prototype = {
	onTileUpdated: function(tile, tileName) {
		var src = getTileUrl(tileName);
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
			}, 700 /* should be 600, but since server-resyncing it looks better with 700 */);
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

var tileDict = new Array();
var lastSeen = new Array();

function getTileUrl(tile, always) {
	if(always) {
		var now = new Date();
		return config.tileUrl + tile + '.png?' + now.getTime();
	} else if(tile in lastSeen) {
		return config.tileUrl + tile + '.png?' + lastSeen[tile];
	} else {
		return config.tileUrl + tile + '.png?0';
	}
}

function registerTile(mapType, tileName, tile) {
	tileDict[tileName] = {
			tileElement: tile,
			mapType: mapType
	};
}

function unregisterTile(mapType, tileName) {
	delete tileDict[tileName];
}

function onTileUpdated(tileName) {
	lastSeen[tileName] = lasttimestamp;
	
	var tile = tileDict[tileName];
	if (tile) {
		tile.mapType.onTileUpdated(tile.tileElement, tileName);
	}
}

var clock = null;
	var markers = new Array();
	var lasttimestamp = '0';
	var followPlayer = '';

	var lst;
	var plistbtn;
	var lstopen = true;

	function updateMarker(mi) {
		if(mi.id in markers) {
			var m = markers[mi.id];
			if (!mi.visible) {
				m.hide();
				return;
			}
			else {
				m.show();
			}
			
			var converted = mi.position;
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
			var marker = new CustomMarker(converted, map, contentfun, mi);
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
							plfollow(mi.id != followPlayer ? mi.id : '');
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
				
				$('#playerlst').append(marker.playerRow);
			}
		}
		
		if(mi.id == followPlayer) {
			map.panTo(markers[mi.id].getPosition());
		}
	}

	function mapUpdate()
	{
		$.ajax({
			url: config.updateUrl + lasttimestamp,
			success: function(res) {
				$('#alert')
					.hide();
				var typeVisibleMap = {
					'warp': document.getElementById('showWarps').checked,
					'sign': document.getElementById('showSigns').checked,
					'home': document.getElementById('showHomes').checked,
					'spawn': document.getElementById('showSpawn').checked
				};
				
				var typeCount = {};
				
				var rows = res.split('\n');
				var loggedin = new Array();
	 			var firstRow = rows[0].split(' ');
				lasttimestamp = firstRow[0];
				delete rows[0];
				
				var servertime = firstRow[1];
				clock.setTime(getMinecraftTime(servertime));
				
				for(var line in rows) {
					var p = rows[line].split(' ');
	
					if (p[0] == '') continue;
					
					({	tile: function() {
							onTileUpdated(p[1]);
						}
					}[p[0]] || function() {
						var mi = {
							id: p[0] + '_' + p[1],
							text: p[1],
							type: p[0],
							position: map.getProjection().fromWorldToLatLng(p[2], p[3], p[4]),
							visible: ((p[0] in typeVisibleMap) ? typeVisibleMap[p[0]] : true)
						};
	
						updateMarker(mi);
						loggedin[mi.id] = 1;
						if (!mi.type in typeCount) typeCount[mi.type] = 0;
						typeCount[mi.type]++;
					})();
				}
	 
				for(var m in markers) {
					if(!(m in loggedin)) {
						markers[m].remove(null);
						if (markers[m].playerRow) {
							markers[m].playerRow.remove();
						}
						delete markers[m];
					}
				}
				setTimeout(mapUpdate, config.updateRate);
				document.getElementById('warpsDiv').style.display = (typeCount['warps'] == 0)?'none':'';
				document.getElementById('signsDiv').style.display = (typeCount['signs'] == 0)?'none':'';
				document.getElementById('homesDiv').style.display = (typeCount['homes'] == 0)?'none':'';
				document.getElementById('spawnsDiv').style.display = (typeCount['spawns'] == 0)?'none':'';
			},
		error: function(request, statusText, ex) {
				$('#alert')
					.text('Could not update map')
					.show();
				setTimeout(mapUpdate, config.updateRate);
			}
		});
	}
	
	window.onload = function initialize() {
		lst = document.getElementById('lst');
		plistbtn = document.getElementById('plistbtn');

		var mapOptions = {
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
		};
		map = new google.maps.Map(document.getElementById("mcmap"), mapOptions);

		google.maps.event.addListener(map, 'dragstart', function(mEvent) {
				plfollow('');
			});
		google.maps.event.addListener(map, 'zoom_changed', function() {
				makeLink();
			});
		google.maps.event.addListener(map, 'center_changed', function() {
				makeLink();
			});
		map.dragstart = plfollow('');

		$.each(config.maps, function(name, mapType){
			map.mapTypes.set(name, mapType);
			
			var mapButton;
			$('#maplist').append($('<div/>')
					.addClass('maprow')
					.append(mapButton = $('<input/>')
						.addClass('maptype_' + name)
						.attr({
							type: 'radio',
							name: 'map',
							id: 'maptypebutton_' + name 
						})
						.attr('checked', config.defaultMap == name ? 'checked' : null)
						)
					.append($('<label/>')
							.attr('for', 'maptypebutton_' + name)
							.text(name)
							)
					.click(function() {
							$('.mapbutton').removeAttr('checked');
							map.setMapTypeId(name);
							mapButton.attr('checked', 'checked');
						})
				);
		});

		map.setMapTypeId(config.defaultMap);
		
		clock = new MinecraftClock($('#clock'));
		
		setTimeout(mapUpdate, config.updateRate);
	}

	function plistopen() {
		if(lstopen) {
			lstopen = false;
			lst.style.display = 'none';
			lst.style.visibility = 'hidden';
			plistbtn.src = 'list_off.png';
		} else {
			lstopen = true;
			lst.style.display = '';
			lst.style.visibility = '';
			plistbtn.src = 'list_on.png';
		}
	}

	function plfollow(name) {
		$('.followButton').removeAttr('checked');
		
		if(name in markers) {
			var m = markers[name];
			$(m.followButton).attr('checked', 'checked');
			map.panTo(m.getPosition());
		}
		followPlayer = name;
	}

	function makeLink() {
		var a=location.href.substring(0,location.href.lastIndexOf("/")+1)
		+ "?lat=" + map.getCenter().lat().toFixed(6)
		+ "&lng=" + map.getCenter().lng().toFixed(6)
		+ "&zoom=" + map.getZoom();
		document.getElementById("link").innerHTML = a;
	}

	//remove item (string or number) from an array
	function removeItem(originalArray, itemToRemove) {
		var j = 0;
		while (j < originalArray.length) {
			//	alert(originalArray[j]);
			if (originalArray[j] == itemToRemove) {
				originalArray.splice(j, 1);
			} else { j++; }
		}
		//	assert('hi');
		return originalArray;
	}
