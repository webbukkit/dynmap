<?php

ob_start();
require 'dynmap_access.php';
ob_end_clean();

if (!isset($tilespath)) {
    $tilespath = "../tiles/";
}

//Use this to force specific tiles path, versus using passed value
//$tilespath = 'my-tiles-path';

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

$path = $_REQUEST['tile'];
if ((!isset($path)) || strstr($path, "..")) {
    header('HTTP/1.0 500 Error');
    echo "<h1>500 Error</h1>";
    echo "Bad marker: " . $path;
    exit();
}

$fname = $tilespath . $path;

$parts = explode("/", $path);

$uid = '[' . strtolower($userid) . ']';

$world = $parts[0];

if (isset($worldaccess[$world])) {
    $ss = stristr($worldaccess[$world], $uid);
    if ($ss === false) {
        $fname = "../images/blank.png";
    }
}
if (count($parts) > 2) {
    $prefix = $parts[1];
    $plen = strlen($prefix);
    if (($plen > 4) && (substr($prefix, $plen - 4) === "_day")) {
        $prefix = substr($prefix, 0, $plen - 4);
    }
    $mapid = $world . "." . $prefix;
    if (isset($mapaccess[$mapid])) {
        $ss = stristr($mapaccess[$mapid], $uid);
        if ($ss === false) {
            $fname = "../images/blank.png";
        }
    }
}

if (!is_readable($fname)) {
    if (strstr($path, ".jpg") || strstr($path, ".png")) {
        $fname = "../images/blank.png";
    } else {
        echo "{ \"result\": \"bad-tile\" }";
        exit;
    }
}
$fp = fopen($fname, 'rb');
if (strstr($path, ".png")) {
    header("Content-Type: image/png");
} elseif (strstr($path, ".jpg")) {
    header("Content-Type: image/jpeg");
} else {
    header("Content-Type: application/text");
}

header("Content-Length: " . filesize($fname));

fpassthru($fp);
exit;
