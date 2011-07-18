var HDProjection = DynmapProjection.extend({
	fromLocationToLatLng: function(location) {
		var wtp = this.options.worldtomap;
		var xx = wtp[0]*location.x + wtp[1]*location.y + wtp[2]*location.z;
		var yy = wtp[3]*location.x + wtp[4]*location.y + wtp[5]*location.z;
		var lat = xx / (8 << this.options.extrazoom);
		var lng = (128-yy) / (8 << this.options.extrazoom);
		return new L.LatLng(lat, lng, true);
	}
});

var HDMapType = DynmapTileLayer.extend({
	projection: undefined,
	options: {
		minZoom: 0,
		maxZoom: 3
	},
	initialize: function(options) {
		options.maxZoom = options.mapzoomin + options.world.extrazoomout;
		L.Util.setOptions(this, options);
		this.projection = new HDProjection({worldtomap: options.worldtomap})
	},
	getTileName: function(tilePoint, zoom) {
        var tileName;
        
        var dnprefix = '';
        if(this.options.nightandday && this.options.dynmap.serverday)
            dnprefix = '_day';

        var extrazoom = this.options.mapzoomout;
        if(zoom < extrazoom) {
        	var scale = 1 << (extrazoom-zoom);
        	var zprefix = "zzzzzzzzzzzzzzzzzzzzzz".substring(0, extrazoom-zoom);
	        tileName = this.options.prefix + dnprefix + '/' + ((scale*tilePoint.x) >> 5) + '_' + ((-scale*tilePoint.y) >> 5) + '/' + zprefix + "_" + (scale*tilePoint.x) + '_' + (-scale*tilePoint.y) + '.png';
        } else {
	        tileName = this.options.prefix + dnprefix + '/' + (tilePoint.x >> 5) + '_' + ((-tilePoint.y) >> 5) + '/' + tilePoint.x + '_' + (-tilePoint.y) + '.png';
    	}
		return tileName;
	},
	calculateTileSize: function(zoom) {
		var extrazoom = this.options.mapzoomout;
		console.log(zoom <= extrazoom, zoom, extrazoom);
		return (zoom <= extrazoom)
				? 128
				: Math.pow(2, 7+zoom-extrazoom);
				//128;
	}
});

maptypes.HDMapType = function(options) { return new HDMapType(options); };
