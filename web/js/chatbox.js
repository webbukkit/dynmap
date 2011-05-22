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
	
	var addrow = function(row) {
		setTimeout(function() { row.remove(); }, (configuration.messagettl * 1000));
		messagelist.append(row);
		messagelist.show();
		messagelist.scrollTop(messagelist.scrollHeight());
	};
	
	$(dynmap).bind('playerjoin', function(event, playername) {
		addrow($('<div/>')
			.addClass('messagerow')
			.text(dynmap.options.joinmessage.replace('%playername%', playername))
			);
	});
	
	$(dynmap).bind('playerquit', function(event, playername) {
		addrow($('<div/>')
			.addClass('messagerow')
			.text(dynmap.options.quitmessage.replace('%playername%', playername))
			);
	});
	
	$(dynmap).bind('chat', function(event, message) {
		var playerName = message.name;
        var playerAccount = message.account;
		var messageRow = $('<div/>')
			.addClass('messagerow');

		var playerIconContainer = $('<span/>')
			.addClass('messageicon');

		if (message.source === 'player' && configuration.showplayerfaces &&
            playerAccount) {
			getMinecraftHead(playerAccount, 16, function(head) {
				messageRow.icon = $(head)
					.addClass('playerIcon')
					.appendTo(playerIconContainer);
			});
		}

        var playerChannelContainer = '';
        if (message.channel) {
            playerChannelContainer = $('<span/>').addClass('messagetext')
            .text('[' + message.channel + '] ')
            .appendTo(messageRow);
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

		messageRow.append(playerIconContainer,playerChannelContainer,playerNameContainer,playerMessageContainer);
		addrow(messageRow);
	});
};