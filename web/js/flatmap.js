var FlatProjection = DynmapProjection.extend({
	fromLocationToLatLng: function(location) {
		return new L.LatLng(
				-location.z / (1 << this.options.mapzoomout),
				location.x / (1 << this.options.mapzoomout),
				true);
	}
});

var FlatMapType = DynmapTileLayer.extend({
	options: {
		minZoom: 0,
		maxZoom: 4
	},
	initialize: function(options) {
		options.maxzoomout = options.mapzoomout || options.world.extrazoomout;
		options.maxZoom = options.mapzoomin + options.maxzoomout;
		L.Util.setOptions(this, options);
		this.projection = new FlatProjection({mapzoomout: options.mapzoomout});
	},
	getTileName: function(tilePoint, zoom) {
		return namedReplace(this.options.bigmap
				? '{prefix}{nightday}_128/{scaledx}_{scaledy}/{zoom}_{x}_{y}.png'
				: '{zoom}{prefix}{nightday}_128_{x}_{y}.png'
				, this.getTileInfo(tilePoint, zoom));
	}
});

maptypes.FlatMapType = function(options) { return new FlatMapType(options); };