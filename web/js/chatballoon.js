componentconstructors['chatballoon'] = function(dynmap, configuration) {
	var me = this;
	me.chatpopups = {};
	$(dynmap).bind('playerupdated', function(event, player) {
		var popup = me.chatpopups[player.name];
		if (popup) {
			var markerPosition = dynmap.getProjection().fromLocationToLatLng(player.location);
			popup.layer.setLatLng(markerPosition);
		}
	});
	$(dynmap).bind('worldchanged', function() {
		$.each(me.chatpopups, function(name, popup) {
			popup.close();
		});
	});
	$(dynmap).bind('chat', function(event, message) {
		if (message.source != 'player') {
			return;
		}
		var player = dynmap.players[message.name];
		if (!player)
			return;
		if (dynmap.world !== player.location.world) {
			return;
		}
		var popupPosition = dynmap.getProjection().fromLocationToLatLng(player.location);
		var popup = me.chatpopups[message.name];
		if (!popup) {
			me.chatpopups[message.name] = popup = {
				layer: new L.Popup({autoPan: configuration.focuschatballoons, closeButton: false}),
				content: $('<div/>').addClass('balloonmessages')[0]
			};
			popup.layer.setContent(popup.content);
			
			popup.close = function() {
				if (popup.timeout) { window.clearTimeout(popup.timeout); }
				dynmap.map.removeLayer(popup.layer);
				delete me.chatpopups[message.name];
			};
			
			popup.layer.setLatLng(popupPosition);
			dynmap.map.addLayer(popup.layer);
		}
		
		// Add line to balloon.
		$('<div/>').addClass('balloonmessage').text(message.text).appendTo(popup.content);

		// Remove older lines when too many messages are shown.
		var children = $(popup.content).children();
		if (children.length > 5) {
			$(children[0]).remove();
		}
		
		popup.layer.setContent(popup.content);
		
		if (popup.timeout) { window.clearTimeout(popup.timeout); }
		popup.timeout = window.setTimeout(function() {
			popup.close();
		}, 8000);
		
		if (configuration.focuschatballoons) {
			dynmap.panToLatLng(popupPosition);
		}
	});
};