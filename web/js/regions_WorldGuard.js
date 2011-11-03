regionConstructors['WorldGuard'] = function(dynmap, configuration) {
	// Helper function.
	function createBoxFromRegion(name, region, boxCreator) {
		function ArrayMax( array ) {
		return Math.max.apply( Math, array );
		}
		function ArrayMin( array ) {
			return Math.min.apply( Math, array );
		}
		if(region.points) {
			var i;
			var xs = region.points.map(function(p) { return p.x; });
			var zs = region.points.map(function(p) { return p.z; });
			return boxCreator(ArrayMax(xs), ArrayMin(xs), region['max-y'], region['min-y'], ArrayMax(zs), ArrayMin(zs), configuration.getStyle(name));
		}
		if(!region.min || !region.max)
			return null;
		if(region.max.y <= region.min.y)
			region.min.y = region.max.y - 1;
		return boxCreator(region.max.x+1, region.min.x, region.max.y, region.min.y, region.max.z+1, region.min.z, configuration.getStyle(name));
	}

	function createOutlineFromRegion(name, region, outCreator) {
		var xarray = [];
		var zarray = [];
		if(region.points) {
			var i;
			for(i = 0; i < region.points.length; i++) {
				xarray[i] = region.points[i].x;
				zarray[i] = region.points[i].z;
			}
		}
		var ymin = 64;
		var ymax = 64;
		if(region['max-y'])
			ymax = region['max-y'];
		if(region['min-y'])
			ymin = region['min-y'];
		if(ymax < ymin) ymax = ymin;			

		return outCreator(xarray, ymax, ymin, zarray, configuration.getStyle(name));
	}
	
	var regionFile = configuration.filename.substr(0, configuration.filename.lastIndexOf('.'));
	regionFile += '_'+configuration.worldName+'.json';
	$.getJSON('standalone/'+regionFile, function(data) {
		var boxLayers = [];
		$.each(data, function(name, region) {
			// Only handle cuboids for the moment (therefore skipping 'global')
			if (region.type === 'cuboid') {
				var boxLayer = createBoxFromRegion(name, region, configuration.createBoxLayer);
				// Skip errorous regions.
				if (boxLayer) {
					boxLayer.bindPopup(configuration.createPopupContent(name, region));
					
					boxLayers.push(boxLayer);
				}
			}
			else if(region.type === 'poly2d') {
				var outLayer = createOutlineFromRegion(name, region, configuration.createOutlineLayer);
				if (outLayer) {
				    outLayer.bindPopup(configuration.createPopupContent(name, region));
				    boxLayers.push(outLayer);
				}
			}
		});
		configuration.result(new L.LayerGroup(boxLayers));
		
	});
};