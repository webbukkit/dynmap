componentconstructors['digitalclock'] = function(dynmap, configuration) {
	var element = $('<div/>')
		.addClass('digitalclock')
		.addClass('largeclock')
		.appendTo(dynmap.options.container);
	
	var timeout = null;
	
	var formatTime = function(time) {
		var formatDigits = function(n, digits) {
			var s = n.toString();
			while (s.length < digits) {
				s = '0' + s;
			}
			return s;
		}
		return formatDigits(time.hours, 2) + ':' + formatDigits(time.minutes, 2);
	};
	
	var setTime = function(servertime) {
		if (timeout != null) {
			window.clearTimeout(timeout);
			timeout = null;
		}
		var time = null;
		if(servertime >= 0) {
			time = getMinecraftTime(servertime);
			element
				.addClass(time.day ? 'day' : 'night')
				.removeClass(time.night ? 'day' : 'night')
				.text(formatTime(time));
		}
		else {
			element
				.removeClass('day night')
				.text('');
		}
		if ((timeout == null) && (time != null)) {
			timeout = window.setTimeout(function() {
				timeout = null;
				setTime(time.servertime+(1000/60));
			}, 700);
		}
	};

	$(dynmap).bind('worldupdated', function(event, update) {
		setTime(update.servertime);
	});
};