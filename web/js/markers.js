
var dynmapmarkersets = {};

componentconstructors['markers'] = function(dynmap, configuration) {
	var me = this;

	function removeAllMarkers() {
		$.each(dynmapmarkersets, function(setname, set) {
			$.each(set.markers, function(mname, marker) {
				set.layergroup.removeLayer(marker.our_marker);
			});
			set.markers = {};
		});
	}
			
	function loadmarkers(world) {
		removeAllMarkers();
		$.getJSON(dynmap.options.tileUrl+'_markers_/marker_'+world+'.json', function(data) {
			var ts = data.timestamp;
			$.each(data.sets, function(name, markerset) {
				var ms = dynmapmarkersets[name];
				if(!ms) {
					ms = { id: name, label: markerset.label, hide: markerset.hide, layerprio: markerset.layerprio, markers: {} } ;
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
					ms.hide = markerset.hide;
					ms.timestamp = ts;
				}
				dynmapmarkersets[name] = ms;
				$.each(markerset.markers, function(mname, marker) {
					ms.markers[mname] = { label: marker.label, markup: marker.markup, x: marker.x, y: marker.y, z:marker.z,
						icon: marker.icon };
					createMarker(ms, ms.markers[mname], ts);
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
				.append($('<img/>').addClass('markerIcon16x16').attr({ src: dynmap.options.tileUrl+'_markers_/'+marker.icon+'.png' }));
			if(marker.markup) {
				$(div).append($('<span/>')
					.addClass(configuration.showlabel?'markerName-show':'markerName')
					.addClass('markerName_' + set.id)
					.append(marker.label));
			}
			else
				$(div).append($('<span/>')
					.addClass(configuration.showlabel?'markerName-show':'markerName')
					.addClass('markerName_' + set.id)
					.text(marker.label));
			return div;
		}});
		marker.timestamp = ts;
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
	
	$(dynmap).bind('component.markers', function(event, msg) {
		if(msg.msg == 'markerupdated') {
			var marker = dynmapmarkersets[msg.set].markers[msg.id];
			if(marker && marker.our_marker) {
				dynmapmarkersets[msg.set].layergroup.removeLayer(marker.our_marker);
				delete marker.our_marker;
			}
			marker = { x: msg.x, y: msg.y, z: msg.z, icon: msg.icon, label: msg.label, markup: msg.markup };
			dynmapmarkersets[msg.set].markers[msg.id] = marker;
			createMarker(dynmapmarkersets[msg.set], marker);
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
				dynmapmarkersets[msg.id] = { id: msg.id, label: msg.label, layerprio: msg.layerprio, markers:{} };
				createMarkerSet(dynmapmarkersets[msg.id]);
			}
			else {
				if(dynmapmarkersets[msg.id].label != msg.label) {
					dynmapmarkersets[msg.id].label = msg.label;
					//dynmap.layercontrol.removeLayer(dynmapmarkersets[msg.id].layergroup);
					//dynmap.layercontrol.addOverlay(dynmapmarkersets[msg.id].layergroup, dynmapmarkersets[msg.id].label);
					dynmap.addToLayerSelector(dynmapmarkersets[msg.id].layergroup, dynmapmarkersets[msg.id].label, 
						dynmapmarkersets[msg.id].layerprio || 0);
					
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
	});
	
    // Remove marker on start of map change
	$(dynmap).bind('mapchanging', function(event) {
		$.each(dynmapmarkersets, function(setname, set) {
			$.each(set.markers, function(mname, marker) {
				set.layergroup.removeLayer(marker.our_marker);
			});
		});
	});
    // Remove marker on map change - let update place it again
	$(dynmap).bind('mapchanged', function(event) {
		$.each(dynmapmarkersets, function(setname, set) {
			$.each(set.markers, function(mname, marker) {
				var marker = set.markers[mname];
				var markerPosition = getPosition(marker);
				marker.our_marker.setLatLng(markerPosition);
				if(dynmap.map.hasLayer(marker.our_marker) == false)
					set.layergroup.addLayer(marker.our_marker);
			});
		});
	});
	// Load markers for new world
	$(dynmap).bind('worldchanged', function(event) {
		loadmarkers(this.world.name);
	});
	
	loadmarkers(dynmap.world.name);

};