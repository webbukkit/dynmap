function createMinecraftHead(player,size,completed,failed) {
	var faceImage = new Image();
	faceImage.onload = function() {
		completed(faceImage);
	};
	faceImage.onerror = function() {
		failed();
	};
	faceImage.src = dynmap.options.tileUrl + 'faces/' + size + 'x' + size + '/' + player + '.png';
}

var playerHeads = {};

function getMinecraftHead(player,size,completed) {
    var key = player + '.' + size;
	var head = playerHeads[key];
	// Synchronous
	if (!completed) {
		return (!head || head.working) ? null : head;
	}

	// Asynchronous
	if (!head) {
		playerHeads[key] = { working: true, hooks: [{f:completed}] };
		createMinecraftHead(player, size, function(head) {
			hooks = playerHeads[key].hooks;
			playerHeads[key] = head;
			var i;
			for(i=0;i<hooks.length;i++) {
				hooks[i].f(head);
			}
			head.working = false;
		}, function() {

		});
	} else if (head.working) {
		//console.log('Other process working on head of ',player,', will add myself to hooks...');
		head.hooks[head.hooks.length] = {f:completed};
	} else {
		completed(head);
	}
}

//function getMinecraftHead(player,size,completed) {
//	createMinecraftHead(player, size, completed, function() {
//		console.error('Failed to retrieve face of "', player, '" with size "', size, '"!')
//	});
//}

function getMinecraftTime(servertime) {
	servertime = parseInt(servertime);
	var day = servertime >= 0 && servertime < 13700;
	return {
		servertime: servertime,
		days: parseInt((servertime+8000) / 24000),
		
		// Assuming it is day at 8:00
		hours: (parseInt(servertime / 1000)+8) % 24,
		minutes: parseInt(((servertime / 1000) % 1) * 60),
		seconds: parseInt(((((servertime / 1000) % 1) * 60) % 1) * 60),
		
		day: day,
		night: !day
	};
}
