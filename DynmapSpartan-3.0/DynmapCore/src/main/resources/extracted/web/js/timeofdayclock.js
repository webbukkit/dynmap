componentconstructors['timeofdayclock'] = function(dynmap, configuration) {
	var me = this;
	
	var timeout = null;
	
	var element = $('<div/>')
		.addClass('largeclock')
		.addClass('timeofday')
		.appendTo(dynmap.options.container);
	
	var sun = $('<div/>')
		.height(60)
		.addClass('timeofday')
		.addClass('sun')
		.css('background-position', (-150) + 'px ' + (-150) + 'px')
		.appendTo(element);
	
	var moon = $('<div/>')
		.height(60)
		.addClass('timeofday')
		.addClass('moon')
		.css('background-position', (-150) + 'px ' + (-150) + 'px')
		.appendTo(sun);
	
	if (configuration.showdigitalclock) {
		var clock = $('<div/>')
			.addClass('timeofday')
			.addClass('digitalclock')
			.appendTo(element);
		
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
				clock
					.addClass(time.day ? 'day' : 'night')
					.removeClass(time.night ? 'day' : 'night')
					.text(formatTime(time));
			}
			else {
				clock
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
	}
	if(configuration.showweather) {
		var weather = $('<div/>')
			.addClass('weather')
			.appendTo(element);
				
		var setWeather = function(hasStorm, isThundering, time) {
			var daynight = (time > 23100 || time < 12900) ? "_day" : "_night";
			var cls = 'sunny';
			if (hasStorm) {
				cls = 'stormy';
				if (isThundering) {
					cls = 'thunder';
				}
			}
			weather
				.removeClass('stormy_day stormy_night sunny_day sunny_night thunder_day thunder_night')
				.addClass(cls + daynight);
		};
		
		$(dynmap).bind('worldupdated', function(event, update) {
			setWeather(update.hasStorm, update.isThundering, update.servertime);
		});
	}
	$(dynmap).bind('worldupdated', function(event, update) {
		var sunangle;
		var time = update.servertime;
		
		if(time > 23100 || time < 12900) {
			//day mode
			var movedtime = time + 900;
			movedtime = (movedtime >= 24000) ? movedtime - 24000 : movedtime;
			//Now we have 0 -> 13800 for the day period
			//Divide by 13800*2=27600 instead of 24000 to compress day
		    sunangle = ((movedtime)/27600 * 2 * Math.PI);
		} else {
			//night mode
			var movedtime = time - 12900;
			//Now we have 0 -> 10200 for the night period
			//Divide by 10200*2=20400 instead of 24000 to expand night
		    sunangle = Math.PI + ((movedtime)/20400 * 2 * Math.PI);
		}
		
		var moonangle = sunangle + Math.PI;

		if(time >= 0) {		
			sun.css('background-position', (-50 * Math.cos(sunangle)) + 'px ' + (-50 * Math.sin(sunangle)) + 'px');
			moon.css('background-position', (-50 * Math.cos(moonangle)) + 'px ' + (-50 * Math.sin(moonangle)) + 'px');
		}
		else {
			sun.css('background-position', '-150px -150px');
			moon.css('background-position', '-150px -150px');
		}
	});
};