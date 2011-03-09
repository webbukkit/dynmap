function FlatProjection() {}
FlatProjection.prototype = {
		fromLatLngToPoint: function(latLng) {
			return new google.maps.Point(latLng.lat()*128.0, latLng.lng()*128.0);
		},
		fromPointToLatLng: function(point) {
			return new google.maps.LatLng(point.x/128.0, point.y/128.0);
		},
		fromWorldToLatLng: function(x, y, z) {
			return new google.maps.LatLng(x / 128.0, z / 128.0);
		}
};

function FlatMapType(configuration) {
	$.extend(this, configuration); }
FlatMapType.prototype = $.extend(new DynMapType(), {
	constructor: FlatMapType,
	projection: new FlatProjection(),
	tileSize: new google.maps.Size(128.0, 128.0),
	minZoom: 0,
	maxZoom: 0,
	prefix: null,
	getTile: function(coord, zoom, doc) {
		var tileName;
		var tile = $('<img/>')
			.attr('src', this.dynmap.getTileUrl(tileName = this.prefix + '_128_' + coord.x + '_' + coord.y + '.png'))
			.error(function() { tile.hide(); })
			.bind('load', function() { tile.show(); })
			.css({
				width: '128px',
				height: '128px',
				borderStyle: 'none'
			})
			.hide();
		this.dynmap.registerTile(this, tileName, tile);
		//this.dynmap.unregisterTile(this, tileName);
		return tile.get(0);
	},
	updateTileSize: function(zoom) {
	}
});

maptypes.FlatMapType = function(configuration) { return new FlatMapType(configuration); };