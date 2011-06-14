componentconstructors['chatballoon'] = function(dynmap, configuration) {
	var me = this;
	me.chatpopups = {};
	$(dynmap).bind('chat', function(event, message) {
		if (message.source != 'player') {
			return;
		}
		var player = dynmap.players[message.name];
		var playerMarker = player && player.marker;
		if (!playerMarker) {
			return;
		}
		if (player.location.world != dynmap.world) {
			return;
		}
		var popup = me.chatpopups[message.name];
		if (!popup) {
			popup = { lines: [ message.text ] };
		} else {
			popup.lines[popup.lines.length] = message.text;
		}

		var MAX_LINES = 5;
		if (popup.lines.length > MAX_LINES) {
			popup.lines = popup.lines.slice(1);
		}
		var htmlMessage = '<div id="content"><b>' + message.name + "</b><br/><br/>";
		var line;
		for (line in popup.lines) {
			htmlMessage = htmlMessage + popup.lines[line] + "<br/>";
		}
		htmlMessage = htmlMessage + "</div>";
		if (!popup.infoWindow) {
			popup.infoWindow = new google.maps.InfoWindow({
				disableAutoPan: !(configuration.focuschatballoons || false),
			    content: htmlMessage
			});
		} else {
			popup.infoWindow.setContent(htmlMessage);
		}
		popup.infoWindow.open(dynmap.map, playerMarker);
		me.chatpopups[message.name] = popup;
		if (popup.timeout) { window.clearTimeout(popup.timeout); }
		popup.timeout = window.setTimeout(function() {
			popup.infoWindow.close();
			popup.infoWindow = null;
			delete me.chatpopups[message.name];
		}, 8000);
	});
};