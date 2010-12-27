/**
 * This constructor creates a label and associates it with a marker.
 * It is for the private use of the MarkerWithLabel class.
 * @constructor
 * @param {Marker} marker The marker with which the label is to be associated.
 * @private
 */
function MarkerLabel_(marker) {
  this.marker_ = marker;

  this.labelDiv_ = document.createElement("div");
  this.labelDiv_.style.cssText = "position: absolute; overflow: hidden;";

  // Set up the DIV for handling mouse events in the label. This DIV forms a transparent veil
  // in the "overlayMouseTarget" pane, a veil that covers just the label. This is done so that
  // events can be captured even if the label is in the shadow of a google.maps.InfoWindow.
  // Code is included here to ensure the veil is always exactly the same size as the label.
  this.eventDiv_ = document.createElement("div");
  this.eventDiv_.style.cssText = this.labelDiv_.style.cssText;
}

// MarkerLabel_ inherits from OverlayView:
MarkerLabel_.prototype = new google.maps.OverlayView();

/**
 * Adds the DIV representing the label to the DOM. This method is called
 * automatically when the marker's <code>setMap</code> method is called.
 * @private
 */
MarkerLabel_.prototype.onAdd = function () {
  var me = this;
  var cMouseIsDown = false;
  var cDraggingInProgress = false;
  var cSavedPosition;
  var cSavedZIndex;
  var cLatOffset, cLngOffset;
  var cIgnoreClick;

  // Stops all processing of an event.
  //
  var cAbortEvent = function (e) {
    if (e.preventDefault) {
      e.preventDefault();
    }
    e.cancelBubble = true;
    if (e.stopPropagation) {
      e.stopPropagation();
    }
  };

  this.getPanes().overlayImage.appendChild(this.labelDiv_);
  this.getPanes().overlayMouseTarget.appendChild(this.eventDiv_);

  this.listeners_ = [
    google.maps.event.addDomListener(document, "mouseup", function (mEvent) {
      if (cDraggingInProgress) {
        mEvent.latLng = cSavedPosition;
        cIgnoreClick = true; // Set flag to ignore the click event reported after a label drag
        google.maps.event.trigger(me.marker_, "dragend", mEvent);
      }
      cMouseIsDown = false;
      google.maps.event.trigger(me.marker_, "mouseup", mEvent);
    }),
    google.maps.event.addListener(me.marker_.getMap(), "mousemove", function (mEvent) {
      if (cMouseIsDown && me.marker_.getDraggable()) {
        // Change the reported location from the mouse position to the marker position:
        mEvent.latLng = new google.maps.LatLng(mEvent.latLng.lat() - cLatOffset, mEvent.latLng.lng() - cLngOffset);
        cSavedPosition = mEvent.latLng;
        if (cDraggingInProgress) {
          google.maps.event.trigger(me.marker_, "drag", mEvent);
        } else {
          // Calculate offsets from the click point to the marker position:
          cLatOffset = mEvent.latLng.lat() - me.marker_.getPosition().lat();
          cLngOffset = mEvent.latLng.lng() - me.marker_.getPosition().lng();
          google.maps.event.trigger(me.marker_, "dragstart", mEvent);
        }
      }
    }),
    google.maps.event.addDomListener(this.eventDiv_, "mouseover", function (e) {
      //me.eventDiv_.style.cursor = "pointer";
      google.maps.event.trigger(me.marker_, "mouseover", e);
    }),
    google.maps.event.addDomListener(this.eventDiv_, "mouseout", function (e) {
      //me.eventDiv_.style.cursor = me.marker_.getCursor();
      google.maps.event.trigger(me.marker_, "mouseout", e);
    }),
    google.maps.event.addDomListener(this.eventDiv_, "click", function (e) {
      if (cIgnoreClick) { // Ignore the click reported when a label drag ends
        cIgnoreClick = false;
      } else {
        cAbortEvent(e); // Prevent click from being passed on to map
        google.maps.event.trigger(me.marker_, "click", e);
      }
    }),
    google.maps.event.addDomListener(this.eventDiv_, "dblclick", function (e) {
      cAbortEvent(e); // Prevent map zoom when double-clicking on a label
      google.maps.event.trigger(me.marker_, "dblclick", e);
    }),
    google.maps.event.addDomListener(this.eventDiv_, "mousedown", function (e) {
      cMouseIsDown = true;
      cDraggingInProgress = false;
      cLatOffset = 0;
      cLngOffset = 0;
      cAbortEvent(e); // Prevent map pan when starting a drag on a label
      google.maps.event.trigger(me.marker_, "mousedown", e);
    }),
    google.maps.event.addListener(this.marker_, "dragstart", function (mEvent) {
      cDraggingInProgress = true;
      cSavedZIndex = me.marker_.getZIndex();
    }),
    google.maps.event.addListener(this.marker_, "drag", function (mEvent) {
      me.marker_.setPosition(mEvent.latLng);
      me.marker_.setZIndex(1000000); // Moves the marker to the foreground during a drag
    }),
    google.maps.event.addListener(this.marker_, "dragend", function (mEvent) {
      cDraggingInProgress = false;
      me.marker_.setZIndex(cSavedZIndex);
    }),
    google.maps.event.addListener(this.marker_, "position_changed", function () {
      me.setPosition();
    }),
    google.maps.event.addListener(this.marker_, "zindex_changed", function () {
      me.setZIndex();
    }),
    google.maps.event.addListener(this.marker_, "visible_changed", function () {
      me.setVisible();
    }),
    google.maps.event.addListener(this.marker_, "labelvisible_changed", function () {
      me.setVisible();
    }),
    google.maps.event.addListener(this.marker_, "title_changed", function () {
      me.setTitle();
    }),
    google.maps.event.addListener(this.marker_, "labelcontent_changed", function () {
      me.setContent();
    }),
    google.maps.event.addListener(this.marker_, "labelanchor_changed", function () {
      me.setAnchor();
    }),
    google.maps.event.addListener(this.marker_, "labelclass_changed", function () {
      me.setStyles();
    }),
    google.maps.event.addListener(this.marker_, "labelstyle_changed", function () {
      me.setStyles();
    })
  ];
};

/**
 * Removes the DIV for the label from the DOM. It also removes all event handlers.
 * This method is called automatically when the marker's <code>setMap(null)</code>
 * method is called.
 * @private
 */
MarkerLabel_.prototype.onRemove = function () {
  var i;
  this.labelDiv_.parentNode.removeChild(this.labelDiv_);
  this.eventDiv_.parentNode.removeChild(this.eventDiv_);

  // Remove event listeners:
  for (i = 0; i < this.listeners_.length; i++) {
    google.maps.event.removeListener(this.listeners_[i]);
  }
};

/**
 * Draws the label on the map.
 * @private
 */
MarkerLabel_.prototype.draw = function () {
  this.setContent();
  this.setTitle();
  this.setStyles();
};

/**
 * Sets the content of the label.
 * The content can be plain text or an HTML DOM node.
 * @private
 */
MarkerLabel_.prototype.setContent = function () {
  var content = this.marker_.get("labelContent");
  if (typeof content.nodeType === "undefined") {
    this.labelDiv_.innerHTML = content;
    this.eventDiv_.innerHTML = this.labelDiv_.innerHTML;
  } else {
    this.labelDiv_.appendChild(content);
    content = content.cloneNode(true);
    this.eventDiv_.appendChild(content);
  }
};

/**
 * Sets the content of the tool tip for the label. It is
 * always set to be the same as for the marker itself.
 * @private
 */
MarkerLabel_.prototype.setTitle = function () {
  this.eventDiv_.title = this.marker_.getTitle() || "";
};

/**
 * Sets the style of the label by setting the style sheet and applying
 * other specific styles requested.
 * @private
 */
MarkerLabel_.prototype.setStyles = function () {
  var i, labelStyle;

  // Apply style values from the style sheet defined in the labelClass parameter:
  this.labelDiv_.className = this.marker_.get("labelClass");
  this.eventDiv_.className = this.labelDiv_.className;

  // Clear existing inline style values:
  this.labelDiv_.style.cssText = "";
  this.eventDiv_.style.cssText = "";
  // Apply style values defined in the labelStyle parameter:
  labelStyle = this.marker_.get("labelStyle");
  for (i in labelStyle) {
    if (labelStyle.hasOwnProperty(i)) {
      this.labelDiv_.style[i] = labelStyle[i];
      this.eventDiv_.style[i] = labelStyle[i];
    }
  }
  this.setMandatoryStyles();
};

/**
 * Sets the mandatory styles to the DIV representing the label as well as to the
 * associated event DIV. This includes setting the DIV position, zIndex, and visibility.
 * @private
 */
MarkerLabel_.prototype.setMandatoryStyles = function () {
  this.labelDiv_.style.position = "absolute";
  this.labelDiv_.style.overflow = "hidden";
  // Make sure the opacity setting causes the desired effect on MSIE:
  if (typeof this.labelDiv_.style.opacity !== "undefined") {
    this.labelDiv_.style.filter = "alpha(opacity=" + (this.labelDiv_.style.opacity * 100) + ")";
  }

  this.eventDiv_.style.position = this.labelDiv_.style.position;
  this.eventDiv_.style.overflow = this.labelDiv_.style.overflow;
  this.eventDiv_.style.opacity = 0.01; // Don't use 0; DIV won't be clickable on MSIE
  this.eventDiv_.style.filter = "alpha(opacity=1)"; // For MSIE
  
  this.setAnchor();
  this.setPosition(); // This also updates zIndex, if necessary.
  this.setVisible();
};

/**
 * Sets the anchor point of the label.
 * @private
 */
MarkerLabel_.prototype.setAnchor = function () {
  var anchor = this.marker_.get("labelAnchor");
  this.labelDiv_.style.marginLeft = -anchor.x + "px";
  this.labelDiv_.style.marginTop = -anchor.y + "px";
  this.eventDiv_.style.marginLeft = -anchor.x + "px";
  this.eventDiv_.style.marginTop = -anchor.y + "px";
};

/**
 * Sets the position of the label. The zIndex is also updated, if necessary.
 * @private
 */
MarkerLabel_.prototype.setPosition = function () {
  var position = this.getProjection().fromLatLngToDivPixel(this.marker_.getPosition());
  
  this.labelDiv_.style.left = position.x + "px";
  this.labelDiv_.style.top = position.y + "px";
  this.eventDiv_.style.left = this.labelDiv_.style.left;
  this.eventDiv_.style.top = this.labelDiv_.style.top;

  this.setZIndex();
};

/**
 * Sets the zIndex of the label. If the marker's zIndex property has not been defined, the zIndex
 * of the label is set to the vertical coordinate of the label. This is in keeping with the default
 * stacking order for Google Maps: markers to the south are in front of markers to the north.
 * @private
 */
MarkerLabel_.prototype.setZIndex = function () {
  var zAdjust = (this.marker_.get("labelInBackground") ? -1 : +1);
  if (typeof this.marker_.getZIndex() === "undefined") {
    this.labelDiv_.style.zIndex = parseInt(this.labelDiv_.style.top, 10) + zAdjust;
    this.eventDiv_.style.zIndex = this.labelDiv_.style.zIndex;
  } else {
    this.labelDiv_.style.zIndex = this.marker_.getZIndex() + zAdjust;
    this.eventDiv_.style.zIndex = this.labelDiv_.style.zIndex;
  }
};

/**
 * Sets the visibility of the label. The label is visible only if the marker itself is
 * visible (i.e., its visible property is true) and the labelVisible property is true.
 * @private
 */
MarkerLabel_.prototype.setVisible = function () {
  if (this.marker_.get("labelVisible")) {
    this.labelDiv_.style.display = this.marker_.getVisible() ? "block" : "none";
  } else {
    this.labelDiv_.style.display = "none";
  }
  this.eventDiv_.style.display = this.labelDiv_.style.display;
};

/**
 * @name MarkerWithLabelOptions
 * @class This class represents the optional parameter passed to the {@link MarkerWithLabel} constructor.
 *  The properties available are the same as for <code>google.maps.Marker</code> with the addition
 *  of the properties listed below. To change any of these additional properties after the labeled
 *  marker has been created, call <code>google.maps.Marker.set(propertyName, propertyValue)</code>.
 *  <p>
 *  When any of these properties changes, a property changed event is fired. The names of these
 *  events are derived from the name of the property and are of the form <code>propertyname_changed</code>.
 *  For example, if the content of the label changes, a <code>labelcontent_changed</code> event
 *  is fired.
 *  <p>
 * @property {string|Node} [labelContent] The content of the label (plain text or an HTML DOM node).
 * @property {Point} [labelAnchor] By default, a label is drawn with its anchor point at (0,0) so
 *  that its top left corner is positioned at the anchor point of the associated marker. Use this
 *  property to change the anchor point of the label. For example, to center a 50px-wide label
 *  beneath a marker, specify a <code>labelAnchor</code> of <code>google.maps.Point(25, 0)</code>.
 *  (Note: x-values increase to the right and y-values increase to the bottom.)
 * @property {string} [labelClass] The name of the CSS class defining the styles for the label.
 *  Note that style values for <code>position</code>, <code>overflow</code>, <code>top</code>,
 *  <code>left</code>, <code>zIndex</code>, <code>display</code>, <code>marginLeft</code>, and
 *  <code>marginTop</code> are ignored; these styles are for internal use only.
 * @property {Object} [labelStyle] An object literal whose properties define specific CSS
 *  style values to be applied to the label. Style values defined here override those that may
 *  be defined in the <code>labelClass</code> style sheet. If this property is changed after the
 *  label has been created, all previously set styles (except those defined in the style sheet)
 *  are removed from the label before the new style values are applied.
 *  Note that style values for <code>position</code>, <code>overflow</code>, <code>top</code>,
 *  <code>left</code>, <code>zIndex</code>, <code>display</code>, <code>marginLeft</code>, and
 *  <code>marginTop</code> are ignored; these styles are for internal use only.
 * @property {boolean} [labelInBackground] A flag indicating whether a label that overlaps its
 *  associated marker should appear in the background (i.e., in a plane below the marker).
 *  The default is <code>false</code>, which causes the label to appear in the foreground.
 * @property {boolean} [labelVisible] A flag indicating whether the label is to be visible.
 *  The default is <code>true</code>. Note that even if <code>labelVisible</code> is
 *  <code>true</code>, the label will <i>not</i> be visible unless the associated marker is also
 *  visible (i.e., unless the marker's <code>visible</code> property is <code>true</code>).
 */
/**
 * Creates a MarkerWithLabel with the options specified in {@link MarkerWithLabelOptions}.
 * @constructor
 * @param {MarkerWithLabelOptions} [opt_options] The optional parameters.
 */
function MarkerWithLabel(opt_options) {
  opt_options = opt_options || {};
  opt_options.labelContent = opt_options.labelContent || "";
  opt_options.labelAnchor = opt_options.labelAnchor || new google.maps.Point(0, 0);
  opt_options.labelClass = opt_options.labelClass || "markerLabels";
  opt_options.labelStyle = opt_options.labelStyle || {};
  opt_options.labelInBackground = opt_options.labelInBackground || false;
  if (typeof opt_options.labelVisible === "undefined") {
    opt_options.labelVisible = true;
  }

  this.label = new MarkerLabel_(this); // Bind the label to the marker

  // Call the parent constructor. It calls Marker.setValues to initialize, so all
  // the new parameters are conveniently saved and can be accessed with get/set.
  // Marker.set triggers a property changed event (called "propertyname_changed")
  // that the marker label listens for in order to react to state changes.
  google.maps.Marker.apply(this, arguments);
}

// MarkerWithLabel inherits from <code>Marker</code>:
MarkerWithLabel.prototype = new google.maps.Marker();

MarkerWithLabel.prototype.setMap = function (theMap) {
  // Call the inherited function...
  google.maps.Marker.prototype.setMap.apply(this, arguments);

  // ... then deal with the label:
  this.label.setMap(theMap);
};



/* generic function for making an XMLHttpRequest
 *  url:   request URL
 *  func:  callback function for success
 *  type:  'text' by default (callback is called with response text)
 *         otherwise, callback is called with a parsed XML dom
 *  fail:  callback function for failure
 *  post:  if given, make a POST request instead of GET; post data given
 *
 *  contenttype: if given for a POST, set request content-type header
 */
function makeRequest(url, func, type, fail, post, contenttype)
{
	var http_request = false;

	type = typeof(type) != 'undefined' ? type : 'text';
	fail = typeof(fail) != 'undefined' ? fail : function() { };

	if(window.XMLHttpRequest) {
		http_request = new XMLHttpRequest();
	} else if(window.ActiveXObject) {
		http_request = new ActiveXObject("Microsoft.XMLHTTP");
	}

	if(type == 'text') {
		http_request.onreadystatechange = function() {
			if(http_request.readyState == 4) {
				if(http_request.status == 200) {
					func(http_request.responseText);
				} else {
					fail(http_request);
				}
			}
		}
	} else {
		http_request.onreadystatechange = function() {
			if(http_request.readyState == 4) {
				if(http_request.status == 200) { func(http_request.responseXML); } else {
					fail(http_request);
				}
			}
		}
	}

	if(typeof(post) != 'undefined') {
		http_request.open('POST', url, true);
		if(typeof(contenttype) != 'undefined')
			http_request.setRequestHeader("Content-Type", contenttype);
		http_request.send(post);
	} else {
		http_request.open('GET', url, true);
		http_request.send(null);
	}
}
 
 
 	var config = {
		tileUrl:     setup.tileUrl,
		updateUrl:   setup.updateUrl,
		tileWidth:   128,
		tileHeight:  128,
		updateRate:  setup.updateRate,
		zoomSize:    [ 128, 128, 256, 512 ]
	};

	function MCMapProjection() {
	  }

	MCMapProjection.prototype.fromLatLngToPoint = function(latLng) {
		var x = (latLng.lng() * config.tileWidth)|0;
		var y = (latLng.lat() * config.tileHeight)|0;

		if(map.zoom == 0) {
			x += config.tileWidth / 2;
		}
		return new google.maps.Point(x, y);
	};

	MCMapProjection.prototype.fromPointToLatLng = function(point) {
		var x = point.x;
		if(map.zoom == 0)
			x -= config.tileWidth / 2;
		var lng = x / config.tileWidth;
		var lat = point.y / config.tileHeight;
		return new google.maps.LatLng(lat, lng);
	};

	function fromWorldToLatLng(x, y, z)
	{
		var dx = +x;
		var dy = +y - 127;
		var dz = +z;
		var px = dx + dz;
		var py = dx - dz - dy;

		var lng = -px / config.tileWidth / 2 + 0.5;
		var lat = py / config.tileHeight / 2;

		return new google.maps.LatLng(lat, lng);
	}

	function mcMapType() {
	}

	var tileDict = new Array();
	var lastSeen = new Array();

	function tileUrl(tile, always) {
		if(always) {
			var now = new Date();
			return config.tileUrl + tile + '.png?' + now.getTime();
		} else if(tile in lastSeen) {
			return config.tileUrl + tile + '.png?' + lastSeen[tile];
		} else {
			return config.tileUrl + tile + '.png?0';
		}
	}

	function imgSubst(tile) {
		if(!(tile in tileDict))
			return;

		var src = tileUrl(tile);
		var t = tileDict[tile];
		t.src = src;
		t.style.display = '';
		t.onerror = function() {
			setTimeout(function() {
				t.src = tileUrl(tile, 1);
			}, 1000);
			t.onerror = '';
		}
	}

	var caveMode = false;

	function caveSwitch()
	{
		caveMode = !caveMode;

		if(caveMode) {
			cavebtn.src = 'cave_on.png';
			map.setMapTypeId('cavemap');
		} else {
			cavebtn.src = 'cave_off.png';
			map.setMapTypeId('mcmap');
		}
	}

	mcMapType.prototype.tileSize = new google.maps.Size(config.tileWidth, config.tileHeight);
	mcMapType.prototype.minZoom = 0;
	mcMapType.prototype.maxZoom = 3;
	mcMapType.prototype.getTile = function(coord, zoom, doc) {
		var img = doc.createElement('IMG');

		img.onerror = function() { img.style.display = 'none'; }

		img.style.width = config.zoomSize[zoom] + 'px';
		img.style.height = config.zoomSize[zoom] + 'px';
		img.style.borderStyle = 'none';

		var pfx = caveMode ? "c" : "";

		if(zoom > 0) {
			var tilename = pfx + "t_" + (- coord.x * config.tileWidth) + '_' + coord.y * config.tileHeight;
		} else {
			var tilename = pfx + "zt_" + (- coord.x * config.tileWidth * 2) + '_' + coord.y * config.tileHeight * 2;
		}

		tileDict[tilename] = img;

		var url = tileUrl(tilename);
		img.src = url;
		//img.style.background = 'url(' + url + ')';
		//img.innerHTML = '<small>' + tilename + '</small>';

		return img;
	}

	var markers = new Array();
	var lasttimestamp = 0;
	var followPlayer = '';

	var lst;
	var plistbtn;
	var cavebtn;
	var lstopen = true;
	var oldplayerlst = '[Connecting]';
	var servertime = 0;

	function mapUpdate()
	{
		makeRequest(config.updateUrl + lasttimestamp, function(res) {
			var rows = res.split('\n');
			var loggedin = new Array();
			var showWarps = document.getElementById('showWarps').checked;
			var showSigns = document.getElementById('showSigns').checked;
			var showHomes = document.getElementById('showHomes').checked;
			var showSpawn = document.getElementById('showSpawn').checked;
 			var firstRow = rows[0].split(' ');
			var lasttimestamp = firstRow[0];
			servertime = firstRow[1];
			delete rows[0];
 			var playerlst = '';
			var numwarps = 0;
			var numsigns = 0;
			var numhomes = 0;
			var numspawns = 0;
			var numplayers = 0;
 
			for(var line in rows) {
				var p = rows[line].split(' ');
				
				if(p[0] == '') continue;
				
				// Hack to keep duplicate markers from conflicting with eachother
				if (p[1] != 'player' && p.length == 5) {
					p[0] = p[0] + '<span style="display:none;">' + p[1] + '</span>';
				}				
				loggedin[p[0]] = 1;
				
				if(p.length == 5) {
					var image = p[1] + '.png';
					
					if (p[1] == 'player') {
						if(playerlst != '') playerlst += '<br />';
						playerlst += '<img id="icon_' + p[0] + '" title="Follow" class="plicon" src="' + (p[0] == followPlayer ? 'follow_on.png' : 'follow_off.png') + '" onclick="plfollow(' + "'" + p[0] + "'" + ')" alt="" /><a href="#" onclick="plclick(' + "'" + p[0] + "'" + ')">' + ((setup.showPortraitsInPlayerList)?'<img class="plisthead" src="' + setup.tileUrl + p[0] + '.png" />':'') + p[0] + '</a>';
						
						if (setup.showPortraitsOnMap) {
							image = setup.tileUrl + p[0] + '.png';
						}
					}
					
					if (p[1] == 'warp') numwarps++;
					if (p[1] == 'sign') numsigns++;
					if (p[1] == 'home') numhomes++;
					if (p[1] == 'spawn') numspawns++;
					if (p[1] == 'player') numplayers++;
					
					var hideMarker = (
						(p[1] == 'warp' && showWarps == false) ||
						(p[1] == 'sign' && showSigns == false) ||
						(p[1] == 'home' && showHomes == false) ||
						(p[1] == 'spawn' && showSpawn == false)
					);

					if(p[0] == followPlayer) {
						map.panTo(fromWorldToLatLng(p[2], p[3], p[4]));
					}
					
					if(p[0] in markers) {
						var m = markers[p[0]];
							
						if (hideMarker) {
							m.setMap(null);
							continue;
						}
						else if (m.map == null) {
							m.setMap(map);
						}
						
						var converted = fromWorldToLatLng(p[2], p[3], p[4]);
						m.setPosition(converted);
					} else {
						if (hideMarker) {
							continue;
						}
						
						var converted = fromWorldToLatLng(p[2], p[3], p[4]);
						var marker = new MarkerWithLabel({
							position: converted,
							map: map,
							labelContent: (p[1] == 'player' && setup.showPlayerNameOnMap == false)?'':p[0],
							labelAnchor: new google.maps.Point(-14, 10),
							labelClass: "labels",
							clickable: false,
							flat: true,
							icon: new google.maps.MarkerImage(image, new google.maps.Size(28, 28), new google.maps.Point(0, 0), new google.maps.Point(14, 14))
						});
						
						markers[p[0]] = marker;
					}
				} else if(p.length == 3) {
					if(p[2] == 't') {
						lastSeen['t_' + p[0]] = lasttimestamp;
						lastSeen['zt_' + p[1]] = lasttimestamp;

						if(!caveMode) {
							imgSubst('t_' + p[0]);
							imgSubst('zt_' + p[1]);
						}
					} else {
						lastSeen['ct_' + p[0]] = lasttimestamp;
						lastSeen['czt_' + p[1]] = lasttimestamp;

						if(caveMode) {
							imgSubst('ct_' + p[0]);
							imgSubst('czt_' + p[1]);
						}
					}
				}
			}
 
			if (playerlst != '') playerlst += '<br>';
			playerlst += '<img class="plicon" src="clock_' + (servertime > 12000 ? 'night' : 'day') + '.png"> <span id="servertime">...</span>';

			if(playerlst != oldplayerlst) {
				oldplayerlst = playerlst;
				lst.innerHTML = playerlst;
			}

			var timelbl = document.getElementById('servertime');
			var rem = 0;
			if (servertime > 12000) {
				rem = (24000 - servertime) / 20;
			} else {
				rem = (12000 - servertime) / 20;
			}
			var remMin = parseInt(rem / 60);
			var remSec = parseInt(rem) - remMin * 60;
			timelbl.innerHTML = remMin + (remSec < 10 ? ":0" : ":") + remSec;

			for(var m in markers) {
				if(!(m in loggedin)) {
					markers[m].setMap(null);
					delete markers[m];
				}
			}
			setTimeout(mapUpdate, config.updateRate);
			document.getElementById('warpsDiv').style.display = (numwarps == 0)?'none':'';
			document.getElementById('signsDiv').style.display = (numsigns == 0)?'none':'';
			document.getElementById('homesDiv').style.display = (numhomes == 0)?'none':'';
			document.getElementById('spawnsDiv').style.display = (numspawns == 0)?'none':'';
			document.getElementById('plist').style.display = (numplayers == 0)?'none':'';
			document.getElementById('controls').style.display = ((numwarps + numsigns + numhomes + numspawns) == 0)?'none':'';
		}, 'text', function() { alert('failed to get update data'); } );
	}

	window.onload = function initialize() {
		lst = document.getElementById('lst');
		plistbtn = document.getElementById('plistbtn');
		cavebtn = document.getElementById('cavebtn');

		var mapOptions = {
			zoom: 1,
			center: new google.maps.LatLng(0, 1),
			navigationControl: true,
			navigationControlOptions: {
				style: google.maps.NavigationControlStyle.DEFAULT
			},
			scaleControl: false,
			mapTypeControl: false,
			streetViewControl: false,
			mapTypeId: 'mcmap',
			backgroundColor: '#000'
		};
		map = new google.maps.Map(document.getElementById("mcmap"), mapOptions);
		mapType = new mcMapType();
		mapType.projection = new MCMapProjection();
		caveMapType = new mcMapType();
		caveMapType.projection = new MCMapProjection();

		map.zoom_changed = function() {
			mapType.tileSize = new google.maps.Size(config.zoomSize[map.zoom], config.zoomSize[map.zoom]);
			caveMapType.tileSize = mapType.tileSize;
		};

		google.maps.event.addListener(map, 'dragstart', function(mEvent) {
				plfollow('');
			});
		google.maps.event.addListener(map, 'zoom_changed', function() {
				makeLink();
			});
		google.maps.event.addListener(map, 'center_changed', function() {
				makeLink();
			});
		map.dragstart = plfollow('');

		map.mapTypes.set('mcmap', mapType);
		map.mapTypes.set('cavemap', caveMapType);

		map.setMapTypeId('mcmap');
		mapUpdate();
	}

	function plistopen() {
		if(lstopen) {
			lstopen = false;
			lst.style.display = 'none';
			lst.style.visibility = 'hidden';
			plistbtn.src = 'list_off.png';
		} else {
			lstopen = true;
			lst.style.display = '';
			lst.style.visibility = '';
			plistbtn.src = 'list_on.png';
		}
	}

	function plclick(name) {
		if(name in markers) {
			if(name != followPlayer) plfollow('');
			map.panTo(markers[name].getPosition());
		}
	}

	function plfollow(name) {
		var icon;

		if(followPlayer == name) {
			icon = document.getElementById('icon_' + followPlayer);
			if(icon) icon.src = 'follow_off.png';
			followPlayer = '';
			return;
		}

		if(followPlayer) {
			icon = document.getElementById('icon_' + followPlayer);
			if(icon) icon.src = 'follow_off.png';
			followPlayer = '';
		}

		if(!name) return;

		icon = document.getElementById('icon_' + name);
		if(icon) icon.src = 'follow_on.png';
		followPlayer = name;

		if(name in markers) {
			map.panTo(markers[name].getPosition());
		}
	}

	function makeLink() {
		var a=location.href.substring(0,location.href.lastIndexOf("/")+1)
		+ "?lat=" + map.getCenter().lat().toFixed(6)
		+ "&lng=" + map.getCenter().lng().toFixed(6)
		+ "&zoom=" + map.getZoom();
		document.getElementById("link").innerHTML = a;
	}

	//remove item (string or number) from an array
	function removeItem(originalArray, itemToRemove) {
		var j = 0;
		while (j < originalArray.length) {
			//	alert(originalArray[j]);
			if (originalArray[j] == itemToRemove) {
				originalArray.splice(j, 1);
			} else { j++; }
		}
		//	assert('hi');
		return originalArray;
	}
