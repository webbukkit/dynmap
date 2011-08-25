var regionConstructors = {};

componentconstructors['regions'] = function(dynmap, configuration) {
	
	// Compatibility with older configurations.
	if (configuration.regionstyle) {
		configuration.regionstyle = $.extend({
			stroke: true,
			color: configuration.regionstyle.strokeColor,
			opacity: configuration.regionstyle.strokeOpacity || 0.01,
			weight: configuration.regionstyle.strokeWeight,
			fill: true,
			smoothFactor: 0.0,
			fillOpacity: configuration.regionstyle.fillOpacity || 0.01,
			fillColor: configuration.regionstyle.fillColor
		}, configuration.regionstyle);
	}
	
	function getStyle(name, group) {
		var style = $.extend({}, configuration.regionstyle);
		if(configuration.groupstyle && group && configuration.groupstyle[group]) {
			var cs = configuration.groupstyle[group];
			if(cs.strokeColor)
				style.color = cs.strokeColor;
			if(cs.strokeOpacity)
				style.opacity = cs.strokeOpacity;
			if(cs.strokeWeight)
				style.weight = cs.strokeWeight;
			if(cs.fillOpacity)
				style.fillOpacity = cs.fillOpacity;
			if(cs.fillColor)
				style.fillColor = cs.fillColor;
		}
		if(configuration.customstyle && name && configuration.customstyle[name]) {
			var cs = configuration.customstyle[name];
			if(cs.strokeColor)
				style.color = cs.strokeColor;
			if(cs.strokeOpacity)
				style.opacity = cs.strokeOpacity;
			if(cs.strokeWeight)
				style.weight = cs.strokeWeight;
			if(cs.fillOpacity)
				style.fillOpacity = cs.fillOpacity;
			if(cs.fillColor)
				style.fillColor = cs.fillColor;
		}
		return style;
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
				latlng(minx,64,minz),
				latlng(maxx,64,minz),
				latlng(maxx,64,maxz),
				latlng(minx,64,maxz)
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
			llist[i] = latlng(xarray[i], 64, zarray[i]);
		}
		return new L.Polygon(llist, style);
	}
	
	function createPopupContent(name, region) {
		function join(a) {
			if (a instanceof Array) {
				return a.join(', ');
			} else if (typeof a === 'string') {
				return a;
			} else {
				return "";
			}
		}
		var members = region.members || {};
		var popup = this.infowindow || '<div class="infowindow"><span style="font-size:120%;">%regionname%</span><br /> Owner <span style="font-weight:bold;">%playerowners%</span><br />Flags<br /><span style="font-weight:bold;">%flags%</span></div>';
		popup = popup.replace('%regionname%', name);
		popup = popup.replace('%playerowners%', join(region.owners.players));
		popup = popup.replace('%groupowners%', join(region.owners.groups));
		popup = popup.replace('%playermanagers%', join(region.associates || ""));
		popup = popup.replace('%playermembers%', join(members.players));
		popup = popup.replace('%groupmembers%', join(members.groups));
		popup = popup.replace('%parent%', region.parent || "");
		popup = popup.replace('%priority%', region.priority || "");
		popup = popup.replace('%nation%', region.nation || "");
		var regionflags = "";
		$.each(region.flags, function(name, value) {
			regionflags = regionflags + "<span>" + name + ": " + value + "</span><br>";
		});
		popup = popup.replace('%flags%', regionflags);
		return $('<div/>').addClass('regioninfo')
			.append(popup)[0];
	}
	
	var self = this;
	loadcss('css/regions.css');
	var regionType = configuration.name;
	loadjs('js/regions_' + regionType + '.js', function() {
		var activeLayer = undefined;
		function undraw() {
			if (activeLayer) {
				dynmap.layercontrol.removeLayer(activeLayer);
				dynmap.map.removeLayer(activeLayer);
				activeLayer = undefined;
			}
		}
		function redraw() {
			undraw();
			var worldName = dynmap.world && dynmap.world.name;
			if (worldName) {
				regionConstructors[regionType](dynmap, $.extend({}, configuration, {
						component: self,
						worldName: worldName,
						createPopupContent: createPopupContent,
						createBoxLayer: configuration.use3dregions ? create3DBoxLayer : create2DBoxLayer,
						createOutlineLayer: configuration.use3dregions ? create3DOutlineLayer : create2DOutlineLayer,
						getStyle: getStyle,
						result: function(regionsLayer) {
							activeLayer = regionsLayer;
							dynmap.map.addLayer(activeLayer);
							dynmap.layercontrol.addOverlay(activeLayer, regionType);
						}
					}));
			}
		}
		$(dynmap).bind('mapchanged', redraw);
		$(dynmap).bind('mapchanging', undraw);
		redraw();
	});
}