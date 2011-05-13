function FlatProjection() {}
FlatProjection.prototype = {
		fromLatLngToPoint: function(latLng) {
			return new google.maps.Point(latLng.lat()*128.0, latLng.lng()*128.0);
		},
		fromPointToLatLng: function(point) {
			return new google.maps.LatLng(point.x/128.0, point.y/128.0);
		},
		fromWorldToLatLng: function(x, y, z) {
			return new google.maps.LatLng(-z / 128.0, x / 128.0);
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
        
        tileName = this.prefix + '_128_' + coord.x + '_' + coord.y + '.png';
        
        imgSize = Math.pow(2, 6+zoom);
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
        size = Math.pow(2, 6+zoom);
        this.tileSize = new google.maps.Size(size, size);
	}
});

maptypes.FlatMapType = function(configuration) { return new FlatMapType(configuration); };