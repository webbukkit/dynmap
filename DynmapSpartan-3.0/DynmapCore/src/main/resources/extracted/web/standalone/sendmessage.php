<?php

session_start();

if (is_readable('dynmap_config.json')) {
    $config =  json_decode(file_get_contents('dynmap_config.json'), true);
    $msginterval = $config['webchat-interval'];
} elseif (is_readable('dynmap_config.php')) {
    $lines = file('dynmap_config.php');
    array_shift($lines);
    array_pop($lines);
    $config = json_decode(implode(' ', $lines), true);
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
        $proxy = array_map('trim', explode(',', $_SERVER['HTTP_X_FORWARDED_FOR']));
        $data->ip = $proxy[0];
    }
    if (is_readable('dynmap_webchat.json')) {
        $old_messages = json_decode(file_get_contents('dynmap_webchat.json'), true);
    }
    if (!empty($old_messages)) {
        foreach ($old_messages as $message) {
            if (($timestamp - $config['updaterate'] - 10000) < $message['timestamp']) {
                $new_messages[] = $message;
            }
        }
    }
    $new_messages[] = $data;
    file_put_contents('dynmap_webchat.json', json_encode($new_messages));
    $_SESSION['lastchat'] = time() + $msginterval;
    echo "{ \"error\" : \"none\" }";
} elseif ($_SERVER['REQUEST_METHOD'] == 'POST' && $lastchat > time()) {
    header('HTTP/1.1 403 Forbidden');
} else {
    echo "{ \"error\" : \"none\" }";
}
