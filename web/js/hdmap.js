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

/*
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
*/
maptypes.HDMapType = function(options) { return new HDMapType(options); };
