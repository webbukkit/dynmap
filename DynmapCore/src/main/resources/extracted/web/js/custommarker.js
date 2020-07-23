L.CustomMarker = L.Class.extend({

	includes: L.Mixin.Events,
	
	options: {
		contentCreator: undefined,
		shadowCreator: undefined,
		clickable: true,
		draggable: false
	},
	
	initialize: function(latlng, options) {
		L.Util.setOptions(this, options);
		this._latlng = latlng;
	},
	
	onAdd: function(map) {
		this._map = map;
		
		if (!this._element && this.options.elementCreator) {
			this._element = this.options.elementCreator();
			
			this._element.className += ' leaflet-marker-icon';
			
			this._initInteraction();
		}
		if (!this._shadow && this.options.shadowCreator) {
			this._shadow = this.options.shadowCreator();
		}

		if (this._element) {
			map._panes.markerPane.appendChild(this._element);
		}
		if (this._shadow) {
			map._panes.shadowPane.appendChild(this._shadow);
		}
		
		map.on('viewreset', this._reset, this);
		this._reset();
		if (map.options.zoomAnimation && map.options.markerZoomAnimation) {
			map.on('zoomanim', this._animateZoom, this);
		}
	},
	
	onRemove: function(map) {
		if (this._element) {
			map._panes.markerPane.removeChild(this._element);
		}
		if (this._shadow) {
			map._panes.shadowPane.removeChild(this._elementShadow);
		}
		
		map.off('viewreset', this._reset, this);
		
		map = null;
	},
	
	getLatLng: function() {
		return this._latlng;
	},
	
	setLatLng: function(latlng) {
		this._latlng = latlng;
		this._reset();
	},

	_animateZoom: function (opt) {
		var pos = this._map._latLngToNewLayerPoint(this._latlng, opt.zoom, opt.center);
		L.DomUtil.setPosition(this._element, pos);
		this._element.style.zIndex = pos.y;
	},
	
	_reset: function() {
		if(this._map == null)
			return;
		var pos = this._map.latLngToLayerPoint(this._latlng);
		
		if (this._element) {
			L.DomUtil.setPosition(this._element, pos);
		}
		if (this._shadow) {
			L.DomUtil.setPosition(this._shadow, pos);
		}
		
		if (this._element) {
			this._element.style.zIndex = pos.y;
		}
	},
	
	_initInteraction: function() {
		if (this._element && this.options.clickable) {
			this._element.className += ' leaflet-clickable';
			
			L.DomEvent.addListener(this._element, 'click', this._onMouseClick, this);

			var events = ['dblclick', 'mousedown', 'mouseover', 'mouseout'];
			for (var i = 0; i < events.length; i++) {
				L.DomEvent.addListener(this._element, events[i], this._fireMouseEvent, this);
			}
		}
		
		if (this._element && L.Handler.MarkerDrag) {
			this.dragging = new L.Handler.MarkerDrag(this);
			
			if (this.options.draggable) {
				this.dragging.enable();
			}
		}
		var animation = (map.options.zoomAnimation && map.options.markerZoomAnimation);
		if (this._element)
			this._element.className += animation ? ' leaflet-zoom-animated' : ' leaflet-zoom-hide';
	},
	
	_onMouseClick: function(e) {
		L.DomEvent.stopPropagation(e);
		if (this.dragging && this.dragging.moved()) { return; }
		this.fire(e.type);
	},
	
	_fireMouseEvent: function(e) {
		this.fire(e.type);
		L.DomEvent.stopPropagation(e);
	},
	
	openPopup: function() {
		this._popup.setLatLng(this._latlng);
		this._map.openPopup(this._popup);
		
		return this;
	},
	
	closePopup: function() {
		if (this._popup) {
			this._popup._close();
		}
	},
	
	bindPopup: function(content, options) {
		this._popup = new L.Popup(options);
		this._popup.setContent(content);
		this.on('click', this.openPopup, this);
		
		return this;
	}
	
});
