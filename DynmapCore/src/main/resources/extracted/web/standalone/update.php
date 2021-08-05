<?php

ob_start();
require 'dynmap_access.php';
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

header('Content-type: text/plain; charset=utf-8');

if (strpos($world, '/') || strpos($world, '\\')) {
    echo "{ \"error\": \"invalid-world\" }";
    return;
}

if (isset($webpath)) {
    $fname = $webpath . '/standalone/updates_' . $world . '.php';
} else {
    $fname = 'updates_' . $world . '.php';
}

if (!is_readable($fname)) {
    header('HTTP/1.0 404 Not Found');
    return;
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

$lines = file($fname);
if (!$lines) {
    header('HTTP/1.0 404 Not Found');
    return;
}
array_shift($lines);
array_pop($lines);
$json = json_decode(implode(' ', $lines));


if (isset($json->loginrequired) && $json->loginrequired && !$loggedin) {
    echo "{ \"error\": \"login-required\" }";
} else {
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
