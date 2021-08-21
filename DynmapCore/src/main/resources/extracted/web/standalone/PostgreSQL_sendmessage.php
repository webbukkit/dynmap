<?php

ob_start();
require_once 'PostgreSQL_funcs.php';
require 'PostgreSQL_config.php';
ob_end_clean();

session_start();

$content = getStandaloneFile('dynmap_config.json');
if (isset($content)) {
    $config = json_decode($content, true);
    $msginterval = $config['webchat-interval'];
} else {
    $msginterval = 2000;
}

if (isset($_SESSION['lastchat'])) {
    $lastchat = $_SESSION['lastchat'];
} else {
    $lastchat = 0;
}

if ($_SERVER['REQUEST_METHOD'] == 'POST' && $lastchat < time()) {
    $micro = microtime(true);
    $timestamp = round($micro * 1000.0);

    $data = json_decode(trim(file_get_contents('php://input')));
    $data->timestamp = $timestamp;
    $data->ip = $_SERVER['REMOTE_ADDR'];
    if (isset($_SESSION['userid'])) {
        $uid = $_SESSION['userid'];
        if (strcmp($uid, '-guest-')) {
            $data->userid = $uid;
        }
    }
    if (isset($_SERVER['HTTP_X_FORWARDED_FOR'])) {
        $data->ip = $_SERVER['HTTP_X_FORWARDED_FOR'];
    }
    $content = getStandaloneFile('dynmap_webchat.json');
    $gotold = false;
    if (isset($content)) {
        $old_messages = json_decode($content, true);
        $gotold = true;
    }

    if (!empty($old_messages)) {
        foreach ($old_messages as $message) {
            if (($timestamp - $config['updaterate'] - 10000) < $message['timestamp']) {
                $new_messages[] = $message;
            }
        }
    }
    $new_messages[] = $data;

    if ($gotold) {
        updateStandaloneFile('dynmap_webchat.json', json_encode($new_messages));
    } else {
        insertStandaloneFile('dynmap_webchat.json', json_encode($new_messages));
    }

    $_SESSION['lastchat'] = time() + $msginterval;
    echo "{ \"error\" : \"none\" }";
} elseif ($_SERVER['REQUEST_METHOD'] == 'POST' && $lastchat > time()) {
    header('HTTP/1.1 403 Forbidden');
} else {
    echo "{ \"error\" : \"none\" }";
}
cleanupDb();
