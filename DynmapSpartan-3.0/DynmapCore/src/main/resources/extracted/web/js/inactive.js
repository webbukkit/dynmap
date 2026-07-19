componentconstructors['inactive'] = function(dynmap, configuration) {
	var me = this;
	var inactivetimer = null;
	$(document)
		.ready(onactivity)
		.mousemove(onactivity)
		.mouseup(onactivity)
		.keypress(onactivity);
	function onactivity() {
		clearTimeout(inactivetimer);
		inactivetimer = setTimeout(oninactive, (configuration.timeout || 1800)*1000);
	}
	function oninactive() {
		if (configuration.showmessage) {
			alert(configuration.showmessage);
		}
		if (configuration.redirecturl) {
			window.location = configuration.redirecturl;
		}
	}
};
