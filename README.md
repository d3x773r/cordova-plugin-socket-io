cordova-plugin-socket-io
================

socket.io plugin for PhoneGap/Cordova with native Android background service.

Currently Android only.

```
cordova plugin add https://github.com/d3x773r/cordova-plugin-socket-io.git

cordova plugin rm https://github.com/d3x773r/cordova-plugin-socket-io.git
```

This plugin uses:
- https://github.com/socketio/socket.io-client-java

For more information about socket.io see that:
- https://socket.io/docs/

## Usage

###### SERVER

```js
var app = require('express')();
var http = require('http').Server(app);
var io = require('socket.io')(http);

const nsp = io.of('/');

nsp.on('connection', function(socket){
  console.log('Client connected.');

  socket.on('message', function(message) {
    console.log('message', message);
  });

  socket.on('disconnect', function() {
    console.log('Client disconnected.');
  });

  socket.on('my custom event', function(data) {
    console.log(data);
  });
});

app.get('/', function(req, res){
  res.send('server is running');
});

app.get('/test', function(req, res){
  nsp.emit('test', {data: 'testing'});
  res.send('testing');
});

http.listen(3000, function(){
	console.log('listening on port 3000');
});
```

###### CLIENT
```js
cordova.plugin.socket.io.connect({
      url: 'http://localhost:3000',
      reconnection: true,
      reconnectionDelay: 60000,
      timeout: -1,
      headers: {
        Authorization: 'dl3230r4frlgtmk'
      },
      query: new URLSearchParams({
        test: '123'
      }).toString()
    }, function (socket) {
      socket.on("connected", function () {
        console.log("connected to ", socket);
      });
      socket.on('disconnect', function () {
        console.log('disconnect');
      });
      socket.on("error", function (err) {
        console.log(err);
      });
  });
```
