function MinecraftDigitalClock(element) {
	this.create(element);
}
MinecraftDigitalClock.prototype = {
	element: null,
	timeout: null,
	time: null,
	create: function(element) {
		this.element = element;
		$(element).addClass('clock');
	},
	setTime: function(time) {
		if (this.timeout != null) {
			window.clearTimeout(this.timeout);
			this.timeout = null;
		}
		this.time = getMinecraftTime(time);
		this.element
			.addClass(this.time.day ? 'day' : 'night')
			.removeClass(this.time.night ? 'day' : 'night')
			.text(this.formatTime(this.time));
		
		if (this.timeout == null) {
			var me = this;
			this.timeout = window.setTimeout(function() {
				me.timeout = null;
				me.setTime(me.time.servertime+(1000/60));
			}, 700);
		}
	},
	formatTime: function(time) {
		var formatDigits = function(n, digits) {
			var s = n.toString();
			while (s.length < digits) {
				s = '0' + s;
			}
			return s;
		}
		return formatDigits(time.hours, 2) + ':' + formatDigits(time.minutes, 2);
	}
};
clocks.digital = function(element) { return new MinecraftDigitalClock(element); };