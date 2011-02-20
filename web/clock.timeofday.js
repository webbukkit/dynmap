function MinecraftTimeOfDay(element,elementsun,elementmoon) {
	this.create(element, elementsun, elementmoon);
}
MinecraftTimeOfDay.prototype = {
	element: null,
	elementsun: null,
	elementmoon: null,
	create: function(element,elementsun,elementmoon) {
		if (!element) element = $('<div/>');
		this.element = element;
		
		if (!elementsun) elementsun = $('<div/>');
		this.elementsun = elementsun;
		this.elementsun.appendTo(this.element);
		if (!elementmoon) elementmoon = $('<div/>');
		this.elementmoon = elementmoon;
		this.elementmoon.appendTo(this.elementsun);
		this.element.height(60);
		this.element.addClass('timeofday');
		this.elementsun.height(60);
		this.elementsun.addClass('timeofday');
		this.elementsun.addClass('sun');
		this.elementmoon.height(60);
		this.elementmoon.addClass('timeofday');
		this.elementmoon.addClass('moon');
		this.elementmoon.html("&nbsp;&rlm;&nbsp;");
		this.elementsun.css("background-position", (-150) + "px " + (-150) + "px");
		this.elementmoon.css("background-position", (-150) + "px " + (-150) + "px");
		
		return element;
	},
	setTime: function(time) {
		var sunangle;
		
		if(time > 23100 || time < 12900)
		{
			//day mode
			var movedtime = time + 900;
			movedtime = (movedtime >= 24000) ? movedtime - 24000 : movedtime;
			//Now we have 0 -> 13800 for the day period
			//Devide by 13800*2=27600 instead of 24000 to compress day
		    sunangle = ((movedtime)/27600 * 2 * Math.PI);
		}
		else
		{
			//night mode
			var movedtime = time - 12900;
			//Now we have 0 -> 10200 for the night period
			//Devide by 10200*2=20400 instead of 24000 to expand night
		    sunangle = Math.PI + ((movedtime)/20400 * 2 * Math.PI);
		}
		
		var moonangle = sunangle + Math.PI;
		
		this.elementsun.css("background-position", (-50 * Math.cos(sunangle)) + "px " + (-50 * Math.sin(sunangle)) + "px");
		this.elementmoon.css("background-position", (-50 * Math.cos(moonangle)) + "px " + (-50 * Math.sin(moonangle)) + "px");
	}
};
clocks.timeofday = function(element) { return new MinecraftTimeOfDay(element); };