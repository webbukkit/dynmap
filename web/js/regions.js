var regionConstructors = {};

function createPolygonSurfaces(latlng, maxx, minx, maxy, miny, maxz, minz) {
	return [
		new L.Polygon([
			latlng(minx,miny,minz),
			latlng(maxx,miny,minz),
			latlng(maxx,miny,maxz),
			latlng(minx,miny,maxz)
			], {}),
		new L.Polygon([
			latlng(minx,maxy,minz),
			latlng(maxx,maxy,minz),
			latlng(maxx,maxy,maxz),
			latlng(minx,maxy,maxz)
			], {}),
		new L.Polygon([
			latlng(minx,miny,minz),
			latlng(minx,maxy,minz),
			latlng(maxx,maxy,minz),
			latlng(maxx,miny,minz)
			], {}),
		new L.Polygon([
			latlng(maxx,miny,minz),
			latlng(maxx,maxy,minz),
			latlng(maxx,maxy,maxz),
			latlng(maxx,miny,maxz)
			], {}),
		new L.Polygon([
			latlng(minx,miny,maxz),
			latlng(minx,maxy,maxz),
			latlng(maxx,maxy,maxz),
			latlng(maxx,miny,maxz)
			], {}),
		new L.Polygon([
			latlng(minx,miny,minz),
			latlng(minx,maxy,minz),
			latlng(minx,maxy,maxz),
			latlng(minx,miny,maxz)
			], {})
		];
}

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