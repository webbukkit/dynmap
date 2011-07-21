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
			return [];
		if(region.max.y <= region.min.y)
			region.min.y = region.max.y - 1;
		return boxCreator(region.max.x, region.min.x, region.max.y, region.min.y, region.max.z, region.min.z);
	}
	
	var regionFile = configuration.filename.substr(0, configuration.filename.lastIndexOf('.'));
	regionFile += '_'+configuration.worldName+'.json';
	$.getJSON('standalone/'+regionFile, function(data) {
		var boxLayers = [];
		$.each(data, function(name, region) {
			var boxLayer = createBoxFromRegion(region, configuration.createBoxLayer);
			
			boxLayer.bindPopup(configuration.createPopupContent(name, region));
			
			boxLayers.push(boxLayer);
		});
		configuration.result(new L.LayerGroup(boxLayers));
		
	});
};