<?php
ob_start();
include('dynmap_access.php');
ob_end_clean();

session_start();

if(isset($_SESSION['userid'])) {
  $userid = $_SESSION['userid'];
}
else {
  $userid = '-guest-';
}

$loggedin = false;
if(strcmp($userid, '-guest-')) {
  $loggedin = true;
}

$path = $_REQUEST['tile'];
if ((!isset($path)) || strstr($path, "..")) {
    header('HTTP/1.0 500 Error');
    echo "<h1>500 Error</h1>";
    echo "Bad marker: " . $path;
    exit();
}

$parts = explode("/", $path);

if (count($parts) != 4) {
   header('Location: ../images/blank.png');
   exit;
}
 
$uid = '[' . strtolower($userid) . ']';

$world = $parts[0];

if(isset($worldaccess[$world])) {
    $ss = stristr($worldaccess[$world], $uid);
	if($ss === false) {
           header('Location: ../images/blank.png');
           exit;
	}
}
$variant='STANDARD';

  $prefix = $parts[1];
  $plen = strlen($prefix);
  if(($plen > 4) && (substr($prefix, $plen - 4) === "_day")) {
	$prefix = substr($prefix, 0, $plen - 4);
        $variant = 'DAY';
  }
  $mapid = $world . "." . $prefix;
  if(isset($mapaccess[$mapid])) {
    $ss = stristr($mapaccess[$mapid], $uid);
	if($ss === false) {
           header('Location: ../images/blank.png');
           exit;
	}
  }

$fparts = explode("_", $parts[3]);
if (count($fparts) == 3) { // zoom_x_y
   $zoom = strlen($fparts[0]);
   $x = intval($fparts[1]);
   $y = intval($fparts[2]);
}
else if (count($fparts) == 2) { // x_y
   $zoom = 0;
   $x = intval($fparts[0]);
   $y = intval($fparts[1]);
}
else {
   header('Location: ../images/blank.png');
   exit;
}

$db = new SQLite3($dbfile, SQLITE3_OPEN_READONLY);

$stmt = $db->prepare('SELECT Tiles.Image,Tiles.Format,Tiles.HashCode,Tiles.LastUpdate,Tiles.ImageLen FROM Maps JOIN Tiles WHERE Maps.WorldID=:wid AND Maps.MapID=:mapid AND Maps.Variant=:var AND Maps.ID=Tiles.MapID AND Tiles.x=:x AND Tiles.y=:y and Tiles.zoom=:zoom');
$stmt->bindValue(':wid', $world, SQLITE3_TEXT);
$stmt->bindValue(':mapid', $prefix, SQLITE3_TEXT);
$stmt->bindValue(':var', $variant, SQLITE3_TEXT);
$stmt->bindValue(':x', $x, SQLITE3_INTEGER);
$stmt->bindValue(':y', $y, SQLITE3_INTEGER);
$stmt->bindValue(':zoom', $zoom, SQLITE3_INTEGER);
$res = $stmt->execute();
$row = $res->fetchArray();
if (isset($row[1])) {
   $format = $row[1];
   if ($format == 0) {
      header('Content-Type: image/png');
   }
   else {
      header('Content-Type: image/jpeg');
   }
   header('ETag: \'' . $row[2] . '\'');
   header('Last-Modified: ' . gmdate('D, d M Y H:i:s', $row[3]/1000) . ' GMT'); 
   if ($row[4] > 0) {
      $v = substr($row[0], 0, $row[4]);
   } else {
      $v = rtrim($row[0], "\0");
   }
   header('Content-Length: ' . strlen($v));
   echo $v;
}
else {
   header('Location: ../images/blank.png');
}

$res->finalize();
$stmt->close();
$db->close();

exit;
?>
