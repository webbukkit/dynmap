var ip;
$.ajax({
	type: "GET",
	url: "//jsonip.appspot.com/?callback=?",
	dataType: "jsonp",
	success: function(getip) { ip = getip.ip; }
	});

componentconstructors['chat'] = function(dynmap, configuration) {
	var me = this;
	// Provides 'chat'-events by monitoring the world-updates.
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
	
	if (dynmap.options.allowwebchat) {
		// Accepts 'sendchat'-events to send chat messages to the server.
		$(dynmap).bind('sendchat', function(event, message) {
			var data = '{"name":"'+ip+'","message":"'+message+'"}';
			$.ajax({
				type: 'POST',
				url: 'up/sendmessage',
				data: data,
				dataType: 'json',
				success: function(response) {
					//handle response
					if(response) {
						$(dynmap).trigger('chat', [{source: 'me', name: ip, text: message}]);
					}
				}
			});
		});
	}
};
