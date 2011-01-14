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

function KzedMapType() {}
KzedMapType.prototype = {
	__proto__: new DynMapType(),
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
		var offset = {x: 0, y: 0};
		
		var debugred;
		var debugblue;
		
		if (zoom == 0) {
			// Most zoomed out tiles.
			
			tileSize = 128;
			imgSize = tileSize;
			tileName = 'z' + this.prefix + '_' + (-coord.x * tileSize*2) + '_' + (coord.y * tileSize*2);
		} else {
			// Other zoom levels.
			tileSize = 128;
			
			// Helper functions.
			var floor = Math.floor;
			var div = function(x,y){return floor(x/y);}
			var mod = function(x,y){return ((x%y)+y)%y;};

			// Split the image up in ... segments (1*1 for zoom 1, 2*2 for zoom 2, 4*4 for zoom 3, etc).
			var segments = Math.pow(2,zoom-1);
			imgSize = segments*tileSize;
			
			// Calculate the location relative to the world of this segment.
			var mapcoord = {x: div(coord.x,segments)*tileSize, y: div(coord.y,segments)*tileSize};
			// Calculate the location relative to the image of this segment.
			offset = {x: mod(coord.x,segments)*-tileSize, y: mod(coord.y,segments)*-tileSize};
			
			// The next couple of lines are somewhat of a hack, but makes it faster to render zoomed in tiles:
			/*tileSize = imgSize;
			
			if (offset.x == 0 && offset.y == 0) {
				tileName = this.prefix + '_' + (-mapcoord.x) + '_' + mapcoord.y;
			}
			offset = {x: 0, y: 0};*/
			// The next line is not:
			tileName = this.prefix + '_' + (-mapcoord.x) + '_' + mapcoord.y;
		}
		var img;
		var tile = $('<div/>')
			.addClass('tile')
			.css({
				overflow: 'hidden',
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
				.css({
					width: imgSize + 'px',
					height: imgSize + 'px',
					borderStyle: 'none',
					marginLeft: offset.x + 'px',
					marginTop: offset.y + 'px'
				})
				.appendTo(tile);
			this.dynmap.registerTile(this, tileName, img);
		} else {
			this.dynmap.unregisterTile(this, tileName);
		}
		return tile.get(0);
	},
};


DefaultMapType.prototype = new KzedMapType();
DefaultMapType.prototype.constructor = DefaultMapType;
function DefaultMapType(){}
DefaultMapType.prototype.prefix = 't';



CaveMapType.prototype = new KzedMapType();
CaveMapType.prototype.constructor = CaveMapType;
function CaveMapType(){}
CaveMapType.prototype.prefix = 'ct';

