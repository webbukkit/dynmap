var DynmapProjection = L.Class.extend({
	initialize: function(options) {
		L.Util.setOptions(this, options);
	},
	fromLocationToLatLng: function(location) {
		throw "fromLocationToLatLng not implemented";
	}
});

var DynmapTileLayer = L.TileLayer.extend({
	_currentzoom: undefined,
	getProjection: function() {
		return this.projection;
	},
	onTileUpdated: function(tile, tileName) {
		var src = this.dynmap.getTileUrl(tileName);
		tile.attr('src', src);
		tile.show();
	},
	
	getTileName: function(tilePoint, zoom) {
		throw "getTileName not implemented";
	},
	
	getTileUrl: function(tilePoint, zoom) {
		var tileName = this.getTileName(tilePoint, zoom);
		var url = this._cachedTileUrls[tileName];
		if (!url) {
			this._cachedTileUrls[tileName] = url = this.options.dynmap.getTileUrl(tileName) + '?' + new Date().getUTCMilliseconds();
		}
		return url;
	},
	
	updateNamedTile: function(name) {
		var tile = this._namedTiles[name];
		delete this._cachedTileUrls[name];
		if (tile) {
			this.updateTile(tile);
		}
	},
	
	updateTile: function(tile) {
		this._loadTile(tile, tile.tilePoint, this._map.getZoom());
	},
	
	// We should override this, since Leaflet does modulo on tilePoint by default. (https://github.com/CloudMade/Leaflet/blob/master/src/layer/tile/TileLayer.js#L151)
	_addTile: function(tilePoint) {
		var tilePos = this._getTilePos(tilePoint),
			zoom = this._map.getZoom(),
			key = tilePoint.x + ':' + tilePoint.y,
			name = this.getTileName(tilePoint, zoom);

		// create tile
		var tile = this._createTile();
		tile.tileName = name;
		tile.tilePoint = tilePoint;
		L.DomUtil.setPosition(tile, tilePos);

		this._tiles[key] = tile;
		this._namedTiles[name] = tile;

		this._loadTile(tile, tilePoint, zoom);

		this._container.appendChild(tile);
	},
	
	_removeOtherTiles: function(bounds) {
		var kArr, x, y, key;

		for (key in this._tiles) {
			if (this._tiles.hasOwnProperty(key)) {
				kArr = key.split(':');
				x = parseInt(kArr[0], 10);
				y = parseInt(kArr[1], 10);

				// remove tile if it's out of bounds
				if (x < bounds.min.x || x > bounds.max.x || y < bounds.min.y || y > bounds.max.y) {
					var tile = this._tiles[key];
					if (tile.parentNode === this._container) {
						this._container.removeChild(this._tiles[key]);
					}
					delete this._namedTiles[tile.tileName];
					delete this._tiles[key];
				}
			}
		}		
	},
	_updateTileSize: function() {
		var newzoom = this._map.getZoom();
		if (this._currentzoom !== newzoom) {
			var newTileSize = this.calculateTileSize(newzoom);
			this._currentzoom = newzoom;
			if (newTileSize !== this.options.tileSize) {
				this.setTileSize(newTileSize);
			}
		}
	},
	
	_reset: function() {
		this._updateTileSize();
		this._tiles = {};
		this._namedTiles = {};
		this._cachedTileUrls = {};
		this._initContainer();
		this._container.innerHTML = '';
	},
	
	_update: function() {
		this._updateTileSize();
		var bounds = this._map.getPixelBounds(),
		tileSize = this.options.tileSize;

		var nwTilePoint = new L.Point(
				Math.floor(bounds.min.x / tileSize),
				Math.floor(bounds.min.y / tileSize)),
			seTilePoint = new L.Point(
				Math.floor(bounds.max.x / tileSize),
				Math.floor(bounds.max.y / tileSize)),
			tileBounds = new L.Bounds(nwTilePoint, seTilePoint);
	
		this._addTilesFromCenterOut(tileBounds);
	
		if (this.options.unloadInvisibleTiles) {
			this._removeOtherTiles(tileBounds);
		}
	},
	calculateTileSize: function(zoom) {
		return this.options.tileSize;
	},
	setTileSize: function(tileSize) {
		this.options.tileSize = tileSize;
		this._tiles = {};
		this._container.innerHTML = '';
		this._createTileProto();
	},
	updateTileSize: function(zoom) {}
});

function loadjs(url, completed) {
	var script = document.createElement('script');
	script.setAttribute('src', url);
	script.setAttribute('type', 'text/javascript');
	var isloaded = false;
	script.onload = function() {
		if (isloaded) { return; }
		isloaded = true;
		completed();
	};
	
	// Hack for IE, don't know whether this still applies to IE9.
	script.onreadystatechange = function() {
		script.onload();
	};
	(document.head || document.getElementsByTagName('head')[0]).appendChild(script);
}

function loadcss(url, completed) {
	var script = document.createElement('link');
	script.setAttribute('href', url);
	script.setAttribute('rel', 'stylesheet');
	var isloaded = false;
	if (completed) {
		script.onload = function() {
			if (isloaded) { return; }
			isloaded = true;
			completed();
		};
		
		// Hack for IE, don't know whether this still applies to IE9.
		script.onreadystatechange = function() {
			script.onload();
		};
	}
	
	(document.head || document.getElementsByTagName('head')[0]).appendChild(script);
}

function splitArgs(s) {
	var r = s.split(' ');
	delete arguments[0];
	var obj = {};
	var index = 0;
	$.each(arguments, function(argumentIndex, argument) {
		if (!argumentIndex) { return; }
		var value = r[argumentIndex-1];
		obj[argument] = value;
	});
	return obj;
}

function swtch(value, options, defaultOption) {
	return (options[value] || defaultOption || function(){})(value);
}
(function( $ ){
	$.fn.scrollHeight = function(height) {
		return this[0].scrollHeight;
	};
})($);

function Location(world, x, y, z) {
	this.world = world;
	this.x = x;
	this.y = y;
	this.z = z;
}

function namedReplace(str, obj)
{
	var startIndex = 0;
	var result = '';
	while(true) {
		var variableBegin = str.indexOf('{', startIndex);
		var variableEnd = str.indexOf('}', variableBegin+1);
		if (variableBegin < 0 || variableEnd < 0) {
			result += str.substr(startIndex);
			break;
		}
		if (variableBegin < variableEnd) {
			var variableName = str.substring(variableBegin+1, variableEnd-1);
			result += str.substring(startIndex, variableBegin-1);
			result += obj[variableName];
		} else /* found '{}' */ {
			result += str.substring(startIndex, variableBegin-1);
			result += '';
		}
		startIndex = variableEnd+1;
	}
	return result;
}