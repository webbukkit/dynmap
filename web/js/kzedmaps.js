function KzedProjection() {}
KzedProjection.prototype = {
		fromLatLngToPoint: function(latLng) {
			var x = latLng.lng() * config.tileWidth;
			var y = latLng.lat() * config.tileHeight;

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
		
        var dnprefix = '';
        if(this.dynmap.map.mapTypes[this.dynmap.map.mapTypeId].nightandday && this.dynmap.serverday)
            dnprefix = '_day';
		var extrazoom = this.dynmap.world.extrazoomout;        
		if (zoom <= extrazoom) {
			var zpre = 'zzzzzzzzzzzzzzzz'.substring(0, extrazoom-zoom+1);
			// Most zoomed out tiles.
			tileSize = 128;
			imgSize = tileSize;
			var tilescale = 2 << (extrazoom-zoom);
            if (this.dynmap.world.bigworld) {
                tileName = zpre + this.prefix + dnprefix + '/' + ((-coord.x * tileSize*tilescale)>>12) + 
                    '_' + ((coord.y * tileSize*tilescale) >> 12) + '/' +
                    (-coord.x * tileSize*tilescale) + '_' + (coord.y * tileSize*tilescale) + '.png';
            }
            else {
                tileName = zpre + this.prefix + dnprefix + '_' + (-coord.x * tileSize*tilescale) + '_' + (coord.y * tileSize*tilescale) + '.png';
            }
		} else {
			// Other zoom levels.
			tileSize = 128;

			imgSize = Math.pow(2, 6+zoom-extrazoom);
            if(this.dynmap.world.bigworld) {
                tileName = this.prefix + dnprefix + '/' + ((-coord.x*tileSize) >> 12) + '_' +
                    ((coord.y*tileSize)>>12) + '/' + 
                    (-coord.x*tileSize) + '_' + (coord.y*tileSize) + '.png';
            }
            else {
                tileName = this.prefix + dnprefix + '_' + (-coord.x*tileSize) + '_' + (coord.y*tileSize) + '.png';
            }
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
		var extrazoom = this.dynmap.world.extrazoomout;
		this.maxZoom = 3 + extrazoom;
		if (zoom <= extrazoom) {
			size = 128;
		} else {
			size = Math.pow(2, 6+zoom-extrazoom);
		}
		this.tileSize = new google.maps.Size(size, size);
	}
});

maptypes.KzedMapType = function(configuration) { return new KzedMapType(configuration); };