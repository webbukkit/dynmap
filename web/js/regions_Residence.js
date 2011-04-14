regionConstructors['polygon'] = function(map, name, region)
{
	if(regionCfg.regiontype == 'worldguard')
	{
		region.x1 = region.min.x;
		region.y1 = region.min.y;
		region.z1 = region.min.z;
		region.x2 = region.max.x;
		region.y2 = region.max.y;
		region.z2 = region.max.z;
	}
	if(regionCfg.use3dregions)
	{
		regionPolygons[name+'_bottom'] = new google.maps.Polygon($.extend(regionCfg.regionstyle, {
			paths: [
			map.getProjection().fromWorldToLatLng(region.x1,region.y1,region.z1),
			map.getProjection().fromWorldToLatLng(region.x2,region.y1,region.z1),
			map.getProjection().fromWorldToLatLng(region.x2,region.y1,region.z2),
			map.getProjection().fromWorldToLatLng(region.x1,region.y1,region.z2)
			],
			map: map
			}));
			google.maps.event.addListener(regionPolygons[name+'_bottom'] , 'click', function(event) {
				regionInfo(event, name, region);
			});
		
		regionPolygons[name+'_top'] = new google.maps.Polygon($.extend(regionCfg.regionstyle, {
			paths: [
			map.getProjection().fromWorldToLatLng(region.x1,region.y2,region.z1),
			map.getProjection().fromWorldToLatLng(region.x2,region.y2,region.z1),
			map.getProjection().fromWorldToLatLng(region.x2,region.y2,region.z2),
			map.getProjection().fromWorldToLatLng(region.x1,region.y2,region.z2)
			],
			map: map
			}));
			google.maps.event.addListener(regionPolygons[name+'_top'] , 'click', function(event) {
				regionInfo(event, name, region);
			});
	
		regionPolygons[name+'_east'] = new google.maps.Polygon($.extend(regionCfg.regionstyle, {
			paths: [
			map.getProjection().fromWorldToLatLng(region.x1,region.y1,region.z1),
			map.getProjection().fromWorldToLatLng(region.x1,region.y2,region.z1),
			map.getProjection().fromWorldToLatLng(region.x2,region.y2,region.z1),
			map.getProjection().fromWorldToLatLng(region.x2,region.y1,region.z1)
			],
			map: map
			}));
			google.maps.event.addListener(regionPolygons[name+'_east'] , 'click', function(event) {
				regionInfo(event, name, region);
			});
	
		regionPolygons[name+'_south'] = new google.maps.Polygon($.extend(regionCfg.regionstyle, {
			paths: [
			map.getProjection().fromWorldToLatLng(region.x2,region.y1,region.z1),
			map.getProjection().fromWorldToLatLng(region.x2,region.y2,region.z1),
			map.getProjection().fromWorldToLatLng(region.x2,region.y2,region.z2),
			map.getProjection().fromWorldToLatLng(region.x2,region.y1,region.z2)
			],
			map: map
			}));
			google.maps.event.addListener(regionPolygons[name+'_south'] , 'click', function(event) {
				regionInfo(event, name, region);
			});
	
		regionPolygons[name+'_west'] = new google.maps.Polygon($.extend(regionCfg.regionstyle, {
			paths: [
			map.getProjection().fromWorldToLatLng(region.x1,region.y1,region.z2),
			map.getProjection().fromWorldToLatLng(region.x1,region.y2,region.z2),
			map.getProjection().fromWorldToLatLng(region.x2,region.y2,region.z2),
			map.getProjection().fromWorldToLatLng(region.x2,region.y1,region.z2)
			],
			map: map
			}));
			google.maps.event.addListener(regionPolygons[name+'_west'] , 'click', function(event) {
				regionInfo(event, name, region);
			});
	
		regionPolygons[name+'_north'] = new google.maps.Polygon($.extend(regionCfg.regionstyle, {
				paths: [
			map.getProjection().fromWorldToLatLng(region.x1,region.y1,region.z1),
				map.getProjection().fromWorldToLatLng(region.x1,region.y2,region.z1),
			map.getProjection().fromWorldToLatLng(region.x1,region.y2,region.z2),
			map.getProjection().fromWorldToLatLng(region.x1,region.y1,region.z2)
			],
			map: map
			}));
			google.maps.event.addListener(regionPolygons[name+'_west'] , 'click', function(event) {
				regionInfo(event, name, region);
			});
	}
	else
	{
		middleY = region.y2;
		regionPolygons[name+'_bottom'] = new google.maps.Polygon($.extend(regionCfg.regionstyle, {
			paths: [
			map.getProjection().fromWorldToLatLng(region.x1,middleY,region.z1),
			map.getProjection().fromWorldToLatLng(region.x2,middleY,region.z1),
			map.getProjection().fromWorldToLatLng(region.x2,middleY,region.z2),
			map.getProjection().fromWorldToLatLng(region.x1,middleY,region.z2)
			],
			map: map
			}));
			google.maps.event.addListener(regionPolygons[name+'_bottom'] , 'click', function(event) {
				regionInfo(event, name, region);
			});
	}
}
regionConstructors['info'] = function(event, name, region)
{
	if(regionCfg.regiontype == 'residence')
	{
		$.each(region.areaflags, function(flag, status)
		{
			
		});
		var replace = ['%regionname%','%owners%'];
		var by = [name,region.permissions.owner];
	}
	var contentString = arrayReplace(replace, by, regionCfg.infowindow)

	regionInfoWindow.setContent(contentString);
	regionInfoWindow.setPosition(event.latLng);

	regionInfoWindow.open(dynmap.map);
}
regionConstructors['update'] = function(map)
{
	if(regionInfoWindow)
		regionInfoWindow.close();
	$.each(regionPolygons, function(index, region)
	{
		region.setMap(null);
	});
	regionPolygons = {};
	$.getJSON("standalone/res.json", function(data)
	{
		$.each(data, function(name, residence)
		{
			if(map == residence.world)
				makeRegionPolygonCube(dynmap.map, name, residence);
		});
	});
}