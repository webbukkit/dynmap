
var dynmapmarkersets = {};

componentconstructors['markers'] = function(dynmap, configuration) {
	var me = this;

	function loadmarkers(world) {
		dynmapmarkersets = {};	
		$.getJSON(dynmap.options.tileUrl+'_markers_/marker_'+world+'.json', function(data) {
			var ts = data.timestamp;
			$.each(data.sets, function(name, markerset) {
				dynmapmarkersets[name] = markerset;
			});
		});
	}
	
	$(dynmap).bind('component.markers', function(event, msg) {
		console.log('got marker event - ' + msg.ctype + ', ' + msg.msg);
	});
	
    // Remove marker on start of map change
	$(dynmap).bind('mapchanging', function(event) {
	});
    // Remove marker on map change - let update place it again
	$(dynmap).bind('mapchanged', function(event) {
	});
	// Load markers for new world
	$(dynmap).bind('worldchanged', function(event) {
		loadmarkers(this.world.name);
	});
	
	loadmarkers(dynmap.world.name);

};