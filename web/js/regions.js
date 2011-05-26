// Author: nidefawl. contact me at bukkit.org or irc.esper.net #nide

var regionCfg;
var regionPolygons = {} ;
var regionInfoWindow = new google.maps.InfoWindow();
var regionConstructors = {};
function makeRegionPolygonCube(map, name, region)
{
	new regionConstructors['polygon'](map, name, region);
}
function regionInfo(event, name, region)
{
	new regionConstructors['info'](event, name, region);
}
componentconstructors['regions'] = function(dynmap, configuration)
{
	regionCfg = configuration;

	loadjs('js/regions_' + regionCfg.name + '.js', function()
	{
		var world_info = dynmap.map.mapTypeId.split('.');
		new regionConstructors['update'](world_info[0]);
		
		$(dynmap).bind('mapchanged', function() {
			var world_info = dynmap.map.mapTypeId.split('.');
			new regionConstructors['update'](world_info[0]); 
		});
	});
}

function arrayReplace(replace, by, str)
{
	for (var i=0; i<replace.length; i++)
		str = str.replace(replace[i], by[i]);
	return str;
} 