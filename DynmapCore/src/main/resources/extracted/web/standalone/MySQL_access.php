<?php
require_once('MySQL_funcs.php');

if ($loginenabled) {
    $rslt = getStandaloneFile('dynmap_access.php');
	eval($rslt);
}
?>
