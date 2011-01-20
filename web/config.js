var config = {
        tileUrl:     'tiles/',
        updateUrl:   'up/',                             // For Apache and lighttpd
//      updateUrl:   'up.aspx?path=',                   // For IIS
        updateRate:  2000,                              // Seconds the map should poll for updates. (Seconds) * 1000. The default is 2000 (every 2 seconds).
        showPortraitsOnMap: true,
        showPortraitsInPlayerList: true,
        showPlayerNameOnMap: false,
        defaultMap: 'defaultmap',
        maps: {
			'defaultmap': new DefaultMapType(),
			'cavemap': new CaveMapType()
		},
		tileWidth: 128,
		tileHeight: 128
};