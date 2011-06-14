  function $() {
    return document.getElementById(arguments[0]);
  }

  window.onload=start;
  
  function start() {   
      if (window.WebSocket) {
        $("realtimeUpdate").style.display = 'block';
        $("realtimeUpdate").onclick = startRealtimeUpdate;
      }
      else {
        $("realtimeUpdate").style.display = 'none';
      }
  }
  
  function log(message)
  {
    var log = $("messageLog");
    log.appendChild(document.createElement("a"));
    var text = document.createElement("div");
    text.innerHTML = message;
    log.appendChild(text);
    
    var maxMessages = document.forms.form1.maxMessages.value;
    var childCount = log.getElementsByTagName("div").length / 2;
    while (childCount > maxMessages)
    {
        log.removeChild(log.firstChild);
        childCount = log.getElementsByTagName("div").length / 2;
    }
    
    var children = log.childNodes;
    var j = 1;
    for(var i = 0; i < children.length; i++)
    {
       var child = children[i];
       if (child.tagName == "A")
       {
            child.setAttribute("name", 'msg-' + j); 
            j++;
       }
    }
    
    var height = 100 + (childCount * 25);

    var html = '<embed height="' + height;
    html = html +  '" width="790" pluginspage="http://www.adobe.com/svg/viewer/install/" type="image/svg+xml" ';
    html = html +  'src="message.svg?maxMessages=' + maxMessages + '&sipMessageFilter=' + document.forms.form1.sipMessageFilter.value + '">';
    $("callflow").innerHTML = html;
         
  }
  
  var webSocket = {

    connect: function() {
      var location = document.location.toString().replace('http:','ws:');
      var location = location.replace('logs-sip','ws-logs');
      this._ws = new WebSocket(location);
      this._ws.onopen = this._onopen;
      this._ws.onmessage = this._onmessage;
      this._ws.onerror = this._onerror;
      this._ws.onclose = this._onclose;
    },

    _onopen: function() {
      $("realtimeUpdate").value = "Stop realtime update";
    },

    send: function(message) {
      if (this._ws) {
        this._ws.send(message);
        log(message);
      }
    },

    _onmessage: function(m) {
      if (m.data) {
        log(m.data);
      }
    },

    _onerror: function(m) {
      if (m.data) {
        log("error: " + m.data);
      }
    },

    _onclose: function(m) {
      this._ws = null;
      $("realtimeUpdate").value = "Start realtime update";
    },

    close: function() {
      if (this._ws) {
        this._ws.close();
      }
    }

  };
  
  function startRealtimeUpdate() {
    if (this.value == "Start realtime update") {
      webSocket.connect();
    } else {
      webSocket.close();
    }
    return false;
  };