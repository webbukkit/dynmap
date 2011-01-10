if (!console) console = { log: function() {} }; 

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

	function onTileUpdated(tileName, f) {
		
	}
	
	function imgSubst(tileName) {
		if(!(tileName in tileDict))
			return;

		var src = tileUrl(tileName);
		var t = tileDict[tileName];
		t.attr('src', src);
		t.show();
	}


	var markers = new Array();
	var lasttimestamp = '0';
	var followPlayer = '';

	var lst;
	var plistbtn;
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
						position: map.getProjection().fromWorldToLatLng(p[2], p[3], p[4]),
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
