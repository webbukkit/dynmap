
var dynmapmarkersets = {};

componentconstructors['markers'] = function(dynmap, configuration) {
	var me = this;

	function removeAllMarkers() {
		$.each(dynmapmarkersets, function(setname, set) {
			$.each(set.markers, function(mname, marker) {
				set.layergroup.removeLayer(marker.our_marker);
			});
			set.markers = {};
			$.each(set.areas, function(aname, area) {
				set.layergroup.removeLayer(area.our_area);
			});
			set.areas = {};			
		});
	}
			
	function loadmarkers(world) {
		removeAllMarkers();
		$.getJSON(dynmap.options.tileUrl+'_markers_/marker_'+world+'.json', function(data) {
			var ts = data.timestamp;
			$.each(data.sets, function(name, markerset) {
				var ms = dynmapmarkersets[name];
				if(!ms) {
					ms = { id: name, label: markerset.label, hide: markerset.hide, layerprio: markerset.layerprio, minzoom: markerset.minzoom, markers: {}, areas: {} } ;
					createMarkerSet(ms, ts);
				}
				else {
					if(ms.label != markerset.label) {
						ms.label = markerset.label;
						//dynmap.layercontrol.removeLayer(ms.layergroup);
						//dynmap.layercontrol.addOverlay(ms.layergroup, ms.label);
						dynmap.addToLayerSelector(ms.layergroup, ms.label, ms.layerprio || 0);
					}
					ms.markers = {};
					ms.areas = {};
					ms.hide = markerset.hide;
					ms.timestamp = ts;
				}
				dynmapmarkersets[name] = ms;
				$.each(markerset.markers, function(mname, marker) {
					ms.markers[mname] = { label: marker.label, markup: marker.markup, x: marker.x, y: marker.y, z:marker.z,
						icon: marker.icon, desc: marker.desc, dim: marker.dim };
					createMarker(ms, ms.markers[mname], ts);
				});
				$.each(markerset.areas, function(aname, area) {
					ms.areas[aname] = { label: area.label, markup: area.markup, desc: area.desc, x: area.x, z: area.z,
						ytop: area.ytop, ybottom: area.ybottom, color: area.color, weight: area.weight, opacity: area.opacity,
						fillcolor: area.fillcolor, fillopacity: area.fillopacity };
					createArea(ms, ms.areas[aname], ts);
				});
			});
		});
	}
	
	function getPosition(marker) {
		return dynmap.getProjection().fromLocationToLatLng({ x: marker.x, y: marker.y, z: marker.z });
	}
	
	function createMarker(set, marker, ts) {
		var markerPosition = getPosition(marker);
		marker.our_marker = new L.CustomMarker(markerPosition, { elementCreator: function() {
			var div = document.createElement('div');

			var markerPosition = getPosition(marker);
			marker.our_marker.setLatLng(markerPosition);
						
			$(div)
				.addClass('Marker')
				.addClass('mapMarker')
				.append($('<img/>').addClass('markerIcon'+marker.dim).attr({ src: dynmap.options.tileUrl+'_markers_/'+marker.icon+'.png' }));
			if(marker.markup) {
				$(div).append($('<span/>')
					.addClass(configuration.showlabel?'markerName-show':'markerName')
					.addClass('markerName_' + set.id)
					.addClass('markerName' + marker.dim)
					.append(marker.label));
			}
			else if(marker.label != "")
				$(div).append($('<span/>')
					.addClass(configuration.showlabel?'markerName-show':'markerName')
					.addClass('markerName_' + set.id)
					.addClass('markerName' + marker.dim)
					.text(marker.label));
			return div;
		}});
		marker.timestamp = ts;
		if(marker.desc) {
			var popup = document.createElement('div');
			$(popup).addClass('MarkerPopup').append(marker.desc);
			marker.our_marker.bindPopup(popup, {});
		}
		if((set.minzoom < 1) || (dynmap.map.getZoom() >= set.minzoom))
			set.layergroup.addLayer(marker.our_marker);
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

		if(area.our_area && dynmap.map.hasLayer(area.our_area))
			set.layergroup.removeLayer(area.our_area);

		if(area.x.length == 2) {	/* Only 2 points */
			if(area.ytop == area.ybottom) {
				area.our_area = create2DBoxLayer(area.x[0], area.x[1], area.ytop, area.ybottom, area.z[0], area.z[1], style);
			}
			else {
				area.our_area = create3DBoxLayer(area.x[0], area.x[1], area.ytop, area.ybottom, area.z[0], area.z[1], style);
			}
		}
		else {
			if(area.ytop == area.ybottom) {
				area.our_area = create2DOutlineLayer(area.x, area.ytop, area.ybottom, area.z, style);
			}
			else {
				area.our_area = create3DOutlineLayer(area.x, area.ytop, area.ybottom, area.z, style);
			}
		}
		area.timestamp = ts;
		if(area.label != "") {
			var popup = document.createElement('div');
			if(area.desc) {
				$(popup).addClass('AreaPopup').append(area.desc);
			}
			else if(area.markup) {
				$(popup).addClass('AreaPopup').append(area.label);
			}
			else {
				$(popup).text(area.label);
			}
			area.our_area.bindPopup(popup, {});
		}
		if((set.minzoom < 1) || (dynmap.map.getZoom() >= set.minzoom)) {
			set.layergroup.addLayer(area.our_area);
		}
	}

	// Helper functions
	latlng = function(x, y, z) {
		return dynmap.getProjection().fromLocationToLatLng(new Location(undefined, x,y,z));
	}
	
	function create3DBoxLayer(maxx, minx, maxy, miny, maxz, minz, style) {
		return new L.MultiPolygon([
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
		
		return new L.MultiPolygon(polylist, style);
	}

	function create2DOutlineLayer(xarray, maxy, miny, zarray, style) {
		var llist = [];
		var i;
		for(i = 0; i < xarray.length; i++) {
			llist[i] = latlng(xarray[i], miny, zarray[i]);
		}
		return new L.Polygon(llist, style);
	}
	
	$(dynmap).bind('component.markers', function(event, msg) {
		if(msg.msg == 'markerupdated') {
			var marker = dynmapmarkersets[msg.set].markers[msg.id];
			if(marker && marker.our_marker) {
				dynmapmarkersets[msg.set].layergroup.removeLayer(marker.our_marker);
				delete marker.our_marker;
			}
			marker = { x: msg.x, y: msg.y, z: msg.z, icon: msg.icon, label: msg.label, markup: msg.markup, desc: msg.desc, dim: msg.dim || '16x16' };
			dynmapmarkersets[msg.set].markers[msg.id] = marker;
			createMarker(dynmapmarkersets[msg.set], marker, msg.timestamp);
		}
		else if(msg.msg == 'markerdeleted') {
			var marker = dynmapmarkersets[msg.set].markers[msg.id];
			if(marker && marker.our_marker) {
				dynmapmarkersets[msg.set].layergroup.removeLayer(marker.our_marker);
			}
			delete dynmapmarkersets[msg.set].markers[msg.id];
		}
		else if(msg.msg == 'setupdated') {
			if(!dynmapmarkersets[msg.id]) {
				dynmapmarkersets[msg.id] = { id: msg.id, label: msg.label, layerprio: msg.layerprio, minzoom: msg.minzoom, markers:{} };
				createMarkerSet(dynmapmarkersets[msg.id]);
			}
			else {
				if((dynmapmarkersets[msg.id].label != msg.label) || (dynmapmarkersets[msg.id].layerprio != msg.layerprio)) {
					dynmapmarkersets[msg.id].label = msg.label;
					dynmapmarkersets[msg.id].layerprio = msg.layerprio;
					//dynmap.layercontrol.removeLayer(dynmapmarkersets[msg.id].layergroup);
					//dynmap.layercontrol.addOverlay(dynmapmarkersets[msg.id].layergroup, dynmapmarkersets[msg.id].label);
					dynmap.addToLayerSelector(dynmapmarkersets[msg.id].layergroup, dynmapmarkersets[msg.id].label, 
						dynmapmarkersets[msg.id].layerprio || 0);
				}
				if(dynmapmarkersets[msg.id].minzoom != msg.minzoom) {
					dynmapmarkersets[msg.id].minzoom = msg.minzoom;
				}			
			}
		}
		else if(msg.msg == 'setdeleted') {
			if(dynmapmarkersets[msg.id]) {
				//dynmap.layercontrol.removeLayer(dynmapmarkersets[msg.id].layergroup);
				dynmap.removeFromLayerSelector(dynmapmarkersets[msg.id].layergroup);
				delete dynmapmarkersets[msg.id].layergroup;
				delete dynmapmarkersets[msg.id];
			}
		}		
		else if(msg.msg == 'areaupdated') {
			var area = dynmapmarkersets[msg.set].areas[msg.id];
			if(area && area.our_area) {
				dynmapmarkersets[msg.set].layergroup.removeLayer(area.our_area);
				delete area.our_area;
			}
			area = { x: msg.x, ytop: msg.ytop, ybottom: msg.ybottom, z: msg.z, label: msg.label, markup: msg.markup, desc: msg.desc,
				color: msg.color, weight: msg.weight, opacity: msg.opacity, fillcolor: msg.fillcolor, fillopacity: msg.fillopacity };
			dynmapmarkersets[msg.set].areas[msg.id] = area;
			createArea(dynmapmarkersets[msg.set], area, msg.timestamp);
		}
		else if(msg.msg == 'areadeleted') {
			var area = dynmapmarkersets[msg.set].areas[msg.id];
			if(area && area.our_area) {
				dynmapmarkersets[msg.set].layergroup.removeLayer(area.our_area);
			}
			delete dynmapmarkersets[msg.set].areas[msg.id];
		}
	});
	
    // Remove marker on start of map change
	$(dynmap).bind('mapchanging', function(event) {
		$.each(dynmapmarkersets, function(setname, set) {
			$.each(set.markers, function(mname, marker) {
				set.layergroup.removeLayer(marker.our_marker);
			});
			$.each(set.areas, function(aname, area) {
				set.layergroup.removeLayer(area.our_area);
			});
		});
	});
    // Remove marker on map change - let update place it again
	$(dynmap).bind('mapchanged', function(event) {
		var zoom = dynmap.map.getZoom();
		$.each(dynmapmarkersets, function(setname, set) {
			if((set.minzoom < 1) || (zoom >= set.minzoom)) {
				$.each(set.markers, function(mname, marker) {
					var marker = set.markers[mname];
					var markerPosition = getPosition(marker);
					marker.our_marker.setLatLng(markerPosition);
					if(dynmap.map.hasLayer(marker.our_marker) == false)
						set.layergroup.addLayer(marker.our_marker);
				});
				$.each(set.areas, function(aname, area) {
					createArea(set, area, area.timestamp);
				});
			}
		});
	});
	$(dynmap).bind('zoomchanged', function(event) {
		var zoom = dynmap.map.getZoom();
		$.each(dynmapmarkersets, function(setname, set) {
			if(set.minzoom > 0) {
				if(zoom >= set.minzoom) {
					$.each(set.markers, function(mname, marker) {
						var marker = set.markers[mname];
						var markerPosition = getPosition(marker);
						marker.our_marker.setLatLng(markerPosition);
						if(dynmap.map.hasLayer(marker.our_marker) == false)
							set.layergroup.addLayer(marker.our_marker);
					});
					$.each(set.areas, function(aname, area) {
						if(dynmap.map.hasLayer(area.our_area))
							set.layergroup.removeLayer(area.our_area);
						createArea(set, area, area.timestamp);
					});
				}
				else {
					$.each(set.markers, function(mname, marker) {
						set.layergroup.removeLayer(marker.our_marker);
					});
					$.each(set.areas, function(aname, area) {
						set.layergroup.removeLayer(area.our_area);
					});
				}
			}
		});
	});

	// Load markers for new world
	$(dynmap).bind('worldchanged', function(event) {
		loadmarkers(this.world.name);
	});
	
	loadmarkers(dynmap.world.name);

};