regionConstructors['Factions'] = function(dynmap, configuration) {
	// Helper function.
	function createOutlineFromRegion(name, region, points, outCreator) {
		var xarray = [];
		var zarray = [];
		var i;
		for(i = 0; i < points.length; i++) {
			xarray[i] = points[i].x;
			zarray[i] = points[i].z;
		}
		var ymin = 64;
		var ymax = 65;

		return outCreator(xarray, ymax, ymin, zarray, configuration.getStyle(name, region.nation));
	}
	
	var regionFile = 'factions_'+configuration.worldName+'.json';
	$.getJSON('standalone/'+regionFile, function(data) {
		var boxLayers = [];
		$.each(data, function(name, region) {
			var i;
			for(i = 0; i < region.points.length; i++) {
				var outLayer = createOutlineFromRegion(name, region, region.points[i], configuration.createOutlineLayer);
				if (outLayer) {
				    outLayer.bindPopup(configuration.createPopupContent(name, 
				    	$.extend(region, {
							owners: { players: [region.mayor] },
							members: { players: [ region.residents ] }
						})));
			    	boxLayers.push(outLayer);
				}
			}
		});
		configuration.result(new L.LayerGroup(boxLayers));
		
	});
};