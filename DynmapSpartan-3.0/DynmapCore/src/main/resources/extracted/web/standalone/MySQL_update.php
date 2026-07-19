<?php

ob_start();
require_once 'MySQL_funcs.php';
require 'MySQL_config.php';
require 'MySQL_access.php';
ob_end_clean();

$world = $_REQUEST['world'];

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

header('Content-type: application/json; charset=utf-8');

if (strpos($world, '/') || strpos($world, '\\')) {
    echo "{ \"error\": \"invalid-world\" }";
    return;
}

if ($loginenabled) {
    $fname = 'updates_' . $world . '.php';
} else {
    $fname = 'updates_' . $world . '.json';
}

$useridlc = strtolower($userid);
$uid = '[' . $useridlc . ']';

if (isset($worldaccess[$world])) {
    $ss = stristr($worldaccess[$world], $uid);
    if ($ss === false) {
        echo "{ \"error\": \"access-denied\" }";
        return;
    }
}

$serverid = 0;
if (isset($_REQUEST['serverid'])) {
    $serverid = $_REQUEST['serverid'];
}

$content = getStandaloneFile('dynmap_' . $world . '.json');
if (!isset($content)) {
    header('HTTP/1.0 503 Database Unavailable');
    echo "<h1>503 Database Unavailable</h1>";
    echo 'Error reading database - ' . $fname . ' #' . $serverid;
    cleanupDb();
    exit;
}


if (!$loginenabled) {
    echo $content;
} elseif (isset($json->loginrequired) && $json->loginrequired && !$loggedin) {
    echo "{ \"error\": \"login-required\" }";
} else {
    $json = json_decode($content);
    $json->loggedin = $loggedin;
    if (isset($json->protected) && $json->protected) {
        $ss = stristr($seeallmarkers, $uid);
        if ($ss === false) {
            if (isset($playervisible[$useridlc])) {
                $plist = $playervisible[$useridlc];
                $pcnt = count($json->players);
                for ($i = 0; $i < $pcnt; $i++) {
                    $p = $json->players[$i];
                    if (!stristr($plist, '[' . $p->account . ']')) {
                        $p->world = "-some-other-bogus-world-";
                        $p->x = 0.0;
                        $p->y = 64.0;
                        $p->z = 0.0;
                    }
                }
            } else {
                $pcnt = count($json->players);
                for ($i = 0; $i < $pcnt; $i++) {
                    $p = $json->players[$i];
                    if (strcasecmp($userid, $p->account) != 0) {
                        $p->world = "-some-other-bogus-world-";
                        $p->x = 0.0;
                        $p->y = 64.0;
                        $p->z = 0.0;
                    }
                }
            }
        }
    }
    echo json_encode($json);
}
cleanupDb();
