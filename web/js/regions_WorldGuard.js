Array.max = function( array ){
    return Math.max.apply( Math, array );
};
Array.min = function( array ){
    return Math.min.apply( Math, array );
};

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

function createPolygonsFromWorldGuardRegion(latlng, name, region)
{
	if(region.points) {
		var i;
		var xs = region.points.map(function(p) { return p.x; });
		var zs = region.points.map(function(p) { return p.z; });
		return createPolygonSurfaces(latlng, Array.max(xs), Array.min(xs), region['max-y'], region['min-y'], Array.max(zs), Array.min(zs));
	}
	if(!region.min || !region.max)
		return [];
	if(region.max.y <= region.min.y)
		region.min.y = region.max.y - 1;
	return createPolygonSurfaces(latlng, region.max.x, region.min.x, region.max.y, region.min.y, region.max.z, region.min.z);
}

function createPopupContent(name, region) {
	
	return $('<div/>')
		.append($('<span/>').addClass('regionname').text(name))
		.append(region.owners.players && $('<span/>').addClass('playerowners').text(region.owners.players.concat()))
		.append(region.owners.groups && $('<span/>').addClass('groupowners').text(region.owners.groups.concat()))
		.append(region.members.players && $('<span/>').addClass('playermembers').text(region.members.players.concat()))
		.append(region.members.groups && $('<span/>').addClass('groupmembers').text(region.members.groups.concat()))
		.append(region.parent && $('<span/>').addClass('regionparent').text(region.parent))
		.append(region.flags && function() {
			var regionflags = $('<span/>').addClass('regionflags');
			$.each(region.flags, function(name, value) {
				regionflags.append($('<span/>').addClass('regionflag').text(name + ': ' + value));
			});
			return regionflags;
		}())
		.append($('<span/>').addClass('regionpriority').text(region.priority))
		[0];
};

regionConstructors['WorldGuard'] = function(dynmap, worldName, result) {
	var latlng = function(x, y, z) {
		var l;
		if (typeof x === 'Object' && !y && !z) {
			l = x;
		} else {
			l = new Location(undefined, x,y,z);
		}
		return dynmap.getProjection().fromLocationToLatLng(l);
	};
	
	regionFile = regionCfg.filename.substr(0, regionCfg.filename.lastIndexOf('.'));
	regionFile += '_'+worldName+'.json';
	$.getJSON('standalone/'+regionFile, function(data) {
		var regionLayers = {};
		$.each(data, function(name, region) {
			var polygons = createPolygonsFromWorldGuardRegion(latlng, name, region);
			var regionLayer = new L.FeatureGroup(polygons);
			
			regionLayer.bindPopup(createPopupContent(name, region));
			
			regionLayers[name] = regionLayer;
		});
		result(regionLayers);
		
	});
};