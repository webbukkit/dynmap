<?php

# Database connection.
$conf['db_host'] = "localhost";
$conf['db_user'] = "mcserver";
$conf['db_pass'] = "yourpassword";
$conf['db_database'] = "minecraft";

//$conf['tileUrl'] = "/tiles/"; # Your tiles URL, if you run the site on same server where minecraft server is installed.
//$conf['tileUrl'] = "http://yourminecraftserver.tld/tiles/"; # if you run the website on a different/remote server.
$conf['tileUrl'] = "/tiles/";

//$conf['updateUrl'] = "http://localhost:8123/"; # Your update URL, if you run the site on same server where minecraft server is installed, then you DO NOT need to setup rewrite rulea in Apache/Lighttpd.
//$conf['updateUrl'] = "http://yourminecraftserver.tld/up/"; # if you run the website on a different/remote server then you NEED rewrite rule in apache/lighthtpd and use this configuration.
$conf['updateUrl'] = "/up/"; # Your update URL, if you run the site on same server where minecraft server is installed, and have configured Apache/Lighttpd proxy/rewrite rules.

$conf['updateRate'] = "2000"; # Seconds the map should poll for updates. (Seconds) * 1000. The default is 2000 (every 2 seconds).

# Default menu entries
$conf['site']		= "plugins/home";
$conf['menu_id']	= "1";
$conf['menu_name']	= "Home";

# Do not change anything below this line
$db = mysqli_init();
$db->real_connect($conf['db_host'],$conf['db_user'],$conf['db_pass'],$conf['db_database']);
if (mysqli_connect_errno()) {
	die ('Fatal database connection error: '.mysqli_connect_error().'('.mysqli_connect_errno().')');
}
$db->set_charset("utf8");

?>