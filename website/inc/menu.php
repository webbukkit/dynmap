<ul id="nav">
<?php

$site			= $_REQUEST["site"];
$menu_id		= $_REQUEST["menu_id"];
$menu_name		= $_REQUEST["menu_name"];
if (!isset($site)) {
	$site		= $conf['site'];
	$menu_id	= $conf['menu_id'];
	$menu_name	= $conf['menu_name'];
}

	$sql="SELECT * FROM navigation ORDER by sort ASC";
	$result = $db->query($sql);

		while ($row = $result->fetch_assoc()) {
			if ($row[id]==$menu_id)
			{
				echo "<li class=\"fstLevelActive\"><a href='index.php?menu_id=$row[id]&menu_name=", urlencode($row[name]), "&site=$row[root]'>$row[name]</a></li>";
			}
			else
			{
				echo "<li class=\"fstLevel\"><a href='index.php?menu_id=$row[id]&menu_name=", urlencode($row[name]), "&site=$row[root]'>$row[name]</a></li>";
			}
		}
?>
</ul>
