var KzedProjection = DynmapProjection.extend({
	fromLocationToLatLng: function(location) {
		var dx = location.x;
		var dy = location.y - 127;
		var dz = location.z;
		var px = dx + dz;
		var py = dx - dz - dy;
		var scale = 1 << this.options.mapzoomout;

		var xx = (128 - px) / scale;
		var yy = py / scale;
		return new L.LatLng(xx, yy, true);
	}
});

var KzedMapType = DynmapTileLayer.extend({
	options: {
		minZoom: 0,
		maxZoom: 4
	},
	initialize: function(options) {
		options.mapzoomout = options.mapzoomout || options.world.extrazoomout;
		options.maxZoom = options.mapzoomin + options.mapzoomout;
		L.Util.setOptions(this, options);
		this.projection = new KzedProjection({mapzoomout: this.options.mapzoomout});
	},
	getTileName: function(tilePoint, zoom) {
		var info = this.getTileInfo(tilePoint, zoom);
		return namedReplace(this.options.bigmap
				? '{prefix}{nightday}/{scaledx}_{scaledy}/{zoom}_{x}_{y}.png'
				: '{zoom}{prefix}{nightday}_{x}_{y}.png'
				, this.getTileInfo(tilePoint, zoom));
	},
	getTileInfo: function(tilePoint, zoom) {
		// Custom tile-info-calculation for KzedMap: *128 and >>12
		var izoom = this.options.maxZoom - zoom;
		var zoomoutlevel = Math.max(0, izoom - this.options.mapzoomin);
		var scale = 1 << zoomoutlevel;
		var zoomprefix = this.zoomprefix(zoomoutlevel);
		var x = -scale*tilePoint.x*128;
		var y = scale*tilePoint.y*128;
		return {
			prefix: this.options.prefix,
			nightday: (this.options.nightandday && this.options.dynmap.serverday) ? '_day' : '',
			scaledx: x >> 12,
			scaledy: y >> 12,
			zoom: this.zoomprefix(zoomoutlevel),
			x: x,
			y: y
		};
	}
});

maptypes.KzedMapType = function(configuration) { return new KzedMapType(configuration); };