var SidebarUtils = {
	createSection: function (labelText, content) {
		var legend = $('<legend/>').text(labelText);
		var upBtn = SidebarUtils.createScrollButton(true, content);
		var downBtn = SidebarUtils.createScrollButton(false, content);
		
		var section = $('<fieldset/>')
			.addClass('section')
			.append(legend)
			.append(upBtn)
			.append(
				content
					.addClass('content')
					.bind('mousewheel', function(event, delta){
						this.scrollTop -= (delta * 10);
						event.preventDefault();
					})
			)
			.append(downBtn);
		
		return {
			section: section,
			legend: legend,
			upBtn: upBtn,
			content: content,
			downBtn: downBtn
		};
	},
	
	createListSection: function (labelText) {
		var content = $('<ul/>').addClass('list');
		return SidebarUtils.createSection(labelText, content);
	},
	
	createScrollButton: function (up, target) {
		var cls = up ? 'scrollup' : 'scrolldown';
		var amount = (up ? '-' : '+') + '=300px';
		
		return $('<div/>')
			.addClass(cls)
			.bind('mousedown mouseup touchstart touchend', function (event) {
		    	if (event.type == 'mousedown' || event.type == 'touchstart') {
					target.animate({"scrollTop": amount}, 3000, 'linear');
		    	} else {
		        	target.stop();
		    	}
			});
	}
};
