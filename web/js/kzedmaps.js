function KzedProjection() {}
KzedProjection.prototype = {
		fromLatLngToPoint: function(latLng) {
			var x = (latLng.lng() * config.tileWidth)|0;
			var y = (latLng.lat() * config.tileHeight)|0;

			return new google.maps.Point(x, y);
		},
		fromPointToLatLng: function(point) {
			var lng = point.x / config.tileWidth;
			var lat = point.y / config.tileHeight;
			return new google.maps.LatLng(lat, lng);
		},
		fromWorldToLatLng: function(x, y, z)
		{
			var dx = +x;
			var dy = +y - 127;
			var dz = +z;
			var px = dx + dz;
			var py = dx - dz - dy;

			var lng = -px / config.tileWidth / 2 + 0.5;
			var lat = py / config.tileHeight / 2;

			return new google.maps.LatLng(lat, lng);
		}
};

function KzedMapType(configuration) { $.extend(this, configuration); }
KzedMapType.prototype = $.extend(new DynMapType(), {
	constructor: KzedMapType,
	projection: new KzedProjection(),
	tileSize: new google.maps.Size(128, 128),
	minZoom: 0,
	maxZoom: 3,
	prefix: null,
	getTile: function(coord, zoom, doc) {
		var tileDebugText = null;
		var tileSize = 128;
		var tileName;
		var imgSize;
		
		var debugred;
		var debugblue;
		
		if (zoom == 0) {
			// Most zoomed out tiles.
			tileSize = 128;
			imgSize = tileSize;
			tileName = 'z' + this.prefix + '_' + (-coord.x * tileSize*2) + '_' + (coord.y * tileSize*2) + '.png';
		} else {
			// Other zoom levels.
			tileSize = 128;

			imgSize = Math.pow(2, 6+zoom);
			tileName = this.prefix + '_' + (-coord.x*tileSize) + '_' + (coord.y*tileSize) + '.png';
		}
		var img;
		var tile = $('<div/>')
			.addClass('tile')
			.css({
				width: tileSize + 'px',
				height: tileSize + 'px'
			});
		if (tileDebugText) {
			$('<span/>')
				.text(tileDebugText)
				.css({
					position: 'absolute',
					color: 'red'
				})
				.appendTo(tile);
		}
		if (tileName) {
			img = $('<img/>')
				.attr('src', this.dynmap.getTileUrl(tileName))
				.error(function() { img.hide(); })
				.bind('load', function() { img.show(); })
				.css({
					width: imgSize + 'px',
					height: imgSize + 'px',
					borderStyle: 'none'
				})
				.hide()
				.appendTo(tile);
			this.dynmap.registerTile(this, tileName, img);
		} else {
			this.dynmap.unregisterTile(this, tileName);
		}
		return tile.get(0);
	},
	updateTileSize: function(zoom) {
		var size;
		if (zoom == 0) {
			size = 128;
		} else {
			size = Math.pow(2, 6+zoom);
		}
		this.tileSize = new google.maps.Size(size, size);
	}
});

maptypes.KzedMapType = function(configuration) { return new KzedMapType(configuration); };