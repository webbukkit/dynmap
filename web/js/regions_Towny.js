regionConstructors['Towny'] = function(dynmap, configuration) {
	// Helper function.
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
		var ymax = 65;

		return outCreator(xarray, ymax, ymin, zarray, configuration.getStyle(name, region.nation));
	}
	
	var regionFile = 'towny_'+configuration.worldName+'.json';
	$.getJSON('standalone/'+regionFile, function(data) {
		var boxLayers = [];
		$.each(data, function(name, region) {
			var outLayer = createOutlineFromRegion(name, region, configuration.createOutlineLayer);
			if (outLayer) {
			    outLayer.bindPopup(configuration.createPopupContent(name, 
			    	$.extend(region, {
						owners: { players: [region.mayor] },
						members: { players: [ region.residents ] }
					})));
			    boxLayers.push(outLayer);
			}
		});
		configuration.result(new L.LayerGroup(boxLayers));
		
	});
};