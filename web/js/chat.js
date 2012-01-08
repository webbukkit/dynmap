var ip;
$.ajax({
	type: "GET",
	url: "//jsonip.appspot.com/?callback=?",
	dataType: "jsonp",
	success: function(getip) { ip = getip.ip; }
	});

componentconstructors['chat'] = function(dynmap, configuration) {
	var me = this;
	
	if(dynmap.getBoolParameterByName("hidechat"))
		return;
		
	// Provides 'chat'-events by monitoring the world-updates.
	$(dynmap).bind('worldupdate', function(event, update) {
		swtch(update.type, {
			chat: function() {
				$(dynmap).trigger('chat', [{source: update.source, name: update.playerName, text: update.message, account: update.account,
                channel: update.channel}]);
			}
		});
	});
	var pname = null;
	if(configuration.allowurlname) {
		pname = dynmap.getParameterByName("chatname");
		if(pname == "") pname = null;
	}
	
	if (dynmap.options.allowwebchat) {
		// Accepts 'sendchat'-events to send chat messages to the server.
		$(dynmap).bind('sendchat', function(event, message) {
			var data = '{"name":'+JSON.stringify(pname?pname:(ip?ip:""))+',"message":'+JSON.stringify(message)+'}';
			$.ajax({
				type: 'POST',
		        contentType: "application/json; charset=utf-8",
				url: config.url.sendmessage,
				data: data,
				dataType: 'json',
				success: function(response) {
					//handle response
					if(response) {
						$(dynmap).trigger('chat', [{source: 'me', name: ip, text: message}]);
					}
				},
				error: function(xhr) {
					if (xhr.status === 403) {
						$(dynmap).trigger('chat', [{source: 'me', name: 'Error', text: dynmap.options.spammessage.replace('%interval%', dynmap.options['webchat-interval'])}]);
					}
				}
			});
		});
	}
};
