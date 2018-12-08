componentconstructors['playermarkers'] = function(dynmap, configuration) {
	var me = this;
	$(dynmap).bind('playeradded', function(event, player) {
		// Create the player-marker.
		var markerPosition = dynmap.getProjection().fromLocationToLatLng(player.location);
		player.marker = new L.CustomMarker(markerPosition, { elementCreator: function() {
			var div = document.createElement('div');
			var playerImage;

			var markerPosition = dynmap.getProjection().fromLocationToLatLng(player.location);
			player.marker.setLatLng(markerPosition);

			// Only show player faces if canvas supported					
			if(dynmap.canvassupport == false)
				configuration.showplayerfaces = false;
					
			$(div)
				.addClass('Marker')
				.addClass('playerMarker')
				.append(playerImage = $('<img/>').addClass(configuration.smallplayerfaces?'playerIconSm':'playerIcon')
						.attr({ src: 'images/player.png' }))
				.append(player.namefield = $('<span/>')
					.addClass(configuration.smallplayerfaces?'playerNameSm':'playerName')
					.append(player.name));
			
			if (configuration.showplayerfaces) {
				if(configuration.smallplayerfaces) {
					getMinecraftHead(player.account, 16, function(head) {
						$(head)
							.addClass('playerIconSm')
						.prependTo(div);
						playerImage.remove();
					});
				}
				else if(configuration.showplayerbody) {
					getMinecraftHead(player.account, 'body', function(head) {
						$(head)
							.addClass('playerIcon')
						.prependTo(div);
						playerImage.remove();
					});
				}
				else {
					getMinecraftHead(player.account, 32, function(head) {
						$(head)
							.addClass('playerIcon')
						.prependTo(div);
						playerImage.remove();
					});
				}
			}
			if (configuration.showplayerhealth) {
				player.healthContainer = $('<div/>')
					.addClass(configuration.smallplayerfaces?'healthContainerSm':'healthContainer')
					.appendTo(div);
				if (player.health !== undefined && player.armor !== undefined) {
					player.healthBar = $('<div/>')
						.addClass('playerHealth')
						.css('width', Math.ceil(player.health*2.5) + 'px');
					player.armorBar = $('<div/>')
						.addClass('playerArmor')
						.css('width', Math.ceil(player.armor*2.5) + 'px');

					$('<div/>')
						.addClass('playerHealthBackground')
						.append(player.healthBar)
						.appendTo(player.healthContainer);
					$('<div/>')
						.addClass('playerArmorBackground')
						.append(player.armorBar)
						.appendTo(player.healthContainer);
				} else {
					player.healthContainer.css('display','none');
				}
			}
			else {
				player.namefield.addClass('playerNameNoHealth');
			}
			
			return div;
		}});
		if(dynmap.world === player.location.world)
			dynmap.playermarkergroup.addLayer(player.marker);
	});
	$(dynmap).bind('playerremoved', function(event, player) {
		// Remove the marker.
		dynmap.playermarkergroup.removeLayer(player.marker);
	});
	$(dynmap).bind('playerupdated', function(event, player) {
		if(dynmap.world === player.location.world) {
			// Add if needed
			dynmap.playermarkergroup.addLayer(player.marker);
			// Update the marker.
			var markerPosition = dynmap.getProjection().fromLocationToLatLng(player.location);
			player.marker.setLatLng(markerPosition);
			// Update health
			if (configuration.showplayerhealth) {
				if (player.health !== undefined && player.armor !== undefined) {
					player.healthContainer.css('display','block');
					player.healthBar.css('width', Math.ceil(player.health*2.5) + 'px');
					player.armorBar.css('width', Math.ceil(player.armor*2.5) + 'px');
				} else {
					player.healthContainer.css('display','none');
				}
			}
		} else {
			dynmap.playermarkergroup.removeLayer(player.marker);
		}
		if (player.namefield && (player.namefield.html() != player.name)) {
		    player.namefield.html(player.name);
		}
	});
    // Remove marker on start of map change
	$(dynmap).bind('mapchanging', function(event) {
		var name;
		for(name in dynmap.players) {
			var player = dynmap.players[name];
			// Turn off marker - let update turn it back on 
			dynmap.playermarkergroup.removeLayer(player.marker);
		}
	});
    // Remove marker on start of map change
	$(dynmap).bind('mapchanging', function(event) {
		var name;
		for(name in dynmap.players) {
			var player = dynmap.players[name];
			dynmap.playermarkergroup.removeLayer(player.marker);
		}
	});
    // Add markers back on end of map change
	$(dynmap).bind('mapchanged', function(event) {
		var name;
		for(name in dynmap.players) {
			var player = dynmap.players[name];
			if(dynmap.world === player.location.world) {
				dynmap.playermarkergroup.addLayer(player.marker);
				var markerPosition = dynmap.getProjection().fromLocationToLatLng(player.location);
				player.marker.setLatLng(markerPosition);
			}
		}
	});
	
	dynmap.playermarkergroup = new L.LayerGroup();
	if(!configuration.hidebydefault)
		dynmap.map.addLayer(dynmap.playermarkergroup);
	dynmap.addToLayerSelector(dynmap.playermarkergroup, configuration.label || 'Players', configuration.layerprio || 0);
};
