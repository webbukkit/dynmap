var KzedProjection = DynmapProjection.extend({
	fromLocationToLatLng: function(location) {
		var dx = location.x;
		var dy = location.y - 127;
		var dz = location.z;
		var px = dx + dz;
		var py = dx - dz - dy;
		var scale = 2 << this.options.extrazoom;

		var lat = px / scale - 64;
		var lng = py / scale;
		return new L.LatLng(-lat, lng, true);
	}
});

var KzedMapType = DynmapTileLayer.extend({
	options: {
		minZoom: 0,
		maxZoom: 4
	},
	initialize: function(options) {
		options.maxZoom = options.mapzoomin + options.world.extrazoomout;
		L.Util.setOptions(this, options);
		this.projection = new KzedProjection({extrazoom: this.options.world.extrazoomout});
	},
	getTileName: function(tilePoint, zoom) {
		var tileSize = 128;
		var tileName = '';
        var dnprefix = '';
		
        if(this.options.nightandday && this.options.dynmap.serverday) {
            dnprefix = '_day';
        }
		var extrazoom = this.options.world.extrazoomout;
		if (zoom <= extrazoom) {
			var zpre = 'zzzzzzzzzzzzzzzz'.substring(0, extrazoom-zoom);
			// Most zoomed out tiles.
			var tilescale = 2 << (extrazoom-zoom);
            if (this.options.bigmap) {
                if(zoom < extrazoom) zpre = zpre + '_';
                tileName = 'z' + this.options.prefix + dnprefix + '/' + ((-tilePoint.x * tileSize*tilescale)>>12) + '_' + ((tilePoint.y * tileSize*tilescale) >> 12) + '/' + zpre + (-tilePoint.x * tileSize*tilescale) + '_' + (tilePoint.y * tileSize*tilescale) + '.png';
            } else {
                tileName = zpre + 'z' + this.options.prefix + dnprefix + '_' + (-tilePoint.x * tileSize*tilescale) + '_' + (tilePoint.y * tileSize*tilescale) + '.png';
            }
		} else {
            if(this.options.bigmap) {
                tileName = this.options.prefix + dnprefix + '/' + ((-tilePoint.x*tileSize) >> 12) + '_' + ((tilePoint.y*tileSize)>>12) + '/' + (-tilePoint.x*tileSize) + '_' + (tilePoint.y*tileSize) + '.png';
            } else {
                tileName = this.options.prefix + dnprefix + '_' + (-tilePoint.x*tileSize) + '_' + (tilePoint.y*tileSize) + '.png';
            }
		}
		return tileName;
	},
	calculateTileSize: function(zoom) {
		var extrazoomout = this.options.dynmap.world.extrazoomout;
		return (zoom <= extrazoomout)
				? 128
				: Math.pow(2, 6+zoom-extrazoomout);
	}
});

maptypes.KzedMapType = function(configuration) { return new KzedMapType(configuration); };