L.CustomMarker = L.Marker.extend({
	
	options: {
		elementCreator: undefined,
		shadowCreator: undefined,
		clickable: true,
		draggable: false
	},
	
	initialize: function(latlng, options) {
		//Dynmap - Pass options to CustomIcon
		options.icon = new L.CustomIcon(options);

		L.Util.setOptions(this, options);
		this._latlng = latlng;
	},
});

L.CustomIcon = L.DivIcon.extend({
	options: {
		elementCreator: function() {
			return document.createElement('div');
		},
		shadowCreator: function() { },
		className: '', //Remove divIcon class
	},

	initialize: function(options) {
		L.Util.setOptions(this, options);
	},

	createIcon() {
		//Call elementCreator to create icon
		var icon = this.options.elementCreator(),
			className = icon.className;

		icon.className += ' leaflet-marker-icon'; //Required for correct styling

		return icon;
	},

	createShadow() {
		return this.options.shadowCreator();
	}
});