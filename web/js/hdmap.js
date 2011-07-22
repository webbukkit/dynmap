function HDProjection() {}
HDProjection.prototype = {
		extrazoom: 0,
		worldtomap: null,
		fromLatLngToPoint: function(latLng) {
			return new google.maps.Point(latLng.lng()*config.tileWidth, latLng.lat()*config.tileHeight);
		},
		fromPointToLatLng: function(point) {
			return new google.maps.LatLng( point.y/config.tileHeight, point.x/config.tileWidth);
		},
		fromWorldToLatLng: function(x, y, z) {
		    var wtp = this.worldtomap;
			var xx = wtp[0]*x + wtp[1]*y + wtp[2]*z;
			var yy = wtp[3]*x + wtp[4]*y + wtp[5]*z;
			
			return new google.maps.LatLng( (1 - (yy / config.tileHeight)) / (1 << this.extrazoom), xx / config.tileWidth / (1 << this.extrazoom));
		}
};

function HDMapType(configuration) {
	$.extend(this, configuration); }
HDMapType.prototype = $.extend(new DynMapType(), {
	constructor: HDMapType,
	projection: new HDProjection(),
	tileSize: new google.maps.Size(128.0, 128.0),
	minZoom: 0,
	maxZoom: 2,
	prefix: null,
	getTile: function(coord, zoom, doc) {
		var	tileSize = 128;
        var imgSize;
        var tileName;
        
        var dnprefix = '';
        var map = this.dynmap.map.mapTypes[this.dynmap.map.mapTypeId];
        if(map.nightandday && this.dynmap.serverday)
            dnprefix = '_day';

        var extrazoom = map.mapzoomout;
        if(zoom < extrazoom) {
        	var scale = 1 << (extrazoom-zoom);
        	var zprefix = "zzzzzzzzzzzzzzzzzzzzzz".substring(0, extrazoom-zoom);
	        tileName = this.prefix + dnprefix + '/' + ((scale*coord.x) >> 5) + '_' + ((-scale*coord.y) >> 5) + 
                	'/' + zprefix + "_" + (scale*coord.x) + '_' + (-scale*coord.y) + '.png';
        	imgSize = 128;
        }
        else {
	        tileName = this.prefix + dnprefix + '/' + (coord.x >> 5) + '_' + ((-coord.y) >> 5) + 
                	'/' + coord.x + '_' + (-coord.y) + '.png';
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
		var extrazoom = this.mapzoomout;
		var mapzoomin = this.mapzoomin;
		this.projection.extrazoom = extrazoom;
		this.projection.worldtomap = this.worldtomap;
		this.maxZoom = mapzoomin + extrazoom;
		if (zoom <= extrazoom) {
        	size = 128;
    	}
    	else {
        	size = Math.pow(2, 7+zoom-extrazoom);
    	}
        this.tileSize = new google.maps.Size(size, size);
	}
});

maptypes.HDMapType = function(configuration) { return new HDMapType(configuration); };