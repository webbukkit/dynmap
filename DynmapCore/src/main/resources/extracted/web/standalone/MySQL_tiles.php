<?php

ob_start();
require_once 'MySQL_funcs.php';
require 'MySQL_config.php';
require 'MySQL_access.php';
ob_end_clean();

session_start();

if (isset($_SESSION['userid'])) {
    $userid = $_SESSION['userid'];
} else {
    $userid = '-guest-';
}

$loggedin = false;
if (strcmp($userid, '-guest-')) {
    $loggedin = true;
}

$path = htmlspecialchars($_REQUEST['tile']);
if ((!isset($path)) || strstr($path, "..")) {
    header('HTTP/1.0 500 Error');
    echo "<h1>500 Error</h1>";
    echo "Bad marker: " . $path;
    exit();
}

$parts = explode("/", $path);

if (count($parts) != 4) {
    header('Location: ../images/blank.png');
    cleanupDb();
    exit;
}

$uid = '[' . strtolower($userid) . ']';

$world = $parts[0];

if (isset($worldaccess[$world])) {
    $ss = stristr($worldaccess[$world], $uid);
    if ($ss === false) {
        header('Location: ../images/blank.png');
        cleanupDb();
        exit;
    }
}
$variant = 'STANDARD';

$prefix = $parts[1];
$plen = strlen($prefix);
if (($plen > 4) && (substr($prefix, $plen - 4) === "_day")) {
    $prefix = substr($prefix, 0, $plen - 4);
    $variant = 'DAY';
}
$mapid = $world . "." . $prefix;
if (isset($mapaccess[$mapid])) {
    $ss = stristr($mapaccess[$mapid], $uid);
    if ($ss === false) {
        header('Location: ../images/blank.png');
        cleanupDb();
        exit;
    }
}

$fparts = explode("_", $parts[3]);
if (count($fparts) == 3) { // zoom_x_y
    $zoom = strlen($fparts[0]);
    $x = intval($fparts[1]);
    $y = intval($fparts[2]);
} elseif (count($fparts) == 2) { // x_y
    $zoom = 0;
    $x = intval($fparts[0]);
    $y = intval($fparts[1]);
} else {
    header('Location: ../images/blank.png');
    cleanupDb();
    exit;
}
initDbIfNeeded();

$stmt = $db->prepare('SELECT t.NewImage,t.Image,t.Format,t.HashCode,t.LastUpdate FROM ' . $dbprefix . 'Maps m JOIN ' . $dbprefix . 'Tiles t ON m.ID=t.MapID WHERE m.WorldID=? AND m.MapID=? AND m.Variant=? AND t.x=? AND t.y=? and t.zoom=?');
$stmt->bind_param('sssiii', $world, $prefix, $variant, $x, $y, $zoom);
$res = $stmt->execute();
$stmt->bind_result($tnewimage, $timage, $format, $thash, $tlast);
if ($stmt->fetch()) {
    if ($format == 0) {
        header('Content-Type: image/png');
    } else if ($format == 2) {
    	header('Content-Type: image/webp');
    } else {
        header('Content-Type: image/jpeg');
    }
    header('ETag: \'' . $thash . '\'');
    header('Last-Modified: ' . gmdate('D, d M Y H:i:s', (int) ($tlast / 1000)) . ' GMT');
    if (is_null($tnewimage)) {
        echo $timage;
    } else {
        echo $tnewimage;
    }
} else {
    header('Location: ../images/blank.png');
}

$stmt->close();
cleanupDb();

exit;
