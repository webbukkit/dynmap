regionConstructors['Residence'] = function(dynmap, configuration) {
	// Helper function.
	function createBoxFromArea(area, boxCreator) {
		return boxCreator(area.X1, area.X2, area.Y1, area.Y2, area.Z1, area.Z2);
	}

	$.getJSON('standalone/res_' + configuration.worldName + '.json', function(data) {
		var boxLayers = [];
		$.each(data, function(name, residence) {
			if(configuration.worldName == residence.Permissions.World) {
				$.each(residence.Areas, function(name, area) {
					var boxLayer = configuration.createBoxLayer(area.X1, area.X2, area.Y1, area.Y2, area.Z1, area.Z2);
					
					boxLayer.bindPopup(configuration.createPopupContent(name, $.extend(residence, {
						owners: { players: [residence.Permissions.Owner] },
						flags: residence.Permissions.AreaFlags
					})));
					
					boxLayers.push(boxLayer);
				});
				$.each(residence.Subzones, function(szname, subzone) {
					$.each(subzone.Areas, function(name2, area2) {
						var subzoneLayer = configuration.createBoxLayer(area2.X1, area2.X2, area2.Y1, area2.Y2, area2.Z1, area2.Z2);
						subzoneLayer.bindPopup(configuration.createPopupContent(name2, $.extend(subzone, {
							owners: { players: [subzone.Permissions.Owner] },
							flags: subzone.Permissions.AreaFlags
						})));
						boxLayers.push(subzoneLayer);
					});
				});	
			}
		});
		configuration.result(new L.LayerGroup(boxLayers));
		
	});
};
