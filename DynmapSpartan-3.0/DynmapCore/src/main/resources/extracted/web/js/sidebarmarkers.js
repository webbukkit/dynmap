componentconstructors['sidebarmarkers'] = function(dynmap, configuration) {
	var cfg = $.extend({
		title: 'Markers (current world)'
	}, configuration);

	this.markersSection = null;
	this.markersList = null;
	
	var me = this;
	
	$(dynmap).bind('markersupdated', function(event, markersets) {
		updateMarkers(markersets);
	});
	
	if (typeof dynmapmarkersets !== 'undefined' && dynmapmarkersets) {
		updateMarkers(dynmapmarkersets);
	}
	
	function initSection() {
		me.markersSection = SidebarUtils.createListSection(cfg.title);
		me.markersList = me.markersSection.content.addClass('markerslist');
		dynmap.sidebarPanel.find('fieldset:eq(0)').after(me.markersSection.section);
		dynmap.sidebarSections.push(me.markersSection);
	}
	
	function updateMarkers(markersets) {
		if (me.markersList == null) {
			initSection();
		}
		
		me.markersList.empty();
		var sets = [];
		
		$.each(markersets, function (key, set) {
			if (!set.markers || $.isEmptyObject(set.markers)) {
				return;
			}
			
			var markers = $('<ul/>').addClass('sublist');
			
			$.each(set.markers, function (key, marker) {
				var title = marker.label + ' (' + marker.x + ',' + marker.y + ',' + marker.z + ')';
				var imgURL = concatURL(dynmap.options.url.markers, '_markers_/'+marker.icon+'.png');
				
				$('<li/>')
					.addClass('marker item')
					.append($('<a/>')
						.attr({ title: title, href: '#' })
						.css({ backgroundImage: 'url(' + imgURL + ')' })
						.text(title)
					)
					.click(function() {
						dynmap.panToLocation({
							world: dynmap.world,
							x: marker.x,
							y: marker.y,
							z: marker.z
						});
					})
					.appendTo(markers);
			});
			
			sets.push($('<li/>')
				.addClass('markerset subsection')
				.append($('<span/>').text(set.label))
				.append(markers)
			);
		});
		
		me.markersList.append(sets);
		dynmap.updateSidebarHeight();
	}
};
