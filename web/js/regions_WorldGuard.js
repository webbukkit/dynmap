regionConstructors['WorldGuard'] = function(dynmap, configuration) {
	// Helper function.
	function createBoxFromRegion(region, boxCreator) {
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
			return boxCreator(ArrayMax(xs), ArrayMin(xs), region['max-y'], region['min-y'], ArrayMax(zs), ArrayMin(zs));
		}
		if(!region.min || !region.max)
			return null;
		if(region.max.y <= region.min.y)
			region.min.y = region.max.y - 1;
		return boxCreator(region.max.x, region.min.x, region.max.y, region.min.y, region.max.z, region.min.z);
	}

	function createOutlineFromRegion(region, outCreator) {
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

		return outCreator(xarray, ymax, ymin, zarray);
	}
	
	var regionFile = configuration.filename.substr(0, configuration.filename.lastIndexOf('.'));
	regionFile += '_'+configuration.worldName+'.json';
	$.getJSON('standalone/'+regionFile, function(data) {
		var boxLayers = [];
		$.each(data, function(name, region) {
			// Only handle cuboids for the moment (therefore skipping 'global')
			if (region.type === 'cuboid') {
				var boxLayer = createBoxFromRegion(region, configuration.createBoxLayer);
				// Skip errorous regions.
				if (boxLayer) {
					boxLayer.bindPopup(configuration.createPopupContent(name, region));
					
					boxLayers.push(boxLayer);
				}
			}
			else if(region.type === 'poly2d') {
				var outLayer = createOutlineFromRegion(region, configuration.createOutlineLayer);
				if (outLayer) {
				    outLayer.bindPopup(configuration.createPopupContent(name, region));
				    boxLayers.push(outLayer);
				}
			}
		});
		configuration.result(new L.LayerGroup(boxLayers));
		
	});
};