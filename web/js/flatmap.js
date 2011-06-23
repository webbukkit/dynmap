function FlatProjection() {}
FlatProjection.prototype = {
		extrazoom: 0,
		fromLatLngToPoint: function(latLng) {
			return new google.maps.Point(latLng.lat()*config.tileWidth, latLng.lng()*config.tileHeight);
		},
		fromPointToLatLng: function(point) {
			return new google.maps.LatLng(point.x/config.tileWidth, point.y/config.tileHeight);
		},
		fromWorldToLatLng: function(x, y, z) {
			return new google.maps.LatLng(-z / config.tileWidth / (1 << this.extrazoom), x / config.tileHeight / (1 << this.extrazoom));
		}
};

function FlatMapType(configuration) {
	$.extend(this, configuration); }
FlatMapType.prototype = $.extend(new DynMapType(), {
	constructor: FlatMapType,
	projection: new FlatProjection(),
	tileSize: new google.maps.Size(128.0, 128.0),
	minZoom: 0,
	maxZoom: 3,
	prefix: null,
	getTile: function(coord, zoom, doc) {
		var	tileSize = 128;
        var imgSize;
        var tileName;
        
        var dnprefix = '';
        if(this.dynmap.map.mapTypes[this.dynmap.map.mapTypeId].nightandday && this.dynmap.serverday)
            dnprefix = '_day';
        var extrazoom = this.dynmap.world.extrazoomout;
        if(zoom < extrazoom) {
        	var scale = 1 << (extrazoom-zoom);
        	var zprefix = "zzzzzzzzzzzz".substring(0, extrazoom-zoom);
	        if(this.dynmap.world.bigworld)
    	        tileName = this.prefix + dnprefix + '_128/' + ((scale*coord.x) >> 5) + '_' + ((scale*coord.y) >> 5) + 
                	'/' + zprefix + "_" + (scale*coord.x) + '_' + (scale*coord.y) + '.png';
        	else
            	tileName = zprefix + this.prefix + dnprefix + '_128_' + (scale*coord.x) + '_' + (scale*coord.y) + '.png';
        	imgSize = 128;
        }
        else {
	        if(this.dynmap.world.bigworld)
    	        tileName = this.prefix + dnprefix + '_128/' + (coord.x >> 5) + '_' + (coord.y >> 5) + 
                	'/' + coord.x + '_' + coord.y + '.png';
        	else
            	tileName = this.prefix + dnprefix + '_128_' + coord.x + '_' + coord.y + '.png';
        	imgSize = Math.pow(2, 7+zoom-extrazoom);
    	}
		var tile = $('<div/>')
			.addClass('tile')
			.css({
				width: tileSize + 'px',
				height: tileSize + 'px'
			});		
        var img = $('<img/>')
			.attr('src', this.dynmap.getTileUrl(tileName))
			.error(function() { img.hide(); })
			.bind('load', function() { img.show(); })
			.css({
				width: imgSize +'px',
				height: imgSize + 'px',
				borderStyle: 'none'
			})
			.hide()
            .appendTo(tile);
		this.dynmap.registerTile(this, tileName, img);
		//this.dynmap.unregisterTile(this, tileName);
		return tile.get(0);
	},
	updateTileSize: function(zoom) {
        var size;
		var extrazoom = this.dynmap.world.extrazoomout;
		this.projection.extrazoom = extrazoom;
		this.maxZoom = 3 + extrazoom;
		if (zoom <= extrazoom) {
        	size = 128;
    	}
    	else {
        	size = Math.pow(2, 7+zoom-extrazoom);
    	}
        this.tileSize = new google.maps.Size(size, size);
	}
});

maptypes.FlatMapType = function(configuration) { return new FlatMapType(configuration); };