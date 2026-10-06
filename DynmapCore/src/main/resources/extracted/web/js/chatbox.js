componentconstructors['chatbox'] = function(dynmap, configuration) {
	var me = this;
	
	if(dynmap.getBoolParameterByName("hidechat"))
		return;
	var chat = $('<div/>')
		.addClass('chat')
		.appendTo(dynmap.options.container);
	var messagelist = $('<div/>')
		.addClass('messagelist')
		.appendTo(chat);

	if (configuration.visiblelines) {
		messagelist.css('max-height', configuration.visiblelines + 'em');
	}
	else {
		messagelist.css('max-height', '6em');
	}	

	if (configuration.scrollback) {
		messagelist.addClass('scrollback')
			.click( function() { $(this).hide(); } );		 
	}

	if (dynmap.options.allowwebchat) {
	  if(dynmap.options.loggedin || !dynmap.options['webchat-requires-login']) {
		var placeholder = dynmap.options['msg-chatplaceholder'] || 'Press T to chat';
		var chatinput = $('<input/>')
			.addClass('chatinput')
			.attr({
				id: 'chatinput',
				type: 'text',
				value: '',
				maxlength: dynmap.options.chatlengthlimit,
				placeholder: placeholder,
				autocomplete: 'off'
			})
			.keydown(function(event) {
				if (event.keyCode == '13') {
					event.preventDefault();
					if(chatinput.val() != '') {
						$(dynmap).trigger('sendchat', [chatinput.val()]);
						chatinput.val('');
					}
				}
				else if (event.keyCode == '27') {	// Escape closes chat, like in game
					chatinput.blur();
				}
			})
			// While typing, show the recent history too (faded lines included), like in game
			.focus(function() {
				chat.addClass('open');
				chatinput.attr('placeholder', '');
				messagelist.show().scrollTop(messagelist.scrollHeight());
			})
			.blur(function() {
				chat.removeClass('open');
				chatinput.attr('placeholder', placeholder);
				messagelist.scrollTop(messagelist.scrollHeight());
			});
		// 'T' opens chat, like in game
		$(document).keydown(function(event) {
			if ((event.key == 't' || event.key == 'T') && !event.ctrlKey && !event.metaKey && !event.altKey &&
				!$(event.target).is('input, textarea, select, [contenteditable]')) {
				event.preventDefault();
				chatinput.focus();
			}
		});
		if(configuration.sendbutton) {
			var chatbutton = $('<button/>').addClass('chatsendbutton').click(function(event) {
			  if(chatinput.val() != '') {
				$(dynmap).trigger('sendchat', [chatinput.val()]);
				chatinput.val('');
			  }
			}).text("+").appendTo(chat);
		}
		chatinput.appendTo(chat);
		if (configuration.scrollback) {
			chatinput.click(function(){ 
				var m = $('.messagelist');
				m.show().scrollTop(m.scrollHeight());
			});
		}
	  }
	  else {
	  	var login = $('<button/>').addClass('loginbutton').click(function(event) {
	  		window.location = 'login.html';
	  	}).text(dynmap.options['msg-chatrequireslogin']).appendTo(chat);
	  }
	}
	
	var addrow = function(row) {
		if (configuration.scrollback) {
			var c = messagelist.children();
			c.slice(0, Math.max(0, c.length-configuration.scrollback)).each(function(index, elem){ $(elem).remove(); });
		} else {
			// Fade out after messagettl, but keep the line for the history shown while typing
			setTimeout(function() {
				row.fadeOut(600, function() {
					row.addClass('faded').css('display', '');
				});
			}, (configuration.messagettl * 1000));
			var history = messagelist.children();
			history.slice(0, Math.max(0, history.length - 99)).remove();
		}
		messagelist.append(row);
		messagelist.show();
		messagelist.scrollTop(messagelist.scrollHeight());
	};
	
	$(dynmap).bind('playerjoin', function(event, playername) {
		if ((dynmap.options.joinmessage.length > 0) && (playername.length > 0)) {
			addrow($('<div/>')
				.addClass('messagerow messagerow-status')
				.append(dynmap.options.joinmessage.replace('%playername%', playername))
				);
		}
		else if ((dynmap.options['msg-hiddennamejoin'].length > 0) && (playername.length == 0)) {
			addrow($('<div/>')
				.addClass('messagerow messagerow-status')
				.append(dynmap.options['msg-hiddennamejoin'])
				);
		}
	});

	$(dynmap).bind('playerquit', function(event, playername) {
		if ((dynmap.options.quitmessage.length > 0) && (playername.length > 0)) {
			addrow($('<div/>')
				.addClass('messagerow messagerow-status')
				.append(dynmap.options.quitmessage.replace('%playername%', playername))
				);
		}
		else if ((dynmap.options['msg-hiddennamequit'].length > 0) && (playername.length == 0)) {
			addrow($('<div/>')
				.addClass('messagerow messagerow-status')
				.append(dynmap.options['msg-hiddennamequit'])
				);
		}
	});

	$(dynmap).bind('chat', function(event, message) {
		var playerName = message.name;
		var playerAccount = message.account;
		var messageRow = $('<div/>')
			.addClass('messagerow')
			.addClass('source-' + message.source);

		var playerIconContainer = $('<span/>')
			.addClass('messageicon');

		if (message.source === 'player' && configuration.showplayerfaces &&
			playerAccount) {
			getMinecraftHead(playerAccount, 16, function(head) {
				messageRow.icon = $(head)
					.addClass('playerMessageIcon')
					.appendTo(playerIconContainer);
			});
		}

		var playerChannelContainer = '';
		if (message.channel) {
			playerChannelContainer = $('<span/>').addClass('messagetext messagechannel')
			.text('[' + message.channel + '] ')
			.appendTo(messageRow);
		}

		if (message.source === 'player' && configuration.showworld && playerAccount) {
			var playerWorldContainer = $('<span/>')
			 .addClass('messagetext messageworld')
			 .text('['+dynmap.players[playerAccount].location.world.name+']')
			 .appendTo(messageRow);
		}

		var playerNameContainer = '';
		if(message.name) {
			// Punctuation around the name comes from the stylesheet (<name> like in game)
			playerNameContainer = $('<span/>').addClass('messagetext messagename').append(message.name);
		}
		
		var playerMessageContainer = $('<span/>')
			.addClass('messagetext')
			.text(chat_encoder(message));

		messageRow.append(playerIconContainer,playerChannelContainer,playerNameContainer,playerMessageContainer);
		addrow(messageRow);
	});
};
