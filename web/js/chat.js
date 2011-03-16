var ip;
$.ajax({
	type: "GET",
	url: "http://jsonip.appspot.com/?callback=?",
	dataType: "jsonp",
	success: function(getip) { ip = getip.ip; }
	});
function sendChat(me, message) {
	var data = '{"name":"'+ip+'","message":"'+message+'"}';
	$.ajax({
		type: 'POST',
		url: 'up/sendmessage',
		data: data,
		dataType: 'json',
		success: function(response) {
			//handle response
			if(response)
				me.onPlayerChat('', response);
		}
	});
}