var cloneCanvas = function(self) {
	var c = document.createElement('canvas');
	c.width = self.width;
	c.height = self.height;
	var cxt = c.getContext('2d');
	cxt.drawImage(self,0,0);
	return c;
};

CanvasRenderingContext2D.prototype.blitImage = function(image, sx ,sy, sw, sh, dx, dy, dw, dh) {
	var x; var y;
	for (x=0;x<dw;x++) {
		for (y=0;y<dh;y++) {
			this.drawImage(image,Math.floor(sx+x*(sw/dw)),Math.floor(sy+y*(sw/dw)),1,1,dx+x,dy+y,1,1);
		}
	}	
};

function createMinecraftHead(player,completed) {
	var skinImage = new Image();
	skinImage.onload = function() {
		var headCanvas = document.createElement('canvas');
		headCanvas.width = 8;
		headCanvas.height = 8;
		var headContext = headCanvas.getContext('2d');
		headContext.blitImage(skinImage,  8,8,8,8, 0,0,8,8);
		headContext.blitImage(skinImage, 40,8,8,8, 0,0,8,8);
		completed(headCanvas);
	};
	skinImage.src = 'http://www.minecraft.net/skin/' + player + '.png';
}

function resizeImage(img,size) {
	var canvas = document.createElement('canvas');
	canvas.width = size;
	canvas.height = size;
	var ctx = canvas.getContext('2d');
	ctx.blitImage(img, 0,0,img.width,img.height, 0,0,size,size);
	return canvas;
}

var players = {};

function getMinecraftHead(player,size,completed) {
	var head = players[player];
	// Synchronous
	if (!completed) {
		return (!head || head.working) ? null : head;
	}

	// Asynchronous
	if (!head) {
		players[player] = { working: true, hooks: [{f:completed,s:size}] };
		console.log('Creating head for ',player,'...');
		createMinecraftHead(player, function(head) {
			console.log('Created head for ',player,': ', head);
			hooks = players[player].hooks;
			players[player] = head;
			var i;
			for(i=0;i<hooks.length;i++) {
				hooks[i].f(resizeImage(head,hooks[i].s));
			}
		});
	} else if (head.working) {
		console.log('Other process working on head of ',player,', will add myself to hooks...');
		head.hooks[head.hooks.length] = {f:completed,s:size};
	} else {
		completed(resizeImage(head,size));
	}
}
