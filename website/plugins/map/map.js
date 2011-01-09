/* THERE SHOULD BE NO NEED FOR MANUAL CONFIGURATION BEYOND THIS POINT */

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
    }

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
    }), google.maps.event.addListener(me.marker_.getMap(), "mousemove", function (mEvent) {
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
    }), google.maps.event.addDomListener(this.eventDiv_, "mouseover", function (e) {
        //me.eventDiv_.style.cursor = "pointer";
        google.maps.event.trigger(me.marker_, "mouseover", e);
    }), google.maps.event.addDomListener(this.eventDiv_, "mouseout", function (e) {
        //me.eventDiv_.style.cursor = me.marker_.getCursor();
        google.maps.event.trigger(me.marker_, "mouseout", e);
    }), google.maps.event.addDomListener(this.eventDiv_, "click", function (e) {
        if (cIgnoreClick) { // Ignore the click reported when a label drag ends
            cIgnoreClick = false;
        } else {
            cAbortEvent(e); // Prevent click from being passed on to map
            google.maps.event.trigger(me.marker_, "click", e);
        }
    }), google.maps.event.addDomListener(this.eventDiv_, "dblclick", function (e) {
        cAbortEvent(e); // Prevent map zoom when double-clicking on a label
        google.maps.event.trigger(me.marker_, "dblclick", e);
    }), google.maps.event.addDomListener(this.eventDiv_, "mousedown", function (e) {
        cMouseIsDown = true;
        cDraggingInProgress = false;
        cLatOffset = 0;
        cLngOffset = 0;
        cAbortEvent(e); // Prevent map pan when starting a drag on a label
        google.maps.event.trigger(me.marker_, "mousedown", e);
    }), google.maps.event.addListener(this.marker_, "dragstart", function (mEvent) {
        cDraggingInProgress = true;
        cSavedZIndex = me.marker_.getZIndex();
    }), google.maps.event.addListener(this.marker_, "drag", function (mEvent) {
        me.marker_.setPosition(mEvent.latLng);
        me.marker_.setZIndex(1000000); // Moves the marker to the foreground during a drag
    }), google.maps.event.addListener(this.marker_, "dragend", function (mEvent) {
        cDraggingInProgress = false;
        me.marker_.setZIndex(cSavedZIndex);
    }), google.maps.event.addListener(this.marker_, "position_changed", function () {
        me.setPosition();
    }), google.maps.event.addListener(this.marker_, "zindex_changed", function () {
        me.setZIndex();
    }), google.maps.event.addListener(this.marker_, "visible_changed", function () {
        me.setVisible();
    }), google.maps.event.addListener(this.marker_, "labelvisible_changed", function () {
        me.setVisible();
    }), google.maps.event.addListener(this.marker_, "title_changed", function () {
        me.setTitle();
    }), google.maps.event.addListener(this.marker_, "labelcontent_changed", function () {
        me.setContent();
    }), google.maps.event.addListener(this.marker_, "labelanchor_changed", function () {
        me.setAnchor();
    }), google.maps.event.addListener(this.marker_, "labelclass_changed", function () {
        me.setStyles();
    }), google.maps.event.addListener(this.marker_, "labelstyle_changed", function () {
        me.setStyles();
    })];
}

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
}

/**
 * Draws the label on the map.
 * @private
 */
MarkerLabel_.prototype.draw = function () {
    this.setContent();
    this.setTitle();
    this.setStyles();
}

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
}

/**
 * Sets the content of the tool tip for the label. It is
 * always set to be the same as for the marker itself.
 * @private
 */
MarkerLabel_.prototype.setTitle = function () {
    this.eventDiv_.title = this.marker_.getTitle() || "";
}

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
}

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
}

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
}

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
}

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
}

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
}

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
    opt_options = opt_options || {}
    opt_options.labelContent = opt_options.labelContent || "";
    opt_options.labelAnchor = opt_options.labelAnchor || new google.maps.Point(0, 0);
    opt_options.labelClass = opt_options.labelClass || "markerLabels";
    opt_options.labelStyle = opt_options.labelStyle || {}
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
}



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

function makeRequest(url, func, type, fail, post, contenttype) {
    var http_request = false;

    type = typeof(type) != 'undefined' ? type : 'text';
    fail = typeof(fail) != 'undefined' ? fail : function () {}

    if (window.XMLHttpRequest) {
        http_request = new XMLHttpRequest();
    } else if (window.ActiveXObject) {
        http_request = new ActiveXObject("Microsoft.XMLHTTP");
    }

    if (type == 'text') {
        http_request.onreadystatechange = function () {
            if (http_request.readyState == 4) {
                if (http_request.status == 200) {
                    func(http_request.responseText);
                } else {
                    fail(http_request);
                }
            }
        }
    } else {
        http_request.onreadystatechange = function () {
            if (http_request.readyState == 4) {
                if (http_request.status == 200) {
                    func(http_request.responseXML);
                } else {
                    fail(http_request);
                }
            }
        }
    }

    if (typeof(post) != 'undefined') {
        http_request.open('POST', url, true);
        if (typeof(contenttype) != 'undefined') http_request.setRequestHeader("Content-Type", contenttype);
        http_request.send(post);
    } else {
        http_request.open('GET', url, true);
        http_request.send(null);
    }
}

var config = {
    tileUrl: setup.tileUrl,
    updateUrl: 'plugins/map/request.php?updateurl=' + setup.updateUrl + '&lasttimestamp=', // use this for Ajax
    //updateUrl: setup.updateUrl, // use this if you configured Apache/Lighttpd proxy/rewrite rule
    tileWidth: 128,
    tileHeight: 128,
    updateRate: setup.updateRate,
    zoomSize: [128, 128, 256, 512]
}

function MCMapProjection() {}

MCMapProjection.prototype.fromLatLngToPoint = function (latLng) {
    var x = (latLng.lng() * config.tileWidth) | 0;
    var y = (latLng.lat() * config.tileHeight) | 0;
    if (map.zoom == 0) {
        x += config.tileWidth / 2;
    }
    return new google.maps.Point(x, y);
}

MCMapProjection.prototype.fromPointToLatLng = function (point) {
    var x = point.x;
    if (map.zoom == 0) x -= config.tileWidth / 2;
    var lng = x / config.tileWidth;
    var lat = point.y / config.tileHeight;
    return new google.maps.LatLng(lat, lng);
}

function fromWorldToLatLng(x, y, z) {
    var dx = +x;
    var dy = +y - 127;
    var dz = +z;
    var px = dx + dz;
    var py = dx - dz - dy;

    var lng = -px / config.tileWidth / 2 + 0.5;
    var lat = py / config.tileHeight / 2;

    return new google.maps.LatLng(lat, lng);
}

function mcMapType() {}

var tileDict = new Array();
var lastSeen = new Array();

function tileUrl(tile, always) {
    if (always) {
        var now = new Date();
        return config.tileUrl + tile + '.png?' + now.getTime();
    } else if (tile in lastSeen) {
        return config.tileUrl + tile + '.png?' + lastSeen[tile];
    } else {
        return config.tileUrl + tile + '.png?0';
    }
}

function imgSubst(tile) {
    if (!(tile in tileDict)) return;

    var src = tileUrl(tile);
    var t = tileDict[tile];
    t.src = src;
    t.style.display = '';
    t.onerror = function () {
        setTimeout(function () {
            t.src = tileUrl(tile, 1);
        }, 1000);
        t.onerror = '';
    }
}

var caveMode = false;

function caveSwitch() {
    caveMode = !caveMode;

    if (caveMode) {
        document.getElementById('cavebtn').src = 'plugins/map/cave_on.png';
        map.setMapTypeId('cavemap');
    } else {
        document.getElementById('cavebtn').src = 'plugins/map/cave_off.png';
        map.setMapTypeId('mcmap');
    }
}

mcMapType.prototype.tileSize = new google.maps.Size(config.tileWidth, config.tileHeight);
mcMapType.prototype.minZoom = 0;
mcMapType.prototype.maxZoom = 3;
mcMapType.prototype.getTile = function (coord, zoom, doc) {
    var img = doc.createElement('IMG');

    img.onerror = function () {
        img.style.display = 'none';
        //img.parentNode.parentNode.parentNode.parentNode.style.background = '#000' // macht den karten hintergrund schwarz, passend zu den tiles der kleinsten zoomstufe.
    }

    img.style.width = config.zoomSize[zoom] + 'px';
    img.style.height = config.zoomSize[zoom] + 'px';
    img.style.borderStyle = 'none';

    //var tilename = (-coord.x * config.tileWidth) + '_' + coord.y * config.tileHeight;
    var pfx = caveMode ? "c" : "";

    if (zoom > 0) {
        var tilename = pfx + "t_" + (-coord.x * config.tileWidth) + '_' + coord.y * config.tileHeight;
    } else {
        var tilename = pfx + "zt_" + (-coord.x * config.tileWidth * 2) + '_' + coord.y * config.tileHeight * 2;
    }
    tileDict[tilename] = img;

    var url = tileUrl(tilename);
    img.src = url;
    return img;
}

var markers = new Array();
var lasttimestamp = 0;
var followPlayer = '';
var cavebtn;

var showPlayers = true;
var showWarps = false;
var showSigns = false;
var showHomes = false;
var showSpawn = false;

function mapUpdate() {
    makeRequest(config.updateUrl + lasttimestamp, function (res) {
        var rows = res.split('\n');
        var loggedin = new Array();

        lasttimestamp = rows[0];
        delete rows[0];

        var playerlist = '';
        var warplist = '';
        var homelist = '';
        var signlist = '';
        var spawnlist = '';
        var numplayers = 0;
        var numwarps = 0;
        var numhomes = 0;
        var numsigns = 0;
        var numspawns = 0;

        for (var line in rows) {
            var p = rows[line].split(' ');

            if (p[0] == '') continue;

            if (p.length == 5) {
                var plname = p[0];
                p[0] = p[0] + '<span>' + p[1] + '</span>';
                var image = 'plugins/map/' + p[1] + '.png';
                loggedin[p[0]] = 1;

                if (p[1] == 'player') {
                    if (playerlist != '') playerlist += '<br />';
                    //playerlist += '<img id="icon_' + p[0] + '" class="plicon" src="plugins/map/' + (p[0] == followPlayer ? 'follow_on.png' : 'follow_off.png') + '" onclick="plfollow(' + "'" + p[0] + "'" + ')" alt="" /> <a href="#" onclick="plclick(' + "'" + p[0] + "'" + ')">' + p[0] + '</a>';
                    playerlist += '<img id="icon_' + p[0] + '" src="' + setup.tileUrl + plname + '.png" class="' + (p[0] == followPlayer ? 'pliconfollow' : 'plicon') + '" onclick="plfollow(' + "'" + p[0] + "'" + ')" alt="" /> <a href="#" onclick="plclick(' + "'" + p[0] + "'" + ')">' + p[0] + '</a>';
                }
                if (p[1] == 'warp') {
                    if (warplist != '') warplist += '<br />';
                    warplist += '<a href="#" onclick="plclick(' + "'" + p[0] + "'" + ')">' + p[0] + '</a>';
                }
                if (p[1] == 'home') {
                    if (homelist != '') homelist += '<br />';
                    homelist += '<a href="#" onclick="plclick(' + "'" + p[0] + "'" + ')">' + p[0] + '</a>';
                }
                if (p[1] == 'sign') {
                    if (signlist != '') signlist += '<br />';
                    signlist += '<a href="#" onclick="plclick(' + "'" + p[0] + "'" + ')">' + p[0] + '</a>';
                }
                if (p[1] == 'spawn') {
                    if (spawnlist != '') spawnlist += '<br />';
                    spawnlist += '<a href="#" onclick="plclick(' + "'" + p[0] + "'" + ')">' + p[0] + '</a>';
                }

                if (p[1] == 'warp') numwarps++;
                if (p[1] == 'sign') numsigns++;
                if (p[1] == 'home') numhomes++;
                if (p[1] == 'spawn') numspawns++;
                if (p[1] == 'player') numplayers++;

                var hideMarker = ((p[1] == 'warp' && showWarps == false) || (p[1] == 'sign' && showSigns == false) || (p[1] == 'home' && showHomes == false) || (p[1] == 'spawn' && showSpawn == false) || (p[1] == 'player' && showPlayers == false));

                if (p[0] == followPlayer) {
                    map.panTo(fromWorldToLatLng(p[2], p[3], p[4]));
                    //change image on player marker to player_on.png
                }

                if (p[0] in markers) {
                    var m = markers[p[0]];

                    if (hideMarker) {
                        m.setMap(null);
                        continue;
                    } else if (m.map == null) {
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
                        labelContent: p[0],
                        labelAnchor: new google.maps.Point(-14, 10),
                        labelClass: "labels",
                        clickable: false,
                        flat: true,
                        icon: new google.maps.MarkerImage(image, new google.maps.Size(25, 25), new google.maps.Point(0, 0), new google.maps.Point(14, 14))
                    });

                    markers[p[0]] = marker;
                }
            } else if (p.length == 3) {
                if (p[2] == 't') {
                    lastSeen['t_' + p[0]] = lasttimestamp;
                    lastSeen['zt_' + p[1]] = lasttimestamp;

                    if (!caveMode) {
                        imgSubst('t_' + p[0]);
                        imgSubst('zt_' + p[1]);
                    }
                } else {
                    lastSeen['ct_' + p[0]] = lasttimestamp;
                    lastSeen['czt_' + p[1]] = lasttimestamp;

                    if (caveMode) {
                        imgSubst('ct_' + p[0]);
                        imgSubst('czt_' + p[1]);
                    }
                }
            }
        }

        if (playerlist == '') { playerlist = 'No One'; }
        document.getElementById('plist').innerHTML = playerlist;
        document.getElementById('wlist').innerHTML = warplist;
        document.getElementById('hlist').innerHTML = homelist;
        document.getElementById('slist').innerHTML = signlist;
        document.getElementById('splist').innerHTML = spawnlist;

        for (var m in markers) {
            if (!(m in loggedin)) {
                markers[m].setMap(null);
                delete markers[m];
            }
        }

        setTimeout(mapUpdate, config.updateRate);
    }, 'text', function () {
        //alert('failed to get update data');
    });
}

function openPlayers() {
    if (showPlayers) {
        showPlayers = false;
        document.getElementById('showPlayers').src = 'plugins/map/player.png';
        document.getElementById('plist').style.display = 'none';
        document.getElementById('right').style.display = 'none';
    } else {
        showPlayers = true;
        showWarps = false;
        showHomes = false;
        showSigns = false;
        showSpawn = false;
        document.getElementById('showPlayers').src = 'plugins/map/player_on.png';
        document.getElementById('plist').style.display = '';
        document.getElementById('right').style.display = '';
        document.getElementById('right').style.top = '54px';
        document.getElementById('wlist').style.display = 'none';
        document.getElementById('hlist').style.display = 'none';
        document.getElementById('slist').style.display = 'none';
        document.getElementById('splist').style.display = 'none';
        document.getElementById('showWarps').src = 'plugins/map/warp.png';
        document.getElementById('showHomes').src = 'plugins/map/home.png';
        document.getElementById('showSigns').src = 'plugins/map/sign.png';
        document.getElementById('showSpawn').src = 'plugins/map/spawn.png';
    }
}
function openWarps() {
    if (showWarps) {
        showWarps = false;
        document.getElementById('showWarps').src = 'plugins/map/warp.png';
        document.getElementById('wlist').style.display = 'none';
        document.getElementById('right').style.display = 'none';
    } else {
        showPlayers = false;
        showWarps = true;
        showHomes = false;
        showSigns = false;
        showSpawn = false;
        document.getElementById('showWarps').src = 'plugins/map/warp_on.png';
        document.getElementById('wlist').style.display = '';
        document.getElementById('right').style.display = '';
        document.getElementById('right').style.top = '134px';
        document.getElementById('plist').style.display = 'none';
        document.getElementById('hlist').style.display = 'none';
        document.getElementById('slist').style.display = 'none';
        document.getElementById('splist').style.display = 'none';
        document.getElementById('showPlayers').src = 'plugins/map/player.png';
        document.getElementById('showHomes').src = 'plugins/map/home.png';
        document.getElementById('showSigns').src = 'plugins/map/sign.png';
        document.getElementById('showSpawn').src = 'plugins/map/spawn.png';
    }
}
function openHomes() {
    if (showHomes) {
        showHomes = false;
        document.getElementById('showHomes').src = 'plugins/map/home.png';
        document.getElementById('hlist').style.display = 'none';
        document.getElementById('right').style.display = 'none';
    } else {
        showPlayers = false;
        showWarps = false;
        showHomes = true;
        showSigns = false;
        showSpawn = false;
        document.getElementById('showHomes').src = 'plugins/map/home_on.png';
        document.getElementById('hlist').style.display = '';
        document.getElementById('right').style.display = '';
        document.getElementById('right').style.top = '109px';
        document.getElementById('plist').style.display = 'none';
        document.getElementById('wlist').style.display = 'none';
        document.getElementById('slist').style.display = 'none';
        document.getElementById('splist').style.display = 'none';
        document.getElementById('showPlayers').src = 'plugins/map/player.png';
        document.getElementById('showWarps').src = 'plugins/map/warp.png';
        document.getElementById('showSigns').src = 'plugins/map/sign.png';
        document.getElementById('showSpawn').src = 'plugins/map/spawn.png';
    }
}
function openSigns() {
    if (showSigns) {
        showSigns = false;
        document.getElementById('showSigns').src = 'plugins/map/sign.png';
        document.getElementById('slist').style.display = 'none';
        document.getElementById('right').style.display = 'none';
    } else {
        showPlayers = false;
        showWarps = false;
        showHomes = false;
        showSigns = true;
        showSpawn = false;
        document.getElementById('showSigns').src = 'plugins/map/sign_on.png';
        document.getElementById('slist').style.display = '';
        document.getElementById('right').style.display = '';
        document.getElementById('right').style.top = '159px';
        document.getElementById('plist').style.display = 'none';
        document.getElementById('wlist').style.display = 'none';
        document.getElementById('hlist').style.display = 'none';
        document.getElementById('splist').style.display = 'none';
        document.getElementById('showPlayers').src = 'plugins/map/player.png';
        document.getElementById('showWarps').src = 'plugins/map/warp.png';
        document.getElementById('showHomes').src = 'plugins/map/home.png';
        document.getElementById('showSpawn').src = 'plugins/map/spawn.png';
    }
}
function openSpawn() {
    if (showSpawn) {
        showSpawn = false;
        document.getElementById('showSpawn').src = 'plugins/map/spawn.png';
        document.getElementById('splist').style.display = 'none';
        document.getElementById('right').style.display = 'none';
    } else {
        showPlayers = false;
        showWarps = false;
        showHomes = false;
        showSigns = false;
        showSpawn = true;
        document.getElementById('showSpawn').src = 'plugins/map/spawn_on.png';
        document.getElementById('splist').style.display = '';
        document.getElementById('right').style.display = '';
        document.getElementById('right').style.top = '84px';
        document.getElementById('plist').style.display = 'none';
        document.getElementById('wlist').style.display = 'none';
        document.getElementById('hlist').style.display = 'none';
        document.getElementById('slist').style.display = 'none';
        document.getElementById('showPlayers').src = 'plugins/map/player.png';
        document.getElementById('showWarps').src = 'plugins/map/warp.png';
        document.getElementById('showHomes').src = 'plugins/map/home.png';
        document.getElementById('showSigns').src = 'plugins/map/sign.png';
    }
}

function plclick(name) {
    if (name in markers) {
        if (name != followPlayer) plfollow('');
        map.panTo(markers[name].getPosition());
    }
}

function plfollow(name) {
    var icon;

    if (followPlayer == name) {
        icon = document.getElementById('icon_' + followPlayer);
        //if (icon) icon.src = 'plugins/map/follow_off.png';
        if (icon) icon.className = 'plicon';
        followPlayer = '';
        return;
    }

    if (followPlayer) {
        icon = document.getElementById('icon_' + followPlayer);
        //if (icon) icon.src = 'plugins/map/follow_off.png';
        if (icon) icon.className = 'plicon';
        followPlayer = '';
    }

    if (!name) return;

    icon = document.getElementById('icon_' + name);
    //if (icon) icon.src = 'plugins/map/follow_on.png';
    if (icon) icon.className = 'pliconfollow';
    followPlayer = name;

    if (name in markers) {
        map.panTo(markers[name].getPosition());
    }
}

function makeLink() {
    var a = location.href.substring(0, location.href.lastIndexOf("/") + 1) + "index&lat=" + map.getCenter().lat().toFixed(6) + "&lng=" + map.getCenter().lng().toFixed(6) + "&zoom=" + map.getZoom() + "&cave=" + caveMode;
    document.getElementById("link").innerHTML = a;
}

function removeItem(originalArray, itemToRemove) {
    var j = 0;
    while (j < originalArray.length) {
        if (originalArray[j] == itemToRemove) {
            originalArray.splice(j, 1);
        } else {
            j++;
        }
    }
    return originalArray;
}

function fs() {
    var fullscreenbtn = document.getElementById("fullscreenbtn");
    var seitediv = document.getElementById("seite");
    var contentdiv = document.getElementById("content");
    var mcmapdiv = document.getElementById("mcmap");
    var footerdiv = document.getElementById("footer");
    var rightdiv = document.getElementById("right");
    var ctrldiv = document.getElementById("ctrl");
    var linkdiv = document.getElementById("link");

    if (fullscreenbtn.src.indexOf("off") != -1) {
        fullscreenbtn.src = "plugins/map/fullscreen_on.png";

        seitediv.style.top = "0px";
        seitediv.style.left = "0px";
        seitediv.style.width = "100%";
        seitediv.style.height = "100%";
        seitediv.style.marginLeft = "0px";
        seitediv.style.marginTop = "0px";
        contentdiv.style.width = "100%";
        contentdiv.style.height = "100%";
        contentdiv.style.top = "0px";
        contentdiv.style.left = "0px";
        mcmapdiv.style.width = "100%";
        mcmapdiv.style.height = "100%";
        mcmapdiv.style.top = "0px";
        mcmapdiv.style.zIndex = "998";
        footerdiv.style.display = "none";
        ctrldiv.style.top = "0px";
        linkdiv.style.zIndex = "998";
    }
    else {
        fullscreenbtn.src = "plugins/map/fullscreen_off.png";

        seitediv.style.top = "50%";
        seitediv.style.left = "50%";
        seitediv.style.width = "1066px";
        seitediv.style.height = "600px";
        seitediv.style.marginLeft = "-533px";
        seitediv.style.marginTop = "-300px";
        contentdiv.style.width = "1064px";
        contentdiv.style.height = "494px";
        contentdiv.style.top = "105px";
        contentdiv.style.left = "1px";
        mcmapdiv.style.width = "1064px";
        mcmapdiv.style.height = "458px";
        mcmapdiv.style.top = "36px";
        mcmapdiv.style.zIndex = "998";
        footerdiv.style.display = "";
        ctrldiv.style.top = "36px";
        linkdiv.style.zIndex = "998";
    }
    google.maps.event.trigger(map, "resize");
}

function initialize() {
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
    }
    map = new google.maps.Map(document.getElementById("mcmap"), mapOptions);
    mapType = new mcMapType();
    mapType.projection = new MCMapProjection();
    caveMapType = new mcMapType();
    caveMapType.projection = new MCMapProjection();

    map.zoom_changed = function () {
        mapType.tileSize = new google.maps.Size(config.zoomSize[map.zoom], config.zoomSize[map.zoom]);
        caveMapType.tileSize = mapType.tileSize;
    }

    google.maps.event.addListener(map, 'dragstart', function (mEvent) {
        plfollow('');
    });
    google.maps.event.addListener(map, 'zoom_changed', function () {
        makeLink();
    });
    google.maps.event.addListener(map, 'center_changed', function () {
        makeLink();
    });

    map.dragstart = plfollow('');
    map.mapTypes.set('mcmap', mapType);
    map.mapTypes.set('cavemap', caveMapType);
    map.setMapTypeId('mcmap');
    mapUpdate();
    makeLink();
}