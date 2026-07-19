componentconstructors['link'] = function(dynmap, configuration) {

  var dynmapLink = L.Control.extend({
		options: { position: 'bottomleft' },

	onAdd: function(map) {
		this._map = map;
		this._container = L.DomUtil.create('div', 'dynmap-link');

		this._linkButton = this._createButton(
				'Link', 'dynmap-link-button', this._follow, this);

		this._container.appendChild(this._linkButton);
		return this._container;
	},

	getContainer: function() {
		return this._container;
	},

	getPosition: function() {
		return this.options.position;
	},

	_createButton: function(title, className, fn, context) {
		var link = document.createElement('a');
		link.href = '#';
		link.title = title;
		link.className = className;
		link.onmouseover = function() {
			link.href = dynmap.getLink();
		};

		L.DomEvent.disableClickPropagation(link);
		L.DomEvent.addListener(link, 'click', L.DomEvent.preventDefault);
		L.DomEvent.addListener(link, 'click', fn, context);

		return link;
	},

	_follow: function() {
		var url = dynmap.getLink();
		window.location = url;
	}
  });

	var link = new dynmapLink();
	dynmap.map.addControl(link);
};
