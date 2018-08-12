<?php
ob_start();
include('dynmap_login.php');
ob_end_clean();

session_start();

if(isset($_POST['j_username'])) {
  $userid = $_POST['j_username'];
}
else {
  $userid = '-guest-';
}
$good = false;

if(strcmp($userid, '-guest-')) {
  if(isset($_POST['j_password'])) {
    $password = $_POST['j_password'];
  }
  else {
    $password = '';
  }
  $ctx = hash_init('sha256');
  hash_update($ctx, $pwdsalt);
  hash_update($ctx, $password);
  $hash = hash_final($ctx);
  $useridlc = strtolower($userid);
  if (strcasecmp($hash, $pwdhash[$useridlc]) == 0) {
     $_SESSION['userid'] = $userid;
     $good = true; 
  }
  else {
     $_SESSION['userid'] = '-guest-';
  }
}
else {
  $_SESSION['userid'] = '-guest-';
  $good = true;
}
/* Prune pending registrations, if needed */
$newlines[] = '<?php /*';
if(is_readable('dynmap_reg.php'))
	$lines = file('dynmap_reg.php');
else
	$lines = array();
if(!empty($lines)) {
	$cnt = count($lines) - 1;
	$changed = false;
	for($i=1; $i < $cnt; $i++) {
		list($uid, $pc, $hsh) = explode('=', rtrim($lines[$i]));
		if($uid == $useridlc) continue;
		if(array_key_exists($uid, $pendingreg)) {
			$newlines[] = $uid . '=' . $pc . '=' . $hsh;
		}
		else {
			$changed = true;
		}
	}
	if($changed) {
		if(count($newlines) < 2) {	/* Nothing? */
			unlink('dynmap_reg.php');
		}
		else {
			$newlines[] = '*/ ?>';
			file_put_contents('dynmap_reg.php', implode("\n", $newlines));
		}
	}
}

if($good) {
   echo "{ \"result\": \"success\" }"; 
}
else {
   echo "{ \"result\": \"loginfailed\" }"; 
}
 
?>

