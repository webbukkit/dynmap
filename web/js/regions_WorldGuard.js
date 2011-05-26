regionConstructors['polygon'] = function(map, name, region)
{
	if(!region.min || !region.max)
		return;
	if(region.max.y > 64)
		region.max.y = 64;
	if(regionCfg.use3dregions)
	{
		regionPolygons[name+'_bottom'] = new google.maps.Polygon($.extend(regionCfg.regionstyle, {
			paths: [
			map.getProjection().fromWorldToLatLng(region.min.x,region.min.y,region.min.z),
			map.getProjection().fromWorldToLatLng(region.max.x,region.min.y,region.min.z),
			map.getProjection().fromWorldToLatLng(region.max.x,region.min.y,region.max.z),
			map.getProjection().fromWorldToLatLng(region.min.x,region.min.y,region.max.z)
			],
			map: map
			}));
		regionPolygons[name+'_top'] = new google.maps.Polygon($.extend(regionCfg.regionstyle, {
			paths: [
			map.getProjection().fromWorldToLatLng(region.min.x,region.max.y,region.min.z),
			map.getProjection().fromWorldToLatLng(region.max.x,region.max.y,region.min.z),
			map.getProjection().fromWorldToLatLng(region.max.x,region.max.y,region.max.z),
			map.getProjection().fromWorldToLatLng(region.min.x,region.max.y,region.max.z)
			],
			map: map
			}));
		regionPolygons[name+'_east'] = new google.maps.Polygon($.extend(regionCfg.regionstyle, {
			paths: [
			map.getProjection().fromWorldToLatLng(region.min.x,region.min.y,region.min.z),
			map.getProjection().fromWorldToLatLng(region.min.x,region.max.y,region.min.z),
			map.getProjection().fromWorldToLatLng(region.max.x,region.max.y,region.min.z),
			map.getProjection().fromWorldToLatLng(region.max.x,region.min.y,region.min.z)
			],
			map: map
			}));
		regionPolygons[name+'_south'] = new google.maps.Polygon($.extend(regionCfg.regionstyle, {
			paths: [
			map.getProjection().fromWorldToLatLng(region.max.x,region.min.y,region.min.z),
			map.getProjection().fromWorldToLatLng(region.max.x,region.max.y,region.min.z),
			map.getProjection().fromWorldToLatLng(region.max.x,region.max.y,region.max.z),
			map.getProjection().fromWorldToLatLng(region.max.x,region.min.y,region.max.z)
			],
			map: map
			}));
		regionPolygons[name+'_west'] = new google.maps.Polygon($.extend(regionCfg.regionstyle, {
			paths: [
			map.getProjection().fromWorldToLatLng(region.min.x,region.min.y,region.max.z),
			map.getProjection().fromWorldToLatLng(region.min.x,region.max.y,region.max.z),
			map.getProjection().fromWorldToLatLng(region.max.x,region.max.y,region.max.z),
			map.getProjection().fromWorldToLatLng(region.max.x,region.min.y,region.max.z)
			],
			map: map
			}));
		regionPolygons[name+'_north'] = new google.maps.Polygon($.extend(regionCfg.regionstyle, {
			paths: [
			map.getProjection().fromWorldToLatLng(region.min.x,region.min.y,region.min.z),
			map.getProjection().fromWorldToLatLng(region.min.x,region.max.y,region.min.z),
			map.getProjection().fromWorldToLatLng(region.min.x,region.max.y,region.max.z),
			map.getProjection().fromWorldToLatLng(region.min.x,region.min.y,region.max.z)
			],
			map: map
			}));
		google.maps.event.addListener(regionPolygons[name+'_bottom'] , 'click', function(event) {
			regionInfo(event, name, region);
		});
		google.maps.event.addListener(regionPolygons[name+'_top'] , 'click', function(event) {
			regionInfo(event, name, region);
		});
		google.maps.event.addListener(regionPolygons[name+'_east'] , 'click', function(event) {
			regionInfo(event, name, region);
		});
		google.maps.event.addListener(regionPolygons[name+'_south'] , 'click', function(event) {
			regionInfo(event, name, region);
		});
		google.maps.event.addListener(regionPolygons[name+'_west'] , 'click', function(event) {
			regionInfo(event, name, region);
		});
		google.maps.event.addListener(regionPolygons[name+'_north'] , 'click', function(event) {
			regionInfo(event, name, region);
		});
	}
	else
	{
		regionPolygons[name+'_bottom'] = new google.maps.Polygon($.extend(regionCfg.regionstyle, {
			paths: [
			map.getProjection().fromWorldToLatLng(region.min.x,region.max.y,region.min.z),
			map.getProjection().fromWorldToLatLng(region.max.x,region.max.y,region.min.z),
			map.getProjection().fromWorldToLatLng(region.max.x,region.max.y,region.max.z),
			map.getProjection().fromWorldToLatLng(region.min.x,region.max.y,region.max.z)
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
	var owners = {'players':'', 'groups':''};
	$.each(region.owners, function(type, names)
	{
		$.each(names, function(index, name)
		{
			if(type == 'players')
				owners['players'] += name+' ';
			else if(type == 'groups')
				owners['groups'] += name+' ';
		});
	});
	var members = {'players':'', 'groups':''};
	$.each(region.members, function(type, names)
	{
		$.each(names, function(index, name)
		{
			if(type == 'players')
				members['players'] += name+' ';
			else if(type == 'groups')
				members['groups'] += name+' ';
		});
	});
	var flags = '';
	$.each(region.flags, function(name, value)
	{
		flags += name+': '+value+'<br />';
	});
	
	name = '<span class="regionname">'+name+'</span>';
	owners['players'] = '<span class="playerowners">'+owners['players']+'</span>';
	owners['groups'] = '<span class="groupowners">'+owners['groups']+'</span>';
	members['players'] = '<span class="playermembers">'+members['players']+'</span>';
	members['groups'] = '<span class="groupmembers">'+members['groups']+'</span>';
	var region_parent = (region.parent) ? '<span class="regionparent">'+region.parent+'</span>' : '';
	flags = '<span class="regionflags">'+flags+'</span>';
	var region_priority = '<span class="regionpriority">'+region.priority+'</span>';
	
	var replace = ['%regionname%','%playerowners%','%groupowners%','%playermembers%','%groupmembers%','%parent%','%flags%','%priority%'];
	var by = [name,owners['players'],owners['groups'],members['players'], members['groups'],region_parent,flags,region_priority];
	
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
	
	regionFile = regionCfg.filename.substr(0, regionCfg.filename.lastIndexOf('.'));
	regionFile += '_'+map+'.json';

	$.getJSON('standalone/'+regionFile, function(data)
	{
		var regionnames = '';
		var count = 0;
		$.each(data, function(name, residence)
		{
			count += 1;
			regionnames += ", "+name;
			makeRegionPolygonCube(dynmap.map, name, residence);
		});
	});
}