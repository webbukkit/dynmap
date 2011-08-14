var FlatProjection = DynmapProjection.extend({
	fromLocationToLatLng: function(location) {
		return new L.LatLng(
				-location.z / (1 << this.options.mapzoomout),
				location.x / (1 << this.options.mapzoomout),
				true);
	},
	fromLatLngToLocation: function(latlon, y) {
		var z = -latlon.lat * (1 << this.options.mapzoomout);
		var x = latlon.lng * (1 << this.options.mapzoomout);
		return { x: x, y: y, z: z };
	}
	
});

var FlatMapType = DynmapTileLayer.extend({
	options: {
		minZoom: 0,
		maxZoom: 4,
		errorTileUrl: 'images/blank.png',
		continuousWorld: true
	},
	initialize: function(options) {
		options.maxZoom = options.mapzoomin + options.mapzoomout;
		L.Util.setOptions(this, options);
		this.projection = new FlatProjection({mapzoomout: options.mapzoomout});
	},
	getTileName: function(tilePoint, zoom) {
		return namedReplace(this.options.bigmap
				? '{prefix}{nightday}_128/{scaledx}_{scaledy}/{zoomprefix}{x}_{y}.png'
				: '{zoom}{prefix}{nightday}_128_{x}_{y}.png'
				, this.getTileInfo(tilePoint, zoom));
	}
});

maptypes.FlatMapType = function(options) { return new FlatMapType(options); };