var cloneCanvas = function(self) {
	var c = document.createElement('canvas');
	c.width = self.width;
	c.height = self.height;
	var cxt = c.getContext('2d');
	cxt.drawImage(self,0,0);
	return c;
};

function blitImage(ctx, image, sx ,sy, sw, sh, dx, dy, dw, dh) {
	var x; var y;
	for (x=0;x<dw;x++) {
		for (y=0;y<dh;y++) {
			ctx.drawImage(image,Math.floor(sx+x*(sw/dw)),Math.floor(sy+y*(sw/dw)),1,1,dx+x,dy+y,1,1);
		}
	}	
}

function createMinecraftHead(player,completed,failed) {
	var skinImage = new Image();
	skinImage.onload = function() {
		var headCanvas = document.createElement('canvas');
		headCanvas.width = 8;
		headCanvas.height = 8;
		var headContext = headCanvas.getContext('2d');
		blitImage(headContext, skinImage,  8,8,8,8, 0,0,8,8);
		blitImage(headContext, skinImage, 40,8,8,8, 0,0,8,8);
		completed(headCanvas);
	};
	skinImage.onerror = function() {
		if (skinImage.src == 'http://www.minecraft.net/img/char.png') {
			failed();
		} else {
			skinImage.src = 'http://www.minecraft.net/img/char.png';
		}
	};
	skinImage.src = 'http://www.minecraft.net/skin/' + player + '.png';
}

function resizeImage(img,size) {
	var canvas = document.createElement('canvas');
	canvas.width = size;
	canvas.height = size;
	var ctx = canvas.getContext('2d');
	blitImage(ctx, img, 0,0,img.width,img.height, 0,0,size,size);
	return canvas;
}

var playerHeads = {};

function getMinecraftHead(player,size,completed) {
	var head = playerHeads[player];
	// Synchronous
	if (!completed) {
		return (!head || head.working) ? null : head;
	}

	// Asynchronous
	if (!head) {
		playerHeads[player] = { working: true, hooks: [{f:completed,s:size}] };
		//console.log('Creating head for ',player,'...');
		createMinecraftHead(player, function(head) {
			//console.log('Created head for ',player,': ', head);
			hooks = playerHeads[player].hooks;
			playerHeads[player] = head;
			var i;
			for(i=0;i<hooks.length;i++) {
				hooks[i].f(resizeImage(head,hooks[i].s));
			}
		}, function() {
			
		});
	} else if (head.working) {
		//console.log('Other process working on head of ',player,', will add myself to hooks...');
		head.hooks[head.hooks.length] = {f:completed,s:size};
	} else {
		completed(resizeImage(head,size));
	}
}

function getMinecraftTime(servertime) {
	servertime = parseInt(servertime);
	var hours = (parseInt(servertime / 1000)+8) % 24;
	var minutes = parseInt(((servertime / 1000) % 1) * 60);
	var seconds = parseInt(((((servertime / 1000) % 1) * 60) % 1) * 60);
	
	var daytime = 0;
	if(hours >= 6 && hours <= 7)
	daytime =  1;
	if(hours >= 8 && hours <= 9)
	daytime =  2;
	if(hours >= 10 && hours <= 17)
	daytime =  3;
	if(hours >= 18 && hours <= 19)
	daytime =  4;
	if(hours >= 20 && hours <= 21)
	daytime =  5;
	if(hours >= 22 && hours <= 23)
	daytime =  6;
	if(hours >= 0 && hours <= 3)
	daytime =  7;
	if(hours >= 4 && hours <= 5)
	daytime =  8;
	
	return {
		servertime: servertime,
		days: parseInt((servertime+8000) / 24000),
		
		// Assuming it is day at 8:00
		hours: hours,
		minutes: minutes,
		seconds: seconds,
		
		day: daytime,
	};
}
