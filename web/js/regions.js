var regionConstructors = {};

componentconstructors['regions'] = function(dynmap, configuration) {
	regionCfg = configuration;
	var regionType = regionCfg.name;
	loadjs('js/regions_' + regionType + '.js', function() {
		var regionsLayer = undefined;
		function undraw() {
			if (regionsLayer) {
				dynmap.map.removeLayer(regionsLayer);
				regionsLayer = undefined;
			}
		}
		function redraw() {
			undraw();
			var worldName = dynmap.world && dynmap.world.name;
			if (worldName) {
				regionConstructors[regionType](dynmap, worldName, function(regionLayers) {
					var newRegionsLayer = new L.LayerGroup();
					$.each(regionLayers, function(name, layer) {
						console.log(name, layer);
						newRegionsLayer.addLayer(layer);
					});
					regionsLayer = newRegionsLayer;
					dynmap.map.addLayer(newRegionsLayer);
				});
			}
		}
		$(dynmap).bind('mapchanged', redraw);
		$(dynmap).bind('mapchanging', undraw);
		redraw();
	});
}