/* generic function for making an XMLHttpRequest
 *  url:   request URL
 *  func:  callback function for success
 *  type:  'text' by default (callback is called with response text)
 *         otherwise, callback is called with a parsed XML dom
 *  fail:  callback function for failure
 *  post:  if given, make a POST request instead of GET; post data given
 *
 *  contenttype: if given for a POST, set request content-type header
 */
function makeRequest(url, func, type, fail, post, contenttype)
{
	var http_request = false;

	type = typeof(type) != 'undefined' ? type : 'text';
	fail = typeof(fail) != 'undefined' ? fail : function() { };

	if(window.XMLHttpRequest) {
		http_request = new XMLHttpRequest();
	} else if(window.ActiveXObject) {
		http_request = new ActiveXObject("Microsoft.XMLHTTP");
	}

	if(type == 'text') {
		http_request.onreadystatechange = function() {
			if(http_request.readyState == 4) {
				if(http_request.status == 200) {
					func(http_request.responseText);
				} else {
					fail(http_request);
				}
			}
		}
	} else {
		http_request.onreadystatechange = function() {
			if(http_request.readyState == 4) {
				if(http_request.status == 200) { func(http_request.responseXML); } else {
					fail(http_request);
				}
			}
		}
	}

	if(typeof(post) != 'undefined') {
		http_request.open('POST', url, true);
		if(typeof(contenttype) != 'undefined')
			http_request.setRequestHeader("Content-Type", contenttype);
		http_request.send(post);
	} else {
		http_request.open('GET', url, true);
		http_request.send(null);
	}
}
 
 
 	var config = {
		tileUrl:     setup.tileUrl,
		updateUrl:   setup.updateUrl,
		tileWidth:   128,
		tileHeight:  128,
		updateRate:  setup.updateRate,
		zoomSize:    [ 128, 128, 256, 512 ]
	};

	function MCMapProjection() {
	  }

	MCMapProjection.prototype.fromLatLngToPoint = function(latLng) {
		var x = (latLng.lng() * config.tileWidth)|0;
		var y = (latLng.lat() * config.tileHeight)|0;

		return new google.maps.Point(x, y);
	};

	MCMapProjection.prototype.fromPointToLatLng = function(point) {
		var x = point.x;
		var lng = x / config.tileWidth;
		var lat = point.y / config.tileHeight;
		return new google.maps.LatLng(lat, lng);
	};

	function fromWorldToLatLng(x, y, z)
	{
		var dx = +x;
		var dy = +y - 127;
		var dz = +z;
		var px = dx + dz;
		var py = dx - dz - dy;

		var lng = -px / config.tileWidth / 2 + 0.5;
		var lat = py / config.tileHeight / 2;

		return new google.maps.LatLng(lat, lng);
	}

	function mcMapType() {
	}

	var tileDict = new Array();
	var lastSeen = new Array();

	function tileUrl(tile, always) {
		if(always) {
			var now = new Date();
			return config.tileUrl + tile + '.png?' + now.getTime();
		} else if(tile in lastSeen) {
			return config.tileUrl + tile + '.png?' + lastSeen[tile];
		} else {
			return config.tileUrl + tile + '.png?0';
		}
	}

	function imgSubst(tile) {
		if(!(tile in tileDict))
			return;

		var src = tileUrl(tile);
		var t = tileDict[tile];
		t.src = src;
		t.style.display = '';
		t.onerror = function() {
			setTimeout(function() {
				t.src = tileUrl(tile, 1);
			}, 1000);
			t.onerror = '';
		}
	}

	var caveMode = false;

	function caveSwitch()
	{
		caveMode = !caveMode;

		if(caveMode) {
			cavebtn.src = 'cave_on.png';
			map.setMapTypeId('cavemap');
		} else {
			cavebtn.src = 'cave_off.png';
			map.setMapTypeId('mcmap');
		}
	}

	mcMapType.prototype.tileSize = new google.maps.Size(config.tileWidth, config.tileHeight);
	mcMapType.prototype.minZoom = 0;
	mcMapType.prototype.maxZoom = 3;
	mcMapType.prototype.getTile = function(coord, zoom, doc) {
		var img = doc.createElement('IMG');

		img.onerror = function() { img.style.display = 'none'; }

		img.style.width = config.zoomSize[zoom] + 'px';
		img.style.height = config.zoomSize[zoom] + 'px';
		//img.style.borderStyle = 'none';
		img.style.border = '1px solid red';
		img.style.margin = '-1px -1px -1px -1px';

		var pfx = caveMode ? "c" : "";

		if(zoom > 0) {
			var tilename = pfx + "t_" + (- coord.x * config.tileWidth) + '_' + coord.y * config.tileHeight;
		} else {
			var tilename = pfx + "zt_" + (- coord.x * config.tileWidth * 2) + '_' + (coord.y * config.tileHeight * 2);
		}

		tileDict[tilename] = img;

		var url = tileUrl(tilename);
		img.src = url;
		//img.style.background = 'url(' + url + ')';
		//img.innerHTML = '<small>' + tilename + '</small>';

		return img;
	}

	var markers = new Array();
	var lasttimestamp = '0';
	var followPlayer = '';

	var lst;
	var plistbtn;
	var cavebtn;
	var lstopen = true;
	var oldplayerlst = '[Connecting]';
	var servertime = 0;

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
		makeRequest(config.updateUrl + lasttimestamp, function(res) {
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
			servertime = firstRow[1];
			delete rows[0];
 
			for(var line in rows) {
				var p = rows[line].split(' ');

				if (p[0] == '') continue;
				
				({	tile: function() {
						var tileName = p[1];
						lastSeen[tileName] = lasttimestamp;
						imgSubst(tileName);
					}
				}[p[0]] || function() {
					var mi = {
						id: p[0] + '_' + p[1],
						text: p[1],
						type: p[0],
						position: fromWorldToLatLng(p[2], p[3], p[4]),
						visible: ((p[0] in typeVisibleMap) ? typeVisibleMap[p[0]] : true)
					};

					updateMarker(mi);
					loggedin[mi.id] = 1;
					if (!mi.type in typeCount) typeCount[mi.type] = 0;
					typeCount[mi.type]++;
				})();
			}
 
			var time = {
				// Assuming it is day at 8:00
				hours: (parseInt(servertime / 1000)+8) % 24,
				minutes: parseInt(((servertime / 1000) % 1) * 60),
				seconds: parseInt(((((servertime / 1000) % 1) * 60) % 1) * 60)
			};

			
			$('#clock')
				.addClass(servertime > 12000 ? 'night' : 'day')
				.removeClass(servertime > 12000 ? 'day' : 'night')
				.text(formatTime(time));

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
		}, 'text', function() { alert('failed to get update data'); } );
	}

	function formatTime(time) {
		//return formatDigits(time.hours, 2) + ':' + formatDigits(time.minutes, 2) + ':' + formatDigits(time.seconds, 2);
		return formatDigits(time.hours, 2) + ':' + formatDigits(time.minutes, 2);
	}

	function formatDigits(n, digits) {
		var s = n.toString();
		while (s.length < digits) {
			s = '0' + s;
		}
		return s;
	}

	window.onload = function initialize() {
		lst = document.getElementById('lst');
		plistbtn = document.getElementById('plistbtn');
		cavebtn = document.getElementById('cavebtn');

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
			mapTypeId: 'mcmap',
			backgroundColor: '#000'
		};
		map = new google.maps.Map(document.getElementById("mcmap"), mapOptions);
		mapType = new mcMapType();
		mapType.projection = new MCMapProjection();
		caveMapType = new mcMapType();
		caveMapType.projection = new MCMapProjection();

		map.zoom_changed = function() {
			mapType.tileSize = new google.maps.Size(config.zoomSize[map.zoom], config.zoomSize[map.zoom]);
			caveMapType.tileSize = mapType.tileSize;
		};

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

		map.mapTypes.set('mcmap', mapType);
		map.mapTypes.set('cavemap', caveMapType);

		map.setMapTypeId('mcmap');
		mapUpdate();
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
