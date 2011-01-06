<?php
session_start();
include('inc/config.php');
?>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" lang="en">
  <head>
    <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
    <link rel="stylesheet" href="css/global.css" type="text/css"  media="screen, projection" />
    <link rel="stylesheet" href="css/screen.css" type="text/css"  media="screen, projection" />
    <link rel="stylesheet" href="css/menu.css" type="text/css"  media="screen, projection" />
    <link rel="shortcut icon" href="images/favicon.png" type="image/png" />

    <!-- DynamicMap -->
    <link rel="stylesheet" type="text/css" href="plugins/map/map.css" media="screen" /> 
    <script type="text/javascript" src="http://maps.google.com/maps/api/js?sensor=false"></script> 
    <script>
    var setup = {
        tileUrl: '<?=$conf['tileUrl'] ?>',
        updateUrl: '<?=$conf['updateUrl'] ?>',
        updateRate: '<?=$conf['updateRate'] ?>'
    }
    </script>
    <script type="text/javascript" src="plugins/map/map.js" ></script>

    <!-- MCStats -->
    <link rel="stylesheet" type="text/css" href="plugins/stats/mcstats.css" media="screen" />
    <script src='http://ajax.googleapis.com/ajax/libs/jquery/1.4.2/jquery.min.js'></script>
    <script src='http://tablesorter.com/jquery.tablesorter.min.js'></script>

    <title>Flames' Minecraft Site</title>
  </head>

  <body onload="initialize()">

    <div id="seite">

		<div id="logo"><img src="images/logo.png" border="0"></div>

   		<div id="menu"><?php include('inc/menu.php'); ?></div>

   		<div id="content">

<?php
echo '<?xml version="1.0" encoding="UTF-8"?>';

	if (file_exists($site.".html"))
	{
		include($site.".html");
	}
	else if (file_exists($site.".php"))
	{
		include($site.".php");
	}
	else
	{
		echo "<div>Error 404<br/>Page \"".$menu_name."/".$sub_menu_name."\" not found.</div>";
	}
?>

    	</div>

    <div id="footer">&copy; 2011 <a target="_new" href="http://yoursite.com">yoursite.com</a></div>

    </div>

  </body>
</html>