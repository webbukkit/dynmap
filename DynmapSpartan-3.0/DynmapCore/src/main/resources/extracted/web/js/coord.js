componentconstructors['coord'] = function(dynmap, configuration) {

	var Coord = L.Control.extend({
		valfield: $('<span/>'),
		mcrfield: $('<span/>'),
		chunkfield: $('<span/>'),

		onAdd: function(map) {
			if(configuration.hidey) 
				this._container = L.DomUtil.create('div', 'coord-control coord-control-noy');
			else
				this._container = L.DomUtil.create('div', 'coord-control');
			this._map = map;
			$('<span/>').addClass('coord-control-label').text((configuration.label || 'x,y,z') + ': ').appendTo(this._container);
			$('<br/>').appendTo(this._container);
			this.valfield.addClass('coord-control-value').text(configuration.hidey ? '---,---' : '---,---,---').appendTo(this._container);
			if(configuration['show-mcr']) {
				$('<br/>').appendTo(this._container);
				this.mcrfield.addClass('coord-control-value').text('--------').appendTo(this._container);
			}
			if(configuration['show-chunk']) {
				$('<br/>').appendTo(this._container);
				this.chunkfield.addClass('coord-control-value').text('Chunk: ---,---').appendTo(this._container);
			}
			this._update();
			return this.getContainer();
		},

		getPosition: function() {
			return 'topleft';
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
		var loc = dynmap.getProjection().fromLatLngToLocation(mevent.latlng, dynmap.world.sealevel+1);
		if(configuration.hidey)
			coord.valfield.text(Math.round(loc.x) + ',' + Math.round(loc.z));
		else
			coord.valfield.text(Math.round(loc.x) + ',' + loc.y + ',' + Math.round(loc.z));
		if(configuration['show-mcr'])
			coord.mcrfield.text('r.' + Math.floor(loc.x/512) + '.' + Math.floor(loc.z/512) + '.mca');
		if(configuration['show-chunk'])
			coord.chunkfield.text('Chunk: ' + Math.floor(loc.x/16) + ',' + Math.floor(loc.z/16));
	});
	dynmap.map.on('mouseout', function(mevent) {
		if(!dynmap.map) return;
		if(configuration.hidey)
			coord.valfield.text('---,---');
		else
			coord.valfield.text('---,---,---');
		if(configuration['show-mcr'])
			coord.mcrfield.text('--------');
		if(configuration['show-chunk'])
			coord.chunkfield.text('Chunk: ---,---');
	});
};
