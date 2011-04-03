<?php
define ('HOSTNAME', 'http://localhost:8123/up/');

$path = $_GET['path'];
$url = HOSTNAME.$path;

// Open the Curl session
$session = curl_init($url);

// If it's a POST, put the POST data in the body
if ( $_SERVER['REQUEST_METHOD'] === 'POST' ) {
// Read the input from stdin
$postText = trim(file_get_contents('php://input'));
curl_setopt($session, CURLOPT_POST, true);
curl_setopt($session, CURLOPT_POSTFIELDS, $postText);
}

// Don't return HTTP headers. Do return the contents of the call
curl_setopt($session, CURLOPT_HEADER, false);
curl_setopt($session, CURLOPT_RETURNTRANSFER, true);

// Make the call
$body = curl_exec($session);

header("Content-Type: ".curl_getinfo($session, CURLINFO_CONTENT_TYPE);

echo $body;
curl_close($session);

?>


