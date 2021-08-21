<?php

require_once 'PostgreSQL_funcs.php';

if ($loginenabled) {
    $rslt = getStandaloneFile('dynmap_access.php');
    var_dump($rslt);
    eval($rslt);
}
