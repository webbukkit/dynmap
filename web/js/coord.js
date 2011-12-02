componentconstructors['coord'] = function(dynmap, configuration) {
	
	var Coord = L.Class.extend({
		valfield: $('<span/>'),
		
		onAdd: function(map) {
			if(configuration.hidey)
				this._container = L.DomUtil.create('div', 'coord-control coord-control-noy');
			else
				this._container = L.DomUtil.create('div', 'coord-control');
			this._map = map;
			$('<span/>').addClass('coord-control-label').text((configuration.label || 'x,y,z') + ': ').appendTo(this._container);
			$('<br/>').appendTo(this._container);
			this.valfield.addClass('coord-control-value').text('').appendTo(this._container);
			
			this._update();
		},
	
		getPosition: function() {
			return L.Control.Position.TOP_LEFT;
		},
	
		getContainer: function() {
			return this._container;
		},
	
		_update: function() {
			if (!this._map) return;
		}
	});
	
	var coord = new Coord();
	dynmap.map.addControl(coord);
	dynmap.map.on('mousemove', function(mevent) {
		if(!dynmap.map) return;
		var loc = dynmap.getProjection().fromLatLngToLocation(mevent.latlng, 64);
		if(configuration.hidey)
			coord.valfield.text(Math.round(loc.x) + ',' + Math.round(loc.z));
		else
			coord.valfield.text(Math.round(loc.x) + ',' + loc.y + ',' + Math.round(loc.z));
	});
	dynmap.map.on('mouseout', function(mevent) {
		if(!dynmap.map) return;
		if(configuration.hidey)
			coord.valfield.text('---,---');
		else
			coord.valfield.text('---,---,---');
	});
};