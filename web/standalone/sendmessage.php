<?php
session_start();

$config =  json_decode(file_get_contents('dynmap_config.json'), true);
$msginterval = $config['webchat-interval'];

if($_SERVER['REQUEST_METHOD'] == 'POST' && $_SESSION['lastchat'] < time())
{
	$micro = explode(' ', microtime());
	$timestamp = $micro[1].round($micro[0]*1000);
	
	$data = json_decode(trim(file_get_contents('php://input')));
	$data->timestamp = $timestamp;
	$old_messages = json_decode(file_get_contents('dynmap_webchat.json'), true);
	if(!empty($old_messages))
	{
		foreach($old_messages as $message)
		{
			if(($timestamp - $config['updaterate'] - 10000) < $message['timestamp'])
				$new_messages[] = $message;
		}
	}
	$new_messages[] = $data;
	file_put_contents('dynmap_webchat.json', json_encode($new_messages));
	$_SESSION['lastchat'] = time()+$msginterval;
}
elseif($_SERVER['REQUEST_METHOD'] == 'POST' && $_SESSION['lastchat'] > time())
{
	echo json_encode(str_replace('%interval%', $msginterval, $config['spammessage']));
}

?>