var DynmapProjection = L.Class.extend({
	initialize: function(options) {
		L.Util.setOptions(this, options);
	},
	fromLocationToLatLng: function(location) {
		throw "fromLocationToLatLng not implemented";
	},
	fromLatLngToLocation: function(location) {
		return null;
	}
});

if (!Array.prototype.indexOf) { // polyfill for IE < 9
	    Array.prototype.indexOf = function (searchElement /*, fromIndex */ ) {
	        "use strict";
	        if (this === void 0 || this === null) {
	            throw new TypeError();
	        }
	        var t = Object(this);
	        var len = t.length >>> 0;
	        if (len === 0) {
	            return -1;
	        }
	        var n = 0;
	        if (arguments.length > 0) {
	            n = Number(arguments[1]);
	            if (n !== n) { // shortcut for verifying if it's NaN
	                n = 0;
	            } else if (n !== 0 && n !== (1 / 0) && n !== -(1 / 0)) {
	                n = (n > 0 || -1) * Math.floor(Math.abs(n));
	            }
	        }
	        if (n >= len) {
	            return -1;
	        }
	        var k = n >= 0 ? n : Math.max(len - Math.abs(n), 0);
	        for (; k < len; k++) {
	            if (k in t && t[k] === searchElement) {
	                return k;
	            }
	        }
	        return -1;
	    }
}

var DynmapLayerControl = L.Control.Layers.extend({
	getPosition: function() {
		return 'topleft';
	},
	
	// Function override to include pos
	addOverlay: function(layer, name, pos) {
		this._addLayer(layer, name, true, pos);
		this._update();
		return this;
	},
	
	// Function override to order layers by pos
	_addLayer: function (layer, name, overlay, pos) {
		var id = L.stamp(layer);

		this._layers[pos] = {
			layer: layer,
			name: name,
			overlay: overlay,
			id: id
		};

		if (this.options.autoZIndex && layer.setZIndex) {
			this._lastZIndex++;
			layer.setZIndex(this._lastZIndex);
		}
	},
	
	// Function override to convert the position-based ordering into the id-based ordering
	_onInputClick: function () {
		var i, input, obj,
		    inputs = this._form.getElementsByTagName('input'),
		    inputsLen = inputs.length,
		    baseLayer;

		this._handlingClick = true;

		// Convert ID to pos
		var id2pos = {};
		for (i in this._layers) {
			id2pos[this._layers[i].id] = i;
		}

		for (i = 0; i < inputsLen; i++) {
			input = inputs[i];
			obj = this._layers[id2pos[input.layerId]];
			
			if (input.checked && !this._map.hasLayer(obj.layer)) {
				this._map.addLayer(obj.layer);
				if (!obj.overlay) {
					baseLayer = obj.layer;
				}
			} else if (!input.checked && this._map.hasLayer(obj.layer)) {
				this._map.removeLayer(obj.layer);
			}
		}

		if (baseLayer) {
			this._map.setZoom(this._map.getZoom());
			this._map.fire('baselayerchange', {layer: baseLayer});
		}

		this._handlingClick = false;
	},
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
			this._cachedTileUrls[tileName] = url = this.options.dynmap.getTileUrl(tileName);
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
	// Override to fix loads completing after layer removed
	_addTilesFromCenterOut: function(bounds) {
		if(this._container == null)		// Ignore if we've stopped being active layer
			return;
		var queue = [],
			center = bounds.getCenter();

		for (var j = bounds.min.y; j <= bounds.max.y; j++) {
			for (var i = bounds.min.x; i <= bounds.max.x; i++) {
				if ((i + ':' + j) in this._tiles) { continue; }
				queue.push(new L.Point(i, j));
			}
		}

		// load tiles in order of their distance to center
		queue.sort(function(a, b) {
			return a.distanceTo(center) - b.distanceTo(center);
		});

		var fragment = document.createDocumentFragment();

		this._tilesToLoad = queue.length;
		for (var k = 0, len = this._tilesToLoad; k < len; k++) {
			this._addTile(queue[k], fragment);
		}

		this._container.appendChild(fragment);
	},
	//Copy and mod of Leaflet method - marked changes with Dynmap: to simplify reintegration
	_addTile: function(tilePoint, container) {
		var tilePos = this._getTilePos(tilePoint),
			zoom = this._map.getZoom(),
			key = tilePoint.x + ':' + tilePoint.y,
			name = this.getTileName(tilePoint, zoom),	//Dynmap
			tileLimit = (1 << zoom);

		// wrap tile coordinates
		if (!this.options.continuousWorld) {
			if (!this.options.noWrap) {
				tilePoint.x = ((tilePoint.x % tileLimit) + tileLimit) % tileLimit;
			} else if (tilePoint.x < 0 || tilePoint.x >= tileLimit) {
				this._tilesToLoad--;
				return;
			}

			if (tilePoint.y < 0 || tilePoint.y >= tileLimit) {
				this._tilesToLoad--;
				return;
			}
		}

		// create tile
		var tile = this._createTile();
		tile.tileName = name;	//Dynmap
		tile.tilePoint = tilePoint;	//Dynmap
		L.DomUtil.setPosition(tile, tilePos);

		this._tiles[key] = tile;
		this._namedTiles[name] = tile;	//Dynmap

		if (this.options.scheme == 'tms') {
			tilePoint.y = tileLimit - tilePoint.y - 1;
		}

		this._loadTile(tile, tilePoint, zoom);

		container.appendChild(tile);
	},
	_loadTile: function(tile, tilePoint, zoom) {
		var me = this;
		tile._layer = this;
		function done() {
			me._loadingTiles.splice(me._loadingTiles.indexOf(tile), 1);
			me._nextLoadTile();
		}
		tile.onload = function(e) {
			me._tileOnLoad.apply(this, [e]);
			done();
		}
		tile.onerror = function() {
			me._tileOnError.apply(this);
			done();
		}
		tile.loadSrc = function() {
			me._loadingTiles.push(tile);
			tile.src = me.getTileUrl(tilePoint, zoom);
		};
		this._loadQueue.push(tile);
		this._nextLoadTile();
	},
	_nextLoadTile: function() {
		if (this._loadingTiles.length > 4) { return; }
		var next = this._loadQueue.shift();
		if (!next) { return; }

		next.loadSrc();
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
		this._loadQueue = [];
		this._loadingTiles = [];
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
	/*calculateTileSize: function(zoom) {
		return this.options.tileSize;
	},*/
	calculateTileSize: function(zoom) {
		// zoomoutlevel: 0 when izoom > mapzoomin, else mapzoomin - izoom (which ranges from 0 till mapzoomin)
		var izoom = this.options.maxZoom - zoom;
		var zoominlevel = Math.max(0, this.options.mapzoomin - izoom);
		return 128 << zoominlevel;
	},
	setTileSize: function(tileSize) {
		this.options.tileSize = tileSize;
		this._tiles = {};
		this._createTileProto();
	},
	updateTileSize: function(zoom) {},

	// Some helper functions.
	zoomprefix: function(amount) {
		return 'zzzzzzzzzzzzzzzzzzzzzz'.substr(0, amount);
	},
	getTileInfo: function(tilePoint, zoom) {
		// zoom: max zoomed in = this.options.maxZoom, max zoomed out = 0
		// izoom: max zoomed in = 0, max zoomed out = this.options.maxZoom
		// zoomoutlevel: izoom < mapzoomin -> 0, else -> izoom - mapzoomin (which ranges from 0 till mapzoomout)
		var izoom = this.options.maxZoom - zoom;
		var zoomoutlevel = Math.max(0, izoom - this.options.mapzoomin);
		var scale = 1 << zoomoutlevel;
		var x = scale*tilePoint.x;
		var y = scale*tilePoint.y;
		return {
			prefix: this.options.prefix,
			nightday: (this.options.nightandday && this.options.dynmap.serverday) ? '_day' : '',
			scaledx: x >> 5,
			scaledy: y >> 5,
			zoom: this.zoomprefix(zoomoutlevel),
			zoomprefix: (zoomoutlevel==0)?"":(this.zoomprefix(zoomoutlevel)+"_"),
			x: x,
			y: y,
			fmt: this.options['image-format'] || 'png'
		};
	}
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
		if (script.readyState == 'loaded' || script.readyState == 'complete')
			script.onload();
	};
	(document.head || document.getElementsByTagName('head')[0]).appendChild(script);
}

function loadcss(url, completed) {
	var link = document.createElement('link');
	link.setAttribute('href', url);
	link.setAttribute('rel', 'stylesheet');
	var isloaded = false;
	if (completed) {
		link.onload = function() {
			if (isloaded) { return; }
			isloaded = true;
			completed();
		};

		// Hack for IE, don't know whether this still applies to IE9.
		link.onreadystatechange = function() {
			link.onload();
		};
	}

	(document.head || document.getElementsByTagName('head')[0]).appendChild(link);
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
			var variableName = str.substring(variableBegin+1, variableEnd);
			result += str.substring(startIndex, variableBegin);
			result += obj[variableName];
		} else /* found '{}' */ {
			result += str.substring(startIndex, variableBegin-1);
			result += '';
		}
		startIndex = variableEnd+1;
	}
	return result;
}

function concatURL(base, addition) {
	if(base.indexOf('?') >= 0)
		return base + escape(addition);
	
	return base + addition;
}
