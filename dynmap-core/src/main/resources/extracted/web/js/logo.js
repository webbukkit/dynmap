componentconstructors['logo'] = function(dynmap, configuration) {
	
	var Logo = L.Control.extend({
		onAdd: function(map) {
			this._container = L.DomUtil.create('div', 'leaflet-control-attribution');
			this._map = map;
			this._update();
			return this._container;
		},
	
		getPosition: function() {
			if(configuration.position == 'top-left')
				return 'topleft';
			else if(configuration.position == 'top-right')
				return 'topright';
			else if(configuration.position == 'bottom-left')
				return 'bottomleft';
			else
				return 'bottomright';
		},
	
		getContainer: function() {
			return this._container;
		},
	
		_update: function() {
			if (!this._map) return;
			var c = this._container;
			if (configuration.linkurl) {
				c = $('<a/>').attr('href', configuration.linkurl).appendTo(c)[0];
			}
			if (configuration.logourl) {
				$(c).append($('<img/>').attr('src', configuration.logourl).attr('alt', configuration.text).appendTo(c)[0]);
			} else {
				$(c).text(configuration.text);
			}
		}
	});
	
	dynmap.map.options.attributionControl = false;
	if (dynmap.map.attributionControl) {
		dynmap.map.removeControl(dynmap.map.attributionControl);
		dynmap.map.attributionControl = null;
	}
	var l = new Logo();
	dynmap.map.addControl(l);
};
