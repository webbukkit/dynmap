function CustomMarker(latlng,  map, oncreated) {
	google.maps.OverlayView.call(this);
	
	this.latlng_ = latlng;
	
	// Once the LatLng and text are set, add the overlay to the map.  This will
	// trigger a call to panes_changed which should in turn call draw.
	this.setMap(map);
	
	this.oncreated = oncreated;
}

CustomMarker.prototype = new google.maps.OverlayView();

CustomMarker.prototype.draw = function() {
	var me = this;
	if (this.removed)
		return;
	// Check if the div has been created.
	var div = this.div_;
	if (!div) {
		// Create a overlay text DIV
		div = this.div_ = document.createElement('DIV');
		// Create the DIV representing our CustomMarker
		div.style.position = "absolute";
		
		google.maps.event.addDomListener(div, "click", function(event) {
			google.maps.event.trigger(me, "click");
		});

		this.oncreated(div);
		
		// Then add the overlay to the DOM
		var panes = this.getPanes();
		panes.overlayLayer.appendChild(div);
	}
	
	// Position the overlay 
	var point = this.getProjection().fromLatLngToDivPixel(this.latlng_);
	if (point) {
		div.style.left = point.x + 'px';
		div.style.top = point.y + 'px';
	}
};

CustomMarker.prototype.setPosition = function(p) {
	this.latlng_ = p;
	var projection = this.getProjection();
	if (projection) {
		var point = projection.fromLatLngToDivPixel(this.latlng_);
		this.div_.style.left = point.x + 'px';
		this.div_.style.top = point.y + 'px';
	}
};

CustomMarker.prototype.getPosition = function(p) {
	return this.latlng_;
};

CustomMarker.prototype.hide = function() {
	if (this.div_ && !this.isHidden) {
		this.div_.style.display = 'none';
		this.isHidden = true;
	}
}

CustomMarker.prototype.show = function() {
	if (this.div_ && this.isHidden) {
		this.div_.style.display = 'block';
		this.isHidden = false;
	}
}

CustomMarker.prototype.toggle = function(t) {
	if ((typeof t) == "boolean") {
		if (t) { this.show(); }
		else { this.hide(); }
	} else {
		this.toggle((this.isHidden) == true);
	}
}

CustomMarker.prototype.remove = function() {
	// Check if the overlay was on the map and needs to be removed.
	if (this.div_) {
	this.div_.parentNode.removeChild(this.div_);
	this.div_ = null;
	this.removed = true;
	}
};
