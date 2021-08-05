<?php

require_once 'PostgreSQL_funcs.php';

if ($loginenabled) {
    $rslt = getStandaloneFile("dynmap_login.php");
    eval($rslt);
}
