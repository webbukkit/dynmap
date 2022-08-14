<?php

ob_start();
require 'dynmap_access.php';
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

$path = htmlspecialchars($_REQUEST['marker']);
if ((!isset($path)) || strstr($path, "..")) {
    header('HTTP/1.0 500 Error');
    echo "<h1>500 Error</h1>";
    echo "Bad marker: " . $path;
    exit();
}

$parts = explode("/", $path);

if (($parts[0] != "faces") && ($parts[0] != "_markers_")) {
    header('HTTP/1.0 500 Error');
    echo "<h1>500 Error</h1>";
    echo "Bad marker: " . $path;
    exit();
}

$db = new SQLite3($dbfile, SQLITE3_OPEN_READONLY);

if ($parts[0] == "faces") {
    if (count($parts) != 3) {
        header('HTTP/1.0 500 Error');
        echo "<h1>500 Error</h1>";
        echo "Bad face: " . $path;
        exit();
    }
    $ft = 0;
    if ($parts[1] == "8x8") {
        $ft = 0;
    } elseif ($parts[1] == '16x16') {
        $ft = 1;
    } elseif ($parts[1] == '32x32') {
        $ft = 2;
    } elseif ($parts[1] == 'body') {
        $ft = 3;
    }
    $pn = explode(".", $parts[2]);
    $stmt = $db->prepare('SELECT Image from Faces WHERE PlayerName=:pn AND TypeID=:ft');
    $stmt->bindValue(":pn", $pn[0], SQLITE3_TEXT);
    $stmt->bindValue(":ft", $ft, SQLITE3_INTEGER);
    $res = $stmt->execute();
    $row = $res->fetchArray();
    if (isset($row[0])) {
        header('Content-Type: image/png');
        echo $row[0];
    } else {
        header('Location: ../images/blank.png');
        exit;
    }
} else { // _markers_
    $in = explode(".", $parts[1]);
    $name = implode(".", array_slice($in, 0, count($in) - 1));
    $ext = $in[count($in) - 1];
    if (($ext == "json") && (strpos($name, "marker_") == 0)) {
        $world = substr($name, 7);
        $stmt = $db->prepare('SELECT Content from MarkerFiles WHERE FileName=:fn');
        $stmt->bindValue(':fn', $world, SQLITE3_TEXT);
        $res = $stmt->execute();
        $row = $res->fetchArray();
        header('Content-Type: application/json');
        if (isset($row[0])) {
            echo $row[0];
        } else {
            echo "{ }";
        }
    } else {
        $stmt = $db->prepare('SELECT Image from MarkerIcons WHERE IconName=:in');
        $stmt->bindValue(":in", $name, SQLITE3_TEXT);
        $res = $stmt->execute();
        $row = $res->fetchArray();
        if (isset($row[0])) {
            header('Content-Type: image/png');
            echo $row[0];
        } else {
            header('Location: ../images/blank.png');
            exit;
        }
    }
}

$res->finalize();
$stmt->close();
$db->close();


exit;
