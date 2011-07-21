regionConstructors['Residence'] = function(dynmap, configuration) {
	// Helper function.
	function createBoxFromArea(area, boxCreator) {
		return boxCreator(area.X1, area.X2, area.Y1, area.Y2, area.Z1, area.Z2);
	}

	$.getJSON('standalone/res_' + worldName + '.json', function(data) {
		var boxLayers = [];
		$.each(data, function(name, residence) {
			if(worldName == residence.Permissions.World) {
				$.each(residence.Areas, function(name, area) {
					var boxLayer = configuration.createBoxLayer(area.X1, area.X2, area.Y1, area.Y2, area.Z1, area.Z2);
					
					boxLayer.bindPopup(configuration.createPopupContent(name, $.extend(residence, {
						owners: { players: [residence.Permissions.Owner] },
						flags: region.Permissions.AreaFlags
					})));
					
					boxLayers.push(boxLayer);
				});
			}
		});
		configuration.result(new L.LayerGroup(boxLayers));
		
	});
};
