var FlatProjection = DynmapProjection.extend({
	fromLocationToLatLng: function(location) {
		return new L.LatLng(-location.z, location.x, true);
	}
});

var FlatMapType = DynmapTileLayer.extend({
	options: {
		minZoom: 0,
		maxZoom: 4
	},
	initialize: function(options) {
		options.maxZoom = options.mapzoomin + options.world.extrazoomout;
		L.Util.setOptions(this, options);
		this.projection = new FlatProjection({extrazoom: this.options.world.extrazoomout});
	},
	getTileName: function(tilePoint, zoom) {
		var tileName;
		var dnprefix = '';
		if(this.options.nightandday && this.options.dynmap.serverday) {
			dnprefix = '_day';
		}
		var extrazoom = this.options.world.extrazoomout;
		if(zoom < extrazoom) {
        	var scale = 1 << (extrazoom-zoom);
        	var zprefix = "zzzzzzzzzzzz".substring(0, extrazoom-zoom);
	        if(this.options.bigmap) {
    	        tileName = this.options.prefix + dnprefix + '_128/' + ((scale*tilePoint.x) >> 5) + '_' + ((scale*tilePoint.y) >> 5) + '/' + zprefix + "_" + (scale*tilePoint.x) + '_' + (scale*tilePoint.y) + '.png';
			} else {
            	tileName = zprefix + this.options.prefix + dnprefix + '_128_' + (scale*tilePoint.x) + '_' + (scale*tilePoint.y) + '.png';
			}
        }
        else {
	        if(this.options.bigmap) {
    	        tileName = this.options.prefix + dnprefix + '_128/' + (tilePoint.x >> 5) + '_' + (tilePoint.y >> 5) + '/' + tilePoint.x + '_' + tilePoint.y + '.png';
			} else {
            	tileName = this.options.prefix + dnprefix + '_128_' + tilePoint.x + '_' + tilePoint.y + '.png';
			}
    	}
		return tileName;
	},
	calculateTileSize: function(zoom) {
		var extrazoom = this.options.world.extrazoomout;
		return (zoom < extrazoom)
				? 128
				: Math.pow(2, 7+zoom-extrazoom);
	}
});

maptypes.FlatMapType = function(options) { return new FlatMapType(options); };