var HDProjection = DynmapProjection.extend({
	fromLocationToLatLng: function(location) {
		var wtp = this.options.worldtomap;
		var xx = wtp[0]*location.x + wtp[1]*location.y + wtp[2]*location.z;
		var yy = wtp[3]*location.x + wtp[4]*location.y + wtp[5]*location.z;
		return new L.LatLng(
			  xx       / (1 << this.options.mapzoomout)
			, (128-yy) / (1 << this.options.mapzoomout)
			, true);
	},
	fromLatLngToLocation: function(latlon, y) {
		var ptw = this.options.maptoworld;
		var lat = latlon.lat * (1 << this.options.mapzoomout);
		var lon = 128 - latlon.lng * (1 << this.options.mapzoomout);
		var x = ptw[0]*lat + ptw[1]*lon + ptw[2]*y;
		var z = ptw[6]*lat + ptw[7]*lon + ptw[8]*y;
		return { x: x, y: y, z: z };
	}
	
});

var HDMapType = DynmapTileLayer.extend({
	projection: undefined,
	options: {
		minZoom: 0,
		maxZoom: 0,
		errorTileUrl: 'images/blank.png',
		continuousWorld: true
	},
	initialize: function(options) {
		options.maxZoom = options.mapzoomin + options.mapzoomout;
		L.Util.setOptions(this, options);
		this.projection = new HDProjection($.extend({map: this}, options));
	},
	getTileName: function(tilePoint, zoom) {
		var info = this.getTileInfo(tilePoint, zoom);
		// Y is inverted for HD-map.
		info.y = -info.y;
		info.scaledy = info.y >> 5;
		return namedReplace('{prefix}{nightday}/{scaledx}_{scaledy}/{zoom}{x}_{y}.{fmt}', info);
	},
	zoomprefix: function(amount) {
		// amount == 0 -> ''
		// amount == 1 -> 'z_'
		// amount == 2 -> 'zz_'
		return 'zzzzzzzzzzzzzzzzzzzzzz'.substr(0, amount) + (amount === 0 ? '' : '_');
	}
});

maptypes.HDMapType = function(options) { return new HDMapType(options); };
