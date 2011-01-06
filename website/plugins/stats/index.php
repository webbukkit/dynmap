<h1>MCStats</h1>

<table id='online'>
   	<thead>
   		<tr>
   			<th>Spieler zur Zeit online</th>
   		</tr>
   	</thead>
   	<tbody>
   		<tr>
   			<td id='playersOnlineList'>
   			</td>
   		</tr>
   	</tbody>
</table>
<br/>	
<table id='stats'>
   	<thead>
   		<tr>
   			<th>Name</th>
   			<th>Group</th>
   			<th>Placed</th>
   			<th>Destroyed</th>
   			<th>Traveled</th>
   			<th>Player since</th>
   			<th>Last login</th>
   			<th>Playtime</th>
   			<th>Session</th>
   		</tr>
   	</thead>
   	<tbody id='statsTable'></tbody>
</table>
<script>
    tableRefresh();
    setInterval('tableRefresh()', 10000);

    /* generic function for making an XMLHttpRequest
     *  url:   request URL
     *  func:  callback function for success
     *  type:  'text' by default (callback is called with response text)
     *         otherwise, callback is called with a parsed XML dom
     *  fail:  callback function for failure
     *  post:  if given, make a POST request instead of GET; post data given
     *
     *  contenttype: if given for a POST, set request content-type header
     */
    function makeRequest(url, func, type, fail, post, contenttype) {
        var http_request = false;

        type = typeof(type) != 'undefined' ? type : 'text';
        fail = typeof(fail) != 'undefined' ? fail : function() {}

        if (window.XMLHttpRequest) {
            http_request = new XMLHttpRequest();
        } else if (window.ActiveXObject) {
            http_request = new ActiveXObject("Microsoft.XMLHTTP");
        }

        if (type == 'text') {
            http_request.onreadystatechange = function() {
                if (http_request.readyState == 4) {
                    if (http_request.status == 200) {
                        func(http_request.responseText);
                    } else {
                        fail(http_request);
                    }
                }
            }
        } else {
            http_request.onreadystatechange = function() {
                if (http_request.readyState == 4) {
                    if (http_request.status == 200) {
                        func(http_request.responseXML);
                    } else {
                        fail(http_request);
                    }
                }
            }
        }

        if (typeof(post) != 'undefined') {
            http_request.open('POST', url, true);
            if (typeof(contenttype) != 'undefined') http_request.setRequestHeader("Content-Type", contenttype);
            http_request.send(post);
        } else {
            http_request.open('GET', url, true);
            http_request.send(null);
        }
    }

    function tableRefresh() {
        makeRequest("stats/mcstats.json", function(raw) { // this works only with Apache/Lighhtpd Alias to the MCStats folder, a Php solution for MCStats webservice coming soon
            var mcStatsRawData = eval("(" + raw + ")");

            var playersOnline = document.getElementById('playersOnlineList');
            playersOnline.innerHTML = '';
            mcStatsRawData.playersOnline.sort(statsSort);
            for (i in mcStatsRawData.playersOnline) {
                var span = document.createElement('span');
                span.setAttribute('class', 'pOnline ' + groupConcat(mcStatsRawData.playersOnline[i].groups));
                span.innerHTML = mcStatsRawData.playersOnline[i].playerName;
                span.innerHTML += ' ';
                playersOnline.appendChild(span);
            }

            var statsTable = document.getElementById('statsTable')
            statsTable.innerHTML = '';
            mcStatsRawData.playerStats.sort(statsSort);
            for (j in mcStatsRawData.playerStats) {
                var col = 0;

                var ps = mcStatsRawData.playerStats[j];
                var tr = document.createElement('tr');

                var playerNameTd = tr.insertCell(col++);
                playerNameTd.setAttribute('class', 'player');
                var playerNameSpan = document.createElement('span');
                playerNameSpan.setAttribute('class', 'pName ');
                playerNameSpan.innerHTML += ps.playerName;
                playerNameTd.appendChild(playerNameSpan);

                var playerGroups = tr.insertCell(col++);
                playerGroups.setAttribute('class', 'pName ' + groupConcat(ps.playerGroups));
                playerGroups.innerHTML = groupConcat(ps.playerGroups);

                var placed = tr.insertCell(col++);
                placed.setAttribute('class', 'right number');
                placed.innerHTML = ps.blocksPlaced;

                var destroyed = tr.insertCell(col++);
                destroyed.setAttribute('class', 'right number');
                destroyed.innerHTML = ps.blocksDestroyed;

                var traveled = tr.insertCell(col++);
                traveled.setAttribute('class', 'right number');
                traveled.innerHTML = ps.metersTraveled;

                var playersince = tr.insertCell(col++);
                playersince.setAttribute('class', 'right date');
                playersince.innerHTML = formatDate(ps.playerSince);

                var lastLogin = tr.insertCell(col++);
                lastLogin.setAttribute('class', 'right date');
                lastLogin.innerHTML = formatDateTime(ps.lastLogin);

                var totalPlaytime = tr.insertCell(col++);
                totalPlaytime.setAttribute('class', 'right duration');
                if (ps.totalPlaytimeSeconds != '-1') {
                    totalPlaytime.innerHTML = formatTime(ps.totalPlaytimeSeconds);
                } else {
                    totalPlaytime.innerHTML = '-';
                }

                var sessionPlaytime = tr.insertCell(col++);
                sessionPlaytime.setAttribute('class', 'right duration');
                if (ps.sessionPlaytimeSeconds != '-1') {
                    sessionPlaytime.innerHTML = formatTime(ps.sessionPlaytimeSeconds);
                } else {
                    sessionPlaytime.innerHTML = '-';
                }

                statsTable.appendChild(tr);
            }
        }, 'text', function() {
            //alert('failed to get update data');
        });
    }

    function strSortNoCase(a, b) {
        a = a.toLowerCase();
        b = b.toLowerCase();
        if (a > b) return 1;
        if (a < b) return -1;
        return 0;
    }

    function statsSort(a, b) {
        return strSortNoCase(a.playerName, b.playerName);
    }

    function groupConcat(groupArray) {
        return groupArray == null ? '' : $.trim(' ' + groupArray.join(' '));
    }

    function formatDate(unixTimestamp) {
        if (unixTimestamp == '') {
            return '';
        } else {
            var date = new Date(parseInt(unixTimestamp * 1000));
            var getMonth = (date.getMonth() + 1);
            var getDate = date.getDate()
            var getFullYear = date.getFullYear()
            var getHours = date.getHours();
            var getMinutes = date.getMinutes();
            var getSeconds = date.getSeconds();

            if (getMonth < 10) {
                getMonth = "0" + getMonth;
            }
            if (getDate < 10) {
                getDate = "0" + getDate;
            }

            return getDate + '.' + getMonth + '.' + getFullYear;
        }
    }

    function formatDateTime(unixTimestamp) {
        if (unixTimestamp == '') {
            return '';
        } else {
            var date = new Date(parseInt(unixTimestamp * 1000));
            var getMonth = (date.getMonth() + 1);
            var getDate = date.getDate()
            var getFullYear = date.getFullYear()
            var getHours = date.getHours();
            var getMinutes = date.getMinutes();
            var getSeconds = date.getSeconds();

            if (getMonth < 10) {
                getMonth = "0" + getMonth;
            }
            if (getDate < 10) {
                getDate = "0" + getDate;
            }
            if (getHours < 10) {
                getHours = "0" + getHours;
            }
            if (getMinutes < 10) {
                getMinutes = "0" + getMinutes;
            }

            return getDate + '.' + getMonth + '.' + getFullYear + ' at ' + getHours + ':' + getMinutes; // format date and time to fit your locale
        }
    }

    function formatTime(sec) {
        var day;
        var minute;
        var hour;
        var second;

        day = Math.floor(sec / 86400);
        if (sec % 86400 == 0) {
            day = day;
            hour = 0;
            minute = 0;
            second = 0;
        } else {
            hour = Math.floor((sec - (day * 86400)) / 3600);
            if (sec % 3600 == 0) {
                hour = hour;
                minute = 0;
                second = 0;
            } else {
                minute = Math.floor((sec - (day * 86400) - (hour * 3600)) / 60);
                if ((sec - (hour * 3600)) % 60 == 0) {
                    second = second;
                } else {
                    second = (sec - (hour * 3600)) % 60
                    hour = hour;
                    minute = minute;
                    second = second;
                }
            }
        }
        if (day == 0) {
            day = "";
        }
        if (day == 1) {
            day = day + " Tag ";
        }
        if (day >= 2) {
            day = day + " Tage ";
        }
        return (day + hour + ' h ' + minute + ' m'); // format time to fit your locale
    }

    //sortable columns => broken, need to be fixed
    $(document).ready(function() {
        $('#stats').tablesorter({
            textExtraction: function(node) {
                if (node.className.indexOf('player') != -1) {
                    return node.childNodes[0].innerHTML;
                } else if (node.className.indexOf('text') != -1) {
                    return node.innerHTML;
                } else if (node.className.indexOf('number') != -1) {
                    node.innerHTML;
                } else if (node.className.indexOf('date') != -1) {
                    var split = node.innerHTML.split('/');
                    return split[2] + split[0] + split[1];
                } else if (node.className.indexOf('duration') != -1) {
                    return node.innerHTML.split(' ')[0];
                } {
                    return node.innerHTML;
                }
            }
        })
    });
</script>
