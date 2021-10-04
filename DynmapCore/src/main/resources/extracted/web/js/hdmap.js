var HDProjection = DynmapProjection.extend({
	fromLocationToLatLng: function(location) {
		var wtp = this.options.worldtomap;
			lat = wtp[3] * location.x + wtp[4] * location.y + wtp[5] * location.z,
			lng = wtp[0] * location.x + wtp[1] * location.y + wtp[2] * location.z;

		return new L.LatLng(
			  -((128 - lat) / (1 << this.options.mapzoomout))
			, lng / (1 << this.options.mapzoomout)
			, location.y);
	},
	fromLatLngToLocation: function(latlon, y) {
		var ptw = this.options.maptoworld,
			lat = 128 + latlon.lat * (1 << this.options.mapzoomout),
			lng = latlon.lng * (1 << this.options.mapzoomout),
			x = ptw[0] * lng + ptw[1] * lat + ptw[2] * y,
			z = ptw[6] * lng + ptw[7] * lat + ptw[8] * y;
		
		return { x: x, y: y, z: z };
	}
});

var HDMapType = DynmapTileLayer.extend({
	projection: undefined,
	options: {
		minZoom: 0,
		errorTileUrl: 'images/blank.png',
		tileSize: 128,
		zoomReverse: true,
	},
	initialize: function(options) {
		options.maxZoom = options.mapzoomin + options.mapzoomout;
		options.maxNativeZoom = options.mapzoomout;

		this.projection = new HDProjection($.extend({map: this}, options));

		L.Util.setOptions(this, options);
		DynmapTileLayer.prototype.initialize.call(this, options);
	},
	getTileName: function(coords) {
		var info = this.getTileInfo(coords);
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
