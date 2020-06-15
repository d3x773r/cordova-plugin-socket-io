cordova-plugin-socketIO
================

socket.io plugin for PhoneGap/Cordova with native Android background services
Currently Android only.

```
cordova plugin add https://github.com/d3x773r/cordova-plugin-socketIO

cordova plugin rm https://github.com/d3x773r/cordova-plugin-socketIO
```

This plugin uses these libraries:
- https://github.com/socketio/socket.io-client-java

###Usage

```js
cordova.plugin.socket.io.connect('http://localhost:3000', function(socket) {
  console.log("connected");
  socket.on("connection", function() {
    console.log("connected");
    socket.emit("message", {text: "My name is HAL 9000"}, function(data) {
      console.log(data);
    });
  });
  socket.on("error", function(err) {
    console.log(err);
  });
  socket.on('message', function(message) {
    console.log('message', message);
  });
  socket.on('disconnect', function () {
    console.log('disconnect');
  });
});
```

###Note
