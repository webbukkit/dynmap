Commands
--------------------------------------------------
/map_wait [wait] - set wait between tile renders (ms)
/map_stat - query number of tiles in render queue
/map_regen - regenerate entire map (currently buggy)
/map_debug - send map debugging messages
/map_nodebug - disable map debugging messages
/map_regenzoom - regenerates zoom-out tiles

/addmarker [name] - adds a named marker to the map
/removemarker [name] - removes a named marker to the map
/listmarkers - list all named markers
/tpmarker [name] - teleport to a named marker

server.properties
--------------------------------------------------
map-colorsetpath - point to colors.txt
map-tilepath - point to web/tiles folder
map-markerpath - point to markers.csv file (do not need to create the file, one will be created when you create a marker)
map-serverport - the port the web server runs on (default is 8123)