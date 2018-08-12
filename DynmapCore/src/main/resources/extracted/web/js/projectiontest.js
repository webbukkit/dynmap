componentconstructors['projectiontest'] = function(dynmap, configuration) {
	var me = this;
	var marker = new L.CustomMarker(new L.LatLng(0,0,true), {
		elementCreator: function() {
			var div = document.createElement('div');
			var textContainer;
			$(div)
				.css({margin: '10px 10px 10px 10px'})
				.append(
					textContainer = $('<span/>')
					.css({'white-space': 'pre'})
					.text('')
				);
			marker.setText = function(text) {
				$(textContainer).text(text);
			};
			return div;
		}
	});
	dynmap.map.addLayer(marker);
		
	dynmap.map.on('mousemove', function(event) {
		marker.setLatLng(event.latlng);
		if (marker.setText) {
			marker.setText('LatLng: (' + event.latlng.lat + ',' + event.latlng.lng + ')\n'+
					       'LayerPoint: (' + event.layerPoint.x + ',' + event.layerPoint.y + ')\n'+
					       'World: (?,?,?)'
					);
		}
	});
};