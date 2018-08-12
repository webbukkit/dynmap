<?php
define ('TILESPATH', 'tiles/');

$path = $_SERVER['PATH_INFO'];

$fname = TILESPATH . $path;

if (!file_exists($fname)) {
  $fname = "images/blank.png";
}
$fp = fopen($fname, 'rb');
if (strstr($path, ".png"))
  header("Content-Type: image/png");
else
  header("Content-Type: image/jpeg");

header("Content-Length: " . filesize($fname));

fpassthru($fp);
exit;
?>
