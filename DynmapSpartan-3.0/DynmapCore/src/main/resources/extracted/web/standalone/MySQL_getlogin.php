<?php

require_once 'MySQL_funcs.php';

if ($loginenabled) {
    $rslt = getStandaloneFile("dynmap_login.php");
    eval($rslt);
}
