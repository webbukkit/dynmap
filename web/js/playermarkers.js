componentconstructors['playermarkers'] = function(dynmap, configuration) {
	var me = this;
	$(dynmap).bind('playeradded', function(event, player) {
		// Create the player-marker.
		var markerPosition = dynmap.map.getProjection().fromWorldToLatLng(player.location.x, player.location.y, player.location.z);
		player.marker = new CustomMarker(markerPosition, dynmap.map, function(div) {
			var playerImage;
			$(div)
				.addClass('Marker')
				.addClass('playerMarker')
				.append(playerImage = $('<img/>')
						.attr({ src: 'images/player.png' }))
				.append($('<span/>')
					.addClass('playerName')
					.text(player.name));
			
			if (configuration.showplayerfaces) {
				getMinecraftHead(player.name, 32, function(head) {
					$(head)
						.addClass('playericon')
						.prependTo(div);
					playerImage.remove();
				});
			}
		});
	});
	$(dynmap).bind('playerremoved', function(event, player) {
		// Remove the marker.
		player.marker.remove();
	});
	$(dynmap).bind('playerupdated', function(event, player) {
		// Update the marker.
		var markerPosition = dynmap.map.getProjection().fromWorldToLatLng(player.location.x, player.location.y, player.location.z);
		player.marker.toggle(dynmap.world === player.location.world);
		player.marker.setPosition(markerPosition);
	});
};