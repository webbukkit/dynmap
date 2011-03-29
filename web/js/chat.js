var ip;
$.ajax({
	type: "GET",
	url: "//jsonip.appspot.com/?callback=?",
	dataType: "jsonp",
	success: function(getip) { ip = getip.ip; }
	});
function sendChat(me, message) {
	var data = '{"name":"'+ip+'","message":"'+message+'"}';
	$.ajax({
		type: 'POST',
		url: 'up/sendmessage',
		data: data,
		dataType: 'json',
		success: function(response) {
			//handle response
			if(response)
				me.onPlayerChat('', response);
		}
	});
}

// Provides 'chat'-events by looking at the world-updates.
componentconstructors['chat'] = function(dynmap, configuration) {
	return {
		dynmap: dynmap,
		initialize: function() {
			$(dynmap).bind('worldupdate', function(event, update) {
				swtch(update.type, {
					chat: function() {
						$(dynmap).trigger('chat', [{source: 'player', name: update.playerName, text: update.message}]);
					},
					webchat: function() {
						$(dynmap).trigger('chat', [{source: 'web', name: update.playerName, text: update.message}]);
					}
				});
			});
		}
	};
};

// TODO: Maybe split this to another file.
componentconstructors['chatballoon'] = function(dynmap, configuration) {
	return {
		dynmap: dynmap,
		options: configuration,
		chatpopups: {},
		initialize: function() {
			var me = this;
			$(dynmap).bind('chat', function(event, message) {
				if (message.source != 'player') {
					return;
				}
				var player = dynmap.players[message.name];
				var playerMarker = player && player.marker;
				if (!playerMarker) {
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
						disableAutoPan: !(me.options.focuschatballoons || false),
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
		}
	};
};