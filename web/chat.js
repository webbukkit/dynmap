function sendChat(message) {
	var ip;
	$.ajax({
		type: "GET",
		url: "http://jsonip.appspot.com/?callback=?",
		dataType: "jsonp",
		success: function(getip) {
				var data = '{"name":"'+getip.ip+'","message":"'+message+'"}';
				$.ajax({
					type: 'POST',
					url: 'up/sendmessage',
					data: data,
					dataType: 'json',
					success: function(response) {
						//handle response
				}
			});
		}
	});
}
//curl -d '{"name":"Nickname","message":"Hello"}' http://localhost:8123/up/sendmessage
