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
		if(this.options.nightandday && this.dynmap.serverday) {
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

/*
function FlatMapType(configuration) {
	$.extend(this, configuration); }
FlatMapType.prototype = $.extend(new DynMapType(), {
	constructor: FlatMapType,
	projection: new FlatProjection(),
	tileSize: 128.0,
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
	        if(this.dynmap.map.mapTypes[this.dynmap.map.mapTypeId].bigmap)
    	        tileName = this.prefix + dnprefix + '_128/' + ((scale*coord.x) >> 5) + '_' + ((scale*coord.y) >> 5) + 
                	'/' + zprefix + "_" + (scale*coord.x) + '_' + (scale*coord.y) + '.png';
        	else
            	tileName = zprefix + this.prefix + dnprefix + '_128_' + (scale*coord.x) + '_' + (scale*coord.y) + '.png';
        	imgSize = 128;
        }
        else {
	        if(this.dynmap.map.mapTypes[this.dynmap.map.mapTypeId].bigmap)
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
		var mapzoomin = this.mapzoomin;
		this.projection.extrazoom = extrazoom;
		this.maxZoom = mapzoomin + extrazoom;
		if (zoom <= extrazoom) {
        	size = 128;
    	}
    	else {
        	size = Math.pow(2, 7+zoom-extrazoom);
    	}
        this.tileSize = size;
	}
});
*/
maptypes.FlatMapType = function(options) { return new FlatMapType(options); };