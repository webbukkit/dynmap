<?php

define ('HOSTNAME', 'http://localhost:8123/up/');

session_start();

// Change these configuration options if needed, see above descriptions for info.
$enable_jsonp    = false;
$enable_native   = false;
$valid_url_regex = '/.*/';

// ############################################################################

$path = $_GET['path'];
$url = HOSTNAME.$path;


$ch = curl_init( $url );

$client_headers = array();
$client_headers[] = 'X-Forwarded-For: '.$_SERVER['REMOTE_ADDR'];
curl_setopt($ch, CURLOPT_HTTPHEADER, $client_headers);  

if ( strtolower($_SERVER['REQUEST_METHOD']) == 'post' ) {
  $postText = trim(file_get_contents('php://input'));
  curl_setopt( $ch, CURLOPT_POST, true );
  curl_setopt( $ch, CURLOPT_POSTFIELDS, $postText );
}


$cookie = array();
foreach ( $_COOKIE as $key => $value ) {
  $cookie[] = $key . '=' . $value;
}
$cookie[] = SID;
$cookie = implode( '; ', $cookie );
curl_setopt( $ch, CURLOPT_COOKIE, $cookie );
  
//curl_setopt( $ch, CURLOPT_FOLLOWLOCATION, true );
curl_setopt( $ch, CURLOPT_HEADER, true );
curl_setopt( $ch, CURLOPT_RETURNTRANSFER, true );
  
curl_setopt( $ch, CURLOPT_USERAGENT, $_SERVER['HTTP_USER_AGENT'] );
  
list( $header, $contents ) = preg_split( '/([\r\n][\r\n])\\1/', curl_exec( $ch ), 2 );
  
$status = curl_getinfo( $ch );
  
curl_close( $ch );

// Split header text into an array.
$header_text = preg_split( '/[\r\n]+/', $header );
  
// Propagate headers to response.
foreach ( $header_text as $header ) {
  if ( preg_match( '/^(?:Content-Type|Content-Language|Set-Cookie):/i', $header ) ) {
    header( $header );
  }
}
  
print $contents;

?>