var regionConstructors = {};

componentconstructors['regions'] = function(dynmap, configuration) {
	
	// Compatibility with older configurations.
	if (configuration.regionstyle) {
		configuration.regionstyle = $.extend({
			stroke: true,
			color: configuration.strokeColor,
			opacity: configuration.strokeOpacity,
			weight: configuration.strokeWeight,
			fill: true
		}, configuration.regionstyle);
	}
	
	// Helper functions
	latlng = function(x, y, z) {
		return dynmap.getProjection().fromLocationToLatLng(new Location(undefined, x,y,z));
	}
	
	function create3DBoxLayer(maxx, minx, maxy, miny, maxz, minz) {
		return new L.FeatureGroup([
			new L.Polygon([
				latlng(minx,miny,minz),
				latlng(maxx,miny,minz),
				latlng(maxx,miny,maxz),
				latlng(minx,miny,maxz)
				], configuration.regionstyle),
			new L.Polygon([
				latlng(minx,maxy,minz),
				latlng(maxx,maxy,minz),
				latlng(maxx,maxy,maxz),
				latlng(minx,maxy,maxz)
				], configuration.regionstyle),
			new L.Polygon([
				latlng(minx,miny,minz),
				latlng(minx,maxy,minz),
				latlng(maxx,maxy,minz),
				latlng(maxx,miny,minz)
				], configuration.regionstyle),
			new L.Polygon([
				latlng(maxx,miny,minz),
				latlng(maxx,maxy,minz),
				latlng(maxx,maxy,maxz),
				latlng(maxx,miny,maxz)
				], configuration.regionstyle),
			new L.Polygon([
				latlng(minx,miny,maxz),
				latlng(minx,maxy,maxz),
				latlng(maxx,maxy,maxz),
				latlng(maxx,miny,maxz)
				], configuration.regionstyle),
			new L.Polygon([
				latlng(minx,miny,minz),
				latlng(minx,maxy,minz),
				latlng(minx,maxy,maxz),
				latlng(minx,miny,maxz)
				], configuration.regionstyle)
			]);
	}
	
	function create2DBoxLayer(maxx, minx, maxy, miny, maxz, minz) {
		return new L.Polygon([
				latlng(minx,64,minz),
				latlng(maxx,64,minz),
				latlng(maxx,64,maxz),
				latlng(minx,64,maxz)
				], configuration.regionstyle);
	}

	function create3DOutlineLayer(xarray, maxy, miny, zarray) {
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
			polylist[i] = new L.Polygon(sidelist, configuration.regionstyle);
		}
		polylist[xarray.length] = new L.Polygon(botlist, configuration.regionstyle);
		polylist[xarray.length+1] = new L.Polygon(toplist, configuration.regionstyle);
		
		return new L.FeatureGroup(polylist);
	}

	function create2DOutlineLayer(xarray, maxy, miny, zarray) {
		var llist = [];
		var i;
		for(i = 0; i < xarray.length; i++) {
			llist[i] = latlng(xarray[i], 64, zarray[i]);
		}
		return new L.Polygon(llist, configuration.regionstyle);
	}
	
	function createPopupContent(name, region) {
		function join(a) {
			if (a instanceof Array) {
				return a.join(', ');
			} else if (typeof a === 'string') {
				return a;
			}
			return null;
		}
		var members = region.members || {};
		return $('<div/>').addClass('regioninfo')
			.append($('<span/>').addClass('regionname').text(name))
			.append($('<span/>').addClass('owners')
				.append(region.owners.players && $('<span/>').addClass('playerowners').text(join(region.owners.players)))
				.append(region.owners.groups && $('<span/>').addClass('groupowners').text(join(region.owners.groups)))
				)
			.append($('<span/>').addClass('members')
				.append(members.players && $('<span/>').addClass('playermembers').text(join(members.players)))
				.append(members.groups && $('<span/>').addClass('groupmembers').text(join(members.groups)))
				)
			.append(region.parent && $('<span/>').addClass('regionparent').text(region.parent))
			.append(region.flags && function() {
				var regionflags = $('<span/>').addClass('regionflags');
				$.each(region.flags, function(name, value) {
					regionflags.append($('<span/>').addClass('regionflag').text(name + ': ' + value));
				});
				return regionflags;
			}())
			.append($('<span/>').addClass('regionpriority').text(region.priority))
			[0];
	}
	
	var self = this;
	loadcss('css/regions.css');
	var regionType = configuration.name;
	loadjs('js/regions_' + regionType + '.js', function() {
		var activeLayer = undefined;
		function undraw() {
			if (activeLayer) {
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
						result: function(regionsLayer) {
							activeLayer = regionsLayer;
							dynmap.map.addLayer(activeLayer);
						}
					}));
			}
		}
		$(dynmap).bind('mapchanged', redraw);
		$(dynmap).bind('mapchanging', undraw);
		redraw();
	});
}