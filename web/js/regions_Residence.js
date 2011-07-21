function createPolygonsFromResidence(latlng, name, residence) {
	return createPolygonSurfaces(residence.X1, residence.X2, residence.Y1, residence.Y2, residence.Z1, residence.Z2);
}
function createPopupContent(name, region) {
	return $('<div/>')
		.append($('<span/>').addClass('regionname').text(name))
		.append(region.owners.players && $('<span/>').addClass('playerowners').text(region.permissions.owner))
		[0];
};

regionConstructors['Residence'] = function(dynmap, worldName, result) {
	var latlng = function(x, y, z) {
		return dynmap.getProjection().fromLocationToLatLng(new Location(undefined, x,y,z));
	};
	
	$.getJSON('standalone/res_' + worldName + '.json', function(data) {
		var regionLayers = {};
		$.each(data, function(name, residence) {
			if(map === residence.Permissions.World) {
				var polygons = createPolygonsFromResidence(latlng, name, residence);
				var regionLayer = new L.FeatureGroup(polygons);
				
				regionLayer.bindPopup(createPopupContent(name, region));
				
				regionLayers[name] = regionLayer;
			}
		});
		result(regionLayers);
		
	});
};
