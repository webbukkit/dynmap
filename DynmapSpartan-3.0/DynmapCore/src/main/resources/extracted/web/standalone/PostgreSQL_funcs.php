<?php

function cleanupDb()
{
    if (isset($db)) {
        $db->close();
        $db = null;
    }
}

function abortDb($errormsg)
{
    header('HTTP/1.0 500 Error');
    echo "<h1>500 Error</h1>";
    echo $errormsg;
    cleanupDb();
    exit;
}

function initDbIfNeeded()
{
    global $db, $dbhost, $dbuserid, $dbpassword, $dbname, $dbport;

    $pos = strpos($dbname, '?');

    if ($pos) {
        $dbname = substr($dbname, 0, $pos);
    }

    if (!$db) {
        $db = new PDO("pgsql:host=$dbhost;port=$dbport;dbname=$dbname", $dbuserid, $dbpassword, array(PDO::ATTR_PERSISTENT => true));
        $db->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
        if (!$db) {
            abortDb("Error opening database");
        }
    }
}


function getStandaloneFileByServerId($fname, $sid)
{
    global $db, $dbprefix;

    initDbIfNeeded();
    $stmt = $db->prepare('SELECT Content from ' . $dbprefix . 'StandaloneFiles WHERE FileName=:fname AND ServerID=:sid');
    $stmt->bindParam(':fname', $fname, PDO::PARAM_STR);
    $stmt->bindParam(':sid', $sid, PDO::PARAM_INT);
    $res = $stmt->execute();
    $content = $stmt->fetch(PDO::FETCH_BOTH);
    if ($res && $content) {
        $rslt = stream_get_contents($content[0]); //stupid streams...
    } else {
        $rslt = null;
    }
    $stmt->closeCursor();
    return $rslt;
}

function getStandaloneFile($fname)
{
    global $serverid;

    if (!isset($serverid)) {
        $serverid = 0;
        if (isset($_REQUEST['serverid'])) {
            $serverid = $_REQUEST['serverid'];
        }
    }
    return getStandaloneFileByServerId($fname, $serverid);
}

function updateStandaloneFileByServerId($fname, $sid, $content)
{
    global $db, $dbprefix;

    initDbIfNeeded();
    $stmt = $db->prepare('UPDATE ' . $dbprefix . 'StandaloneFiles SET Content=:content WHERE FileName=:fname AND ServerID=:sid');
    $stmt->bindParam(':content', $content, PDO::PARAM_LOB);
    $stmt->bindParam(':fname', $fname, PDO::PARAM_STR);
    $stmt->bindParam(':sid', $sid, PDO::PARAM_INT);
    $res = $stmt->execute();
    $stmt->closeCursor();
    if (!$res) {
        $res = insertStandaloneFileByServerId($fname, $sid, $content);
    }
    return $res;
}

function updateStandaloneFile($fname, $content)
{
    global $serverid;

    if (!isset($serverid)) {
        $serverid = 0;
        if (isset($_REQUEST['serverid'])) {
            $serverid = $_REQUEST['serverid'];
        }
    }
    return updateStandaloneFileByServerId($fname, $serverid, $content);
}

function insertStandaloneFileByServerId($fname, $sid, $content)
{
    global $db, $dbprefix;

    initDbIfNeeded();
    $stmt = $db->prepare('INSERT INTO ' . $dbprefix . 'StandaloneFiles (Content,FileName,ServerID) VALUES (?,?,?);');
    $res = $stmt->execute(array($content, $fname, $sid));
    $stmt->close();
    return $res;
}

function insertStandaloneFile($fname, $content)
{
    global $serverid;

    if (!isset($serverid)) {
        $serverid = 0;
        if (isset($_REQUEST['serverid'])) {
            $serverid = $_REQUEST['serverid'];
        }
    }
    return insertStandaloneFileByServerId($fname, $serverid, $content);
}
