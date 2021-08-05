<?php

ob_start();
require_once 'PostgreSQL_funcs.php';
require 'PostgreSQL_config.php';
require 'PostgreSQL_access.php';
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

$path = $_REQUEST['marker'];
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

initDbIfNeeded();

if ($parts[0] == "faces") {
    if (count($parts) != 3) {
        header('HTTP/1.0 500 Error');
        echo "<h1>500 Error</h1>";
        echo "Bad face: " . $path;
        cleanupDb();
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
    $stmt = $db->prepare('SELECT Image from ' . $dbprefix . 'Faces WHERE PlayerName=? AND TypeID=?');
    $res = $stmt->execute(array($pn[0], $ft));
    $timage = $stmt->fetch();
    if ($res && $timage) {
        header('Content-Type: image/png');
        echo stream_get_contents($timage[0]);
    } else {
        header('Location: ../images/blank.png');
    }
} else { // _markers_
    $in = explode(".", $parts[1]);
    $name = implode(".", array_slice($in, 0, count($in) - 1));
    $ext = $in[count($in) - 1];
    if (($ext == "json") && (strpos($name, "marker_") == 0)) {
        $world = substr($name, 7);
        $stmt = $db->prepare('SELECT Content from ' . $dbprefix . 'MarkerFiles WHERE FileName=?');
        $res = $stmt->execute(array($world));
        $timage = $stmt->fetch();
        header('Content-Type: application/json');
        if ($res && $timage) {
            echo stream_get_contents($timage[0]); //PDO returns arrays, even for single colums, and bytea is returned as stream.
        } else {
            echo "{ }";
        }
    } else {
        $stmt = $db->prepare('SELECT Image from ' . $dbprefix . 'MarkerIcons WHERE IconName=?');
        $res = $stmt->execute(array($name));
        $timage = $stmt->fetch();
        if ($res && $timage) {
            header('Content-Type: image/png');
            echo stream_get_contents($timage[0]);
        } else {
            header('Location: ../images/blank.png');
        }
    }
}

$stmt->closeCursor();

cleanupDb();

exit;
