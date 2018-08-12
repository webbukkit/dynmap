<?php

function cleanupDb() {
   if (isset($db)) {
      $db->close();
      $db = NULL;
   }
}

function abortDb($errormsg) {
   header('HTTP/1.0 500 Error');
   echo "<h1>500 Error</h1>";
   echo $errormsg;
   cleanupDb();
   exit;
}

function initDbIfNeeded() {
   global $db, $dbhost, $dbuserid, $dbpassword, $dbname, $dbport;
   
   $pos = strpos($dbname, '?');

   if ($pos) {
      $dbname = substr($dbname, 0, $pos);
   }
   
   if (!$db) {
      $db = mysqli_connect('p:' . $dbhost, $dbuserid, $dbpassword, $dbname, $dbport);
      if (mysqli_connect_errno()) {
         abortDb("Error opening database");
	  }
   }
}

function getStandaloneFileByServerId($fname, $sid) {
   global $db, $dbprefix;
   
   initDbIfNeeded();
   $stmt = $db->prepare('SELECT Content from ' . $dbprefix . 'StandaloneFiles WHERE FileName=? AND ServerID=?');
   $stmt->bind_param('si', $fname, $sid);
   $res = $stmt->execute();
   $stmt->store_result();
   $stmt->bind_result($content);
   if ($stmt->fetch()) {
        $rslt = $content;
   }
   else {
        $rslt = NULL;
   }
   $stmt->close();	
   return $rslt;
}

function getStandaloneFile($fname) {
   global $serverid;
   
   if (!isset($serverid)) {
      $serverid = 0;
      if(isset($_REQUEST['serverid'])) {
         $serverid = $_REQUEST['serverid'];
      }
   }
   return getStandaloneFileByServerId($fname, $serverid);
}

function updateStandaloneFileByServerId($fname, $sid, $content) {
   global $db, $dbprefix;
   
   initDbIfNeeded();
   $stmt = $db->prepare('UPDATE ' . $dbprefix . 'StandaloneFiles SET Content=? WHERE FileName=? AND ServerID=?');
   $stmt->bind_param('ssi', $content, $fname, $sid);
   $res = $stmt->execute();
   $stmt->close();
   if (!$res) {
      $res = insertStandaloneFileByServerId($fname, $sid, $content);
   }
   return $res;
}

function updateStandaloneFile($fname, $content) {
   global $serverid;
   
   if (!isset($serverid)) {
      $serverid = 0;
      if(isset($_REQUEST['serverid'])) {
         $serverid = $_REQUEST['serverid'];
      }
   }
   return updateStandaloneFileByServerId($fname, $serverid, $content);
}

function insertStandaloneFileByServerId($fname, $sid, $content) {
   global $db, $dbprefix;
   
   initDbIfNeeded();
   $stmt = $db->prepare('INSERT INTO ' . $dbprefix . 'StandaloneFiles (Content,FileName,ServerID) VALUES (?,?,?);');
   $stmt->bind_param('ssi', $content, $fname, $sid);
   $res = $stmt->execute();
   $stmt->close();
   return $res;
}

function insertStandaloneFile($fname, $content) {
   global $serverid;
   
   if (!isset($serverid)) {
      $serverid = 0;
      if(isset($_REQUEST['serverid'])) {
         $serverid = $_REQUEST['serverid'];
      }
   }
   return insertStandaloneFileByServerId($fname, $serverid, $content);
}

?>
