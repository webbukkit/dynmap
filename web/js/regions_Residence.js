regionConstructors['polygon'] = function(map, resname, region)
{
  $.each(region.Areas, function(aname, area) {
    var name = resname + '_' + aname;
	if(regionCfg.use3dregions)
	{
		regionPolygons[name+'_bottom'] = new google.maps.Polygon($.extend(regionCfg.regionstyle, {
			paths: [
			map.getProjection().fromWorldToLatLng(area.X1,area.Y1,area.Z1),
			map.getProjection().fromWorldToLatLng(area.X2,area.Y1,area.Z1),
			map.getProjection().fromWorldToLatLng(area.X2,area.Y1,area.Z2),
			map.getProjection().fromWorldToLatLng(area.X1,area.Y1,area.Z2)
			],
			map: map
			}));
			google.maps.event.addListener(regionPolygons[name+'_bottom'] , 'click', function(event) {
				regionInfo(event, resname, region);
			});
		
		regionPolygons[name+'_top'] = new google.maps.Polygon($.extend(regionCfg.regionstyle, {
			paths: [
			map.getProjection().fromWorldToLatLng(area.X1,area.Y2,area.Z1),
			map.getProjection().fromWorldToLatLng(area.X2,area.Y2,area.Z1),
			map.getProjection().fromWorldToLatLng(area.X2,area.Y2,area.Z2),
			map.getProjection().fromWorldToLatLng(area.X1,area.Y2,area.Z2)
			],
			map: map
			}));
			google.maps.event.addListener(regionPolygons[name+'_top'] , 'click', function(event) {
				regionInfo(event, resname, region);
			});
	
		regionPolygons[name+'_east'] = new google.maps.Polygon($.extend(regionCfg.regionstyle, {
			paths: [
			map.getProjection().fromWorldToLatLng(area.X1,area.Y1,area.Z1),
			map.getProjection().fromWorldToLatLng(area.X1,area.Y2,area.Z1),
			map.getProjection().fromWorldToLatLng(area.X2,area.Y2,area.Z1),
			map.getProjection().fromWorldToLatLng(area.X2,area.Y1,area.Z1)
			],
			map: map
			}));
			google.maps.event.addListener(regionPolygons[name+'_east'] , 'click', function(event) {
				regionInfo(event, resname, region);
			});
	
		regionPolygons[name+'_south'] = new google.maps.Polygon($.extend(regionCfg.regionstyle, {
			paths: [
			map.getProjection().fromWorldToLatLng(area.X2,area.Y1,area.Z1),
			map.getProjection().fromWorldToLatLng(area.X2,area.Y2,area.Z1),
			map.getProjection().fromWorldToLatLng(area.X2,area.Y2,area.Z2),
			map.getProjection().fromWorldToLatLng(area.X2,area.Y1,area.Z2)
			],
			map: map
			}));
			google.maps.event.addListener(regionPolygons[name+'_south'] , 'click', function(event) {
				regionInfo(event, resname, region);
			});
	
		regionPolygons[name+'_west'] = new google.maps.Polygon($.extend(regionCfg.regionstyle, {
			paths: [
			map.getProjection().fromWorldToLatLng(area.X1,area.Y1,area.Z2),
			map.getProjection().fromWorldToLatLng(area.X1,area.Y2,area.Z2),
			map.getProjection().fromWorldToLatLng(area.X2,area.Y2,area.Z2),
			map.getProjection().fromWorldToLatLng(area.X2,area.Y1,area.Z2)
			],
			map: map
			}));
			google.maps.event.addListener(regionPolygons[name+'_west'] , 'click', function(event) {
				regionInfo(event, resname, region);
			});
	
		regionPolygons[name+'_north'] = new google.maps.Polygon($.extend(regionCfg.regionstyle, {
				paths: [
			map.getProjection().fromWorldToLatLng(area.X1,area.Y1,area.Z1),
				map.getProjection().fromWorldToLatLng(area.X1,area.Y2,area.Z1),
			map.getProjection().fromWorldToLatLng(area.X1,area.Y2,area.Z2),
			map.getProjection().fromWorldToLatLng(area.X1,area.Y1,area.Z2)
			],
			map: map
			}));
			google.maps.event.addListener(regionPolygons[name+'_west'] , 'click', function(event) {
				regionInfo(event, resname, region);
			});
	}
	else
	{
		middleY = area.Y2;
		regionPolygons[name+'_bottom'] = new google.maps.Polygon($.extend(regionCfg.regionstyle, {
			paths: [
			map.getProjection().fromWorldToLatLng(area.X1,middleY,area.Z1),
			map.getProjection().fromWorldToLatLng(area.X2,middleY,area.Z1),
			map.getProjection().fromWorldToLatLng(area.X2,middleY,area.Z2),
			map.getProjection().fromWorldToLatLng(area.X1,middleY,area.Z2)
			],
			map: map
			}));
			google.maps.event.addListener(regionPolygons[name+'_bottom'] , 'click', function(event) {
				regionInfo(event, resname, region);
			});
	}
  });
}
regionConstructors['info'] = function(event, name, region)
{
	var flags = "";
	$.each(region.Permissions.AreaFlags, function(flag, status)
	{
		flags += flag+': '+status+'<br />';
	});
	var replace = ['%regionname%','%playerowners%','%flags%','%groupowners%', '%playermembers%', '%groupmembers%','%parent%', '%priority%'];
	var by = [name,region.Permissions.Owner, flags, '', '', '', '', ''];
	
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
	$.getJSON("standalone/res_" + map + ".json", function(data)
	{
		$.each(data, function(name, residence)
		{
			if(map == residence.Permissions.World)
				makeRegionPolygonCube(dynmap.map, name, residence);
		});
	});
}