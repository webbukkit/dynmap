
var dynmapmarkersets = {};

componentconstructors['markers'] = function(dynmap, configuration) {
	var me = this;

	function removeAllMarkers() {
		$.each(dynmapmarkersets, function(setname, set) {
			$.each(set.markers, function(mname, marker) {
				deleteMarker(set, marker);
			});
			set.markers = {};
			$.each(set.areas, function(aname, area) {
				deleteMarker(set, area);
			});
			set.areas = {};			
			$.each(set.lines, function(lname, line) {
				deleteMarker(set, line);
			});
			set.lines = {};			
			$.each(set.circles, function(cname, circle) {
				deleteMarker(set, circle);
			});
			set.circles = {};			
		});
	}
			
	function loadmarkers(world) {
		removeAllMarkers();
		var url = concatURL(dynmap.options.url.markers, '_markers_/marker_' + world + '.json');
		
		$.getJSON(url, function(data) {
			var ts = data.timestamp;
			$.each(data.sets, function(name, markerset) {
				if(markerset.showlabels == undefined) markerset.showlabels = configuration.showlabel;
				var ms = dynmapmarkersets[name];
				if(!ms) {
					ms = { id: name, label: markerset.label, hide: markerset.hide, layerprio: markerset.layerprio, minzoom: markerset.minzoom || -1, maxzoom: markerset.maxzoom || -1, 
						showlabels: markerset.showlabels, markers: {}, areas: {}, lines: {}, circles: {} } ;
					createMarkerSet(ms, ts);
				}
				else {
					if(ms.label != markerset.label) {
						ms.label = markerset.label;
						dynmap.addToLayerSelector(ms.layergroup, ms.label, ms.layerprio || 0);
					}
					ms.markers = {};
					ms.areas = {};
					ms.lines = {};
					ms.circles = {};
					ms.hide = markerset.hide;
					ms.showlabels = markerset.showlabels;
					ms.timestamp = ts;
				}
				dynmapmarkersets[name] = ms;
				$.each(markerset.markers, function(mname, marker) {
					ms.markers[mname] = { label: marker.label, markup: marker.markup, x: marker.x, y: marker.y, z:marker.z,
						icon: marker.icon, desc: marker.desc, dim: marker.dim, minzoom: marker.minzoom || -1, maxzoom: marker.maxzoom || -1 };
					createMarker(ms, ms.markers[mname], ts);
				});
				$.each(markerset.areas, function(aname, area) {
					ms.areas[aname] = { label: area.label, markup: area.markup, desc: area.desc, x: area.x, z: area.z,
						ytop: area.ytop, ybottom: area.ybottom, color: area.color, weight: area.weight, opacity: area.opacity,
						fillcolor: area.fillcolor, fillopacity: area.fillopacity, minzoom: area.minzoom || -1, maxzoom: area.maxzoom || -1 };
					createArea(ms, ms.areas[aname], ts);
				});
				$.each(markerset.lines, function(lname, line) {
					ms.lines[lname] = { label: line.label, markup: line.markup, desc: line.desc, x: line.x, y: line.y, z: line.z,
						color: line.color, weight: line.weight, opacity: line.opacity, minzoom: line.minzoom || -1, maxzoom: line.maxzoom || -1 };
					createLine(ms, ms.lines[lname], ts);
				});
				$.each(markerset.circles, function(cname, circle) {
					ms.circles[cname] = { label: circle.label, markup: circle.markup, desc: circle.desc, x: circle.x, y: circle.y, z: circle.z,
						xr: circle.xr, zr: circle.zr, color: circle.color, weight: circle.weight, opacity: circle.opacity,
						fillcolor: circle.fillcolor, fillopacity: circle.fillopacity, minzoom: circle.minzoom || -1, maxzoom: circle.maxzoom || -1 };
					createCircle(ms, ms.circles[cname], ts);
				});
			});
			
			$(dynmap).trigger('markersupdated', [dynmapmarkersets]);
		});
	}
	
	function getPosition(marker) {
		return dynmap.getProjection().fromLocationToLatLng({ x: marker.x, y: marker.y, z: marker.z });
	}
	
	function createMarker(set, marker, ts) {
	
		if(marker.our_layer) {
			set.layergroup.removeLayer(marker.our_layer);
			delete marker.our_layer;
			marker.our_layer = null;
		}
	
		var markerPosition = getPosition(marker);
		marker.our_layer = new L.CustomMarker(markerPosition, { elementCreator: function() {
			var div = document.createElement('div');

			var markerPosition = getPosition(marker);
			marker.our_layer.setLatLng(markerPosition);
			var url = concatURL(dynmap.options.url.markers, '_markers_/'+marker.icon+'.png');
			
			$(div)
				.addClass('Marker')
				.addClass('mapMarker')
				.append($('<img/>').addClass('markerIcon'+marker.dim).attr({ src: url }));
			if (marker.label != "")
				$(div).append($('<span/>')
					.addClass(set.showlabels?'markerName-show':'markerName')
					.addClass('markerName_' + set.id)
					.addClass('markerName' + marker.dim)
					.append(marker.label));
			return div;
		}});
		marker.timestamp = ts;
		if(marker.desc) {
			var popup = document.createElement('div');
			$(popup).addClass('MarkerPopup').append(marker.desc);
			marker.our_layer.bindPopup(popup, {});
		}
		
		updateMarker(set, marker, dynmap.map.getZoom());
	}

	function updateMarker(set, marker, mapzoom) {
		if (set && marker && marker.our_layer) {
			// marker specific zoom supercedes set specific zoom
			var minzoom = (marker.minzoom >= 0) ? marker.minzoom : set.minzoom;
			var maxzoom = (marker.maxzoom >= 0) ? marker.maxzoom : set.maxzoom;
			if (maxzoom < 0) maxzoom = 100;
			set.layergroup.removeLayer(marker.our_layer);
			if ((mapzoom >= minzoom) && (mapzoom <= maxzoom)) {  
				set.layergroup.addLayer(marker.our_layer);
			}
		}
	}
	
	function deleteMarker(set, marker) {
		if(marker && marker.our_layer) {
			set.layergroup.removeLayer(marker.our_layer);
			delete marker.our_layer;
		}
	}	
	
	function createMarkerSet(set, ts) {
		set.layergroup = new L.LayerGroup();
		set.timestamp = ts;
		if(!set.hide)
			dynmap.map.addLayer(set.layergroup);
//		dynmap.layercontrol.addOverlay(set.layergroup, set.label);
		dynmap.addToLayerSelector(set.layergroup, set.label, set.layerprio || 0);

	}

	function createArea(set, area, ts) {
		var style = { color: area.color, opacity: area.opacity, weight: area.weight, fillOpacity: area.fillopacity, fillColor: area.fillcolor, smoothFactor: 0.0 };

		if(area.our_layer) {
			set.layergroup.removeLayer(area.our_layer);
			delete area.our_layer;
			area.our_layer = null;
		}
		
		if(area.x.length == 2) {	/* Only 2 points */
			if(area.ytop == area.ybottom) {
				area.our_layer = create2DBoxLayer(area.x[0], area.x[1], area.ytop, area.ybottom, area.z[0], area.z[1], style);
			}
			else {
				area.our_layer = create3DBoxLayer(area.x[0], area.x[1], area.ytop, area.ybottom, area.z[0], area.z[1], style);
			}
		}
		else {
			if(area.ytop == area.ybottom) {
				area.our_layer = create2DOutlineLayer(area.x, area.ytop, area.ybottom, area.z, style);
			}
			else {
				area.our_layer = create3DOutlineLayer(area.x, area.ytop, area.ybottom, area.z, style);
			}
		}
		area.timestamp = ts;
		if(area.label != "") {
			var popup = document.createElement('span');
			if(area.desc) {
				$(popup).addClass('AreaPopup').append(area.desc);
			}
			else {
				$(popup).addClass('AreaPopup').append(area.label);
			}
			area.our_layer.bindPopup($(popup).html(), {});
		}
		
		updateMarker(set, area, dynmap.map.getZoom());
	}
	
	function createLine(set, line, ts) {
		var style = { color: line.color, opacity: line.opacity, weight: line.weight, smoothFactor: 0.0 };

		if(line.our_layer) {
			set.layergroup.removeLayer(line.our_layer);
			delete line.our_layer;
			line.our_layer = null;
		}
		
		var llist = [];
		var i;
		for(i = 0; i < line.x.length; i++) {
			llist[i] = latlng(line.x[i], line.y[i], line.z[i]);
		}
		line.our_layer = new L.Polyline(llist, style);
		line.timestamp = ts;
		if(line.label != "") {
			var popup = document.createElement('span');
			if(line.desc) {
				$(popup).addClass('LinePopup').append(line.desc);
			}
			else {
				$(popup).addClass('LinePopup').append(line.label);
			}
			line.our_layer.bindPopup($(popup).html(), {});
		}
		
		updateMarker(set, line, dynmap.map.getZoom());
	}

	function createCircle(set, circle, ts) {
		var style = { color: circle.color, opacity: circle.opacity, weight: circle.weight, fillOpacity: circle.fillopacity, fillColor: circle.fillcolor };

		if(circle.our_layer) {
			set.layergroup.removeLayer(circle.our_layer);
			delete circle.our_layer;
			circle.our_layer = null;
		}	
		var x = [];
		var z = [];
		var i;
		for(i = 0; i < 360; i++) {
			var rad = i * Math.PI / 180.0;
			x[i] = circle.xr * Math.sin(rad) + circle.x;
			z[i] = circle.zr * Math.cos(rad) + circle.z;
		}
		circle.our_layer = create2DOutlineLayer(x, circle.y, circle.y, z, style);
		circle.timestamp = ts;
		if(circle.label != "") {
			var popup = document.createElement('span');
			if(circle.desc) {
				$(popup).addClass('CirclePopup').append(circle.desc);
			}
			else {
				$(popup).addClass('CirclePopup').append(circle.label);
			}
			circle.our_layer.bindPopup($(popup).html(), {});
		}
		
		updateMarker(set, circle, dynmap.map.getZoom());
	}
	
	// Helper functions
	latlng = function(x, y, z) {
		return dynmap.getProjection().fromLocationToLatLng(new Location(undefined, x,y,z));
	}
	
	function create3DBoxLayer(maxx, minx, maxy, miny, maxz, minz, style) {
		return new L.Polygon([
			[
				latlng(minx,miny,minz),
				latlng(maxx,miny,minz),
				latlng(maxx,miny,maxz),
				latlng(minx,miny,maxz)
			],[
				latlng(minx,maxy,minz),
				latlng(maxx,maxy,minz),
				latlng(maxx,maxy,maxz),
				latlng(minx,maxy,maxz)
			],[
				latlng(minx,miny,minz),
				latlng(minx,maxy,minz),
				latlng(maxx,maxy,minz),
				latlng(maxx,miny,minz)
			],[
				latlng(maxx,miny,minz),
				latlng(maxx,maxy,minz),
				latlng(maxx,maxy,maxz),
				latlng(maxx,miny,maxz)
			],[
				latlng(minx,miny,maxz),
				latlng(minx,maxy,maxz),
				latlng(maxx,maxy,maxz),
				latlng(maxx,miny,maxz)
			],[
				latlng(minx,miny,minz),
				latlng(minx,maxy,minz),
				latlng(minx,maxy,maxz),
				latlng(minx,miny,maxz)
			]], style);
	}
	
	function create2DBoxLayer(maxx, minx, maxy, miny, maxz, minz, style) {
		if(style.fillOpacity <= 0.0)
			return new L.Polyline([
				latlng(minx,miny,minz),
				latlng(maxx,miny,minz),
				latlng(maxx,miny,maxz),
				latlng(minx,miny,maxz),
				latlng(minx,miny,minz)
				], style);
		else
			return new L.Polygon([
				latlng(minx,miny,minz),
				latlng(maxx,miny,minz),
				latlng(maxx,miny,maxz),
				latlng(minx,miny,maxz)
				], style);
	}

	function create3DOutlineLayer(xarray, maxy, miny, zarray, style) {
		var toplist = [];
		var botlist = [];
		var i;
		var polylist = [];
		for(i = 0; i < xarray.length; i++) {
			toplist[i] = latlng(xarray[i], maxy, zarray[i]);
			botlist[i] = latlng(xarray[i], miny, zarray[i]);
		}
		for(i = 0; i < xarray.length; i++) {
			var sidelist = [];
			sidelist[0] = toplist[i];
			sidelist[1] = botlist[i];
			sidelist[2] = botlist[(i+1)%xarray.length];
			sidelist[3] = toplist[(i+1)%xarray.length];
			polylist[i] = sidelist;
		}
		polylist[xarray.length] = botlist;
		polylist[xarray.length+1] = toplist;
		
		return new L.Polygon(polylist, style);
	}

	function create2DOutlineLayer(xarray, maxy, miny, zarray, style) {
		var llist = [];
		var i;
		for(i = 0; i < xarray.length; i++) {
			llist[i] = latlng(xarray[i], miny, zarray[i]);
		}
		if(style.fillOpacity <= 0.0) {
			llist.push(llist[0]);
			return new L.Polyline(llist, style);
		}
		else
			return new L.Polygon(llist, style);
	}
	
	$(dynmap).bind('component.markers', function(event, msg) {
		if(msg.msg == 'markerupdated') {
			var set = dynmapmarkersets[msg.set];
			if (!set) return;
			deleteMarker(set, set.markers[msg.id]);
			
			var marker = { x: msg.x, y: msg.y, z: msg.z, icon: msg.icon, label: msg.label, markup: msg.markup, desc: msg.desc, dim: msg.dim || '16x16', minzoom: msg.minzoom || -1, maxzoom: msg.maxzoom };
			set.markers[msg.id] = marker;
			createMarker(set, marker, msg.timestamp);
		}
		else if(msg.msg == 'markerdeleted') {
			var set = dynmapmarkersets[msg.set];
			if (!set) return;
			deleteMarker(set, set.markers[msg.id]);
			delete set.markers[msg.id];
		}
		else if(msg.msg == 'setupdated') {
			if(msg.showlabels == undefined) msg.showlabels = configuration.showlabel;
			if(!dynmapmarkersets[msg.id]) {
				dynmapmarkersets[msg.id] = { id: msg.id, label: msg.label, layerprio: msg.layerprio, minzoom: msg.minzoom,  maxzoom: msg.maxzoom,
					showlabels: msg.showlabels, markers:{} };
				createMarkerSet(dynmapmarkersets[msg.id]);
			}
			else {
				if((dynmapmarkersets[msg.id].label != msg.label) || (dynmapmarkersets[msg.id].layerprio != msg.layerprio) ||
				   (dynmapmarkersets[msg.id].showlabels != msg.showlabels)) {
					dynmapmarkersets[msg.id].label = msg.label;
					dynmapmarkersets[msg.id].layerprio = msg.layerprio;
					dynmapmarkersets[msg.id].showlabels = msg.showlabels;
					//dynmap.layercontrol.removeLayer(dynmapmarkersets[msg.id].layergroup);
					//dynmap.layercontrol.addOverlay(dynmapmarkersets[msg.id].layergroup, dynmapmarkersets[msg.id].label);
					dynmap.addToLayerSelector(dynmapmarkersets[msg.id].layergroup, dynmapmarkersets[msg.id].label, 
						dynmapmarkersets[msg.id].layerprio || 0);
				}
				if(dynmapmarkersets[msg.id].minzoom != msg.minzoom) {
					dynmapmarkersets[msg.id].minzoom = msg.minzoom;
				}			
				if(dynmapmarkersets[msg.id].maxzoom != msg.maxzoom) {
					dynmapmarkersets[msg.id].maxzoom = msg.maxzoom;
				}			
			}
		}
		else if(msg.msg == 'setdeleted') {
			if(dynmapmarkersets[msg.id]) {
				dynmap.removeFromLayerSelector(dynmapmarkersets[msg.id].layergroup);
				delete dynmapmarkersets[msg.id].layergroup;
				delete dynmapmarkersets[msg.id];
			}
		}		
		else if(msg.msg == 'areaupdated') {
			var set = dynmapmarkersets[msg.set];
			if (!set) return;
			deleteMarker(set, set.areas[msg.id]);

			var area = { x: msg.x, ytop: msg.ytop, ybottom: msg.ybottom, z: msg.z, label: msg.label, markup: msg.markup, desc: msg.desc,
				color: msg.color, weight: msg.weight, opacity: msg.opacity, fillcolor: msg.fillcolor, fillopacity: msg.fillopacity, minzoom: msg.minzoom || -1, maxzoom: msg.maxzoom || -1 };
			set.areas[msg.id] = area;
			createArea(set, area, msg.timestamp);
		}
		else if(msg.msg == 'areadeleted') {
			var set = dynmapmarkersets[msg.set];
			if (!set) return;
			deleteMarker(set, set.areas[msg.id]);
			delete set.areas[msg.id];
		}
		else if(msg.msg == 'lineupdated') {
			var set = dynmapmarkersets[msg.set];
			if (!set) return;
			deleteMarker(set, set.lines[msg.id]);
			
			var line = { x: msg.x, y: msg.y, z: msg.z, label: msg.label, markup: msg.markup, desc: msg.desc,
				color: msg.color, weight: msg.weight, opacity: msg.opacity, minzoom: msg.minzoom || -1, maxzoom: msg.maxzoom || -1 };
			set.lines[msg.id] = line;
			createLine(set, line, msg.timestamp);
		}
		else if(msg.msg == 'linedeleted') {
			var set = dynmapmarkersets[msg.set];
			if (!set) return;
			deleteMarker(set, set.lines[msg.id]);
			delete set.lines[msg.id];
		}
		else if(msg.msg == 'circleupdated') {
			var set = dynmapmarkersets[msg.set];
			if (!set) return;
			deleteMarker(set, set.circles[msg.id]);

			var circle = { x: msg.x, y: msg.y, z: msg.z, xr: msg.xr, zr: msg.zr, label: msg.label, markup: msg.markup, desc: msg.desc,
				color: msg.color, weight: msg.weight, opacity: msg.opacity, fillcolor: msg.fillcolor, fillopacity: msg.fillopacity, minzoom: msg.minzoom || -1, maxzoom: msg.maxzoom || -1 };
			set.circles[msg.id] = circle;
			createCircle(set, circle, msg.timestamp);
		}
		else if(msg.msg == 'circledeleted') {
			var set = dynmapmarkersets[msg.set];
			if (!set) return;
			deleteMarker(set, set.circles[msg.id]);
			delete set.circles[msg.id];
		}
		
		$(dynmap).trigger('markersupdated', [dynmapmarkersets]);
	});
	
    // Remove markers on start of map change
	$(dynmap).bind('mapchanging', function(event) {
		$.each(dynmapmarkersets, function(setname, set) {
			$.each(set.markers, function(mname, marker) {
				deleteMarker(set, marker);
			});
			$.each(set.areas, function(aname, area) {
				deleteMarker(set, area);
			});
			$.each(set.lines, function(lname, line) {
				deleteMarker(set, line);
			});
			$.each(set.circles, function(cname, circle) {
				deleteMarker(set, circle);
			});
		});
	});
    // Recreate markers after map change
	$(dynmap).bind('mapchanged', function(event) {
		var zoom = dynmap.map.getZoom();
		$.each(dynmapmarkersets, function(setname, set) {
			$.each(set.markers, function(mname, marker) {
				createMarker(set, marker, marker.timestamp);
			});
			$.each(set.areas, function(aname, area) {
				createArea(set, area, area.timestamp);
			});
			$.each(set.lines, function(lname, line) {
				createLine(set, line, line.timestamp);
			});
			$.each(set.circles, function(cname, circle) {
				createCircle(set, circle, circle.timestamp);
			});
		});
	});
	$(dynmap).bind('zoomchanged', function(event) {
		var zoom = dynmap.map.getZoom();
		$.each(dynmapmarkersets, function(setname, set) {
			$.each(set.markers, function(mname, marker) {
				updateMarker(set, marker, zoom);
			});
			$.each(set.areas, function(aname, area) {
				updateMarker(set, area, zoom);
			});
			$.each(set.lines, function(lname, line) {
				updateMarker(set, line, zoom);
			});
			$.each(set.circles, function(cname, circle) {
				updateMarker(set, circle, zoom);
			});
		});
	});

	// Load markers for new world
	$(dynmap).bind('worldchanged', function(event) {
		loadmarkers(this.world.name);
	});
	
	loadmarkers(dynmap.world.name);
};
