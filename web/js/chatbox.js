componentconstructors['chatbox'] = function(dynmap, configuration) {
	var me = this;
	var chat = $('<div/>')
		.addClass('chat')
		.appendTo(dynmap.options.container);
	var messagelist = $('<div/>')
		.addClass('messagelist')
		.appendTo(chat);
	
	if (dynmap.options.allowwebchat) {
		var chatinput = $('<input/>')
			.addClass('chatinput')
			.attr({
				id: 'chatinput',
				type: 'text',
				value: ''
			})
			.keydown(function(event) {
				if (event.keyCode == '13') {
					event.preventDefault();
					if(chatinput.val() != '') {
						$(dynmap).trigger('sendchat', [chatinput.val()]);
						chatinput.val('');
					}
				}
			})
			.appendTo(chat);
	}
	
	$(dynmap).bind('chat', function(event, message) {
		var playerName = message.name;
		var messageRow = $('<div/>')
			.addClass('messagerow');

		var playerIconContainer = $('<span/>')
			.addClass('messageicon');

		if (message.source === 'player' && configuration.showplayerfaces) {
			getMinecraftHead(playerName, 16, function(head) {
				messageRow.icon = $(head)
					.addClass('playerIcon')
					.appendTo(playerIconContainer);
			});
		}

		if (message.source === 'player' && configuration.showworld) {
			var playerWorldContainer = $('<span/>')
			 .addClass('messagetext')
			 .text('['+dynmap.players[message.name].location.world.name+']')
			 .appendTo(messageRow);
		}

		var playerNameContainer = $('<span/>')
			.addClass('messagetext')
			.text(' '+message.name+': ');

		var playerMessageContainer = $('<span/>')
			.addClass('messagetext')
			.text(message.text);

		messageRow.append(playerIconContainer,playerNameContainer,playerMessageContainer);
		//messageRow.append(playerIconContainer,playerWorldContainer,playerGroupContainer,playerNameContainer,playerMessageContainer);
		setTimeout(function() { messageRow.remove(); }, (configuration.messagettl * 1000));
		messagelist.append(messageRow);
		
		messagelist.show();
		//var scrollHeight = jQuery(me.messagelist).attr('scrollHeight');
		messagelist.scrollTop(messagelist.scrollHeight());
	});
};