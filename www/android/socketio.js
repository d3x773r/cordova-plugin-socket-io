var exec = require('cordova/exec');

const PLUGIN_NAME = "SocketIO";

var BaseClass = function() {
 var self = this;
 var _vars = {};
 var _listeners = {};

 self.get = function(key) {
   return key in _vars ? _vars[key] : null;
 };
 self.set = function(key, value) {
   _vars[key] = value;
 };

 self.trigger = function(eventName) {
   var args = [];
   for (var i = 1; i < arguments.length; i++) {
     args.push(arguments[i]);
   }
   var event = document.createEvent('Event');
   event.initEvent(eventName, false, false);
   event.mydata = args;
   event.myself = self;
   document.dispatchEvent(event);
 };
 self.on = function(eventName, callback) {
   _listeners[eventName] = _listeners[eventName] || [];

   var listener = function (e) {
     if (!e.myself || e.myself !== self) {
       return;
     }
     callback.apply(self, e.mydata);
   };
   document.addEventListener(eventName, listener, false);
   _listeners[eventName].push({
     'callback': callback,
     'listener': listener
   });
 };
 self.addEventListener = self.on;

 self.off = function(eventName, callback) {
   var i;
   if (typeof eventName === "string" &&
       eventName in _listeners) {

     if (typeof callback === "function") {
       for (i = 0; i < _listeners[eventName].length; i++) {
         if (_listeners[eventName][i].callback === callback) {
           document.removeEventListener(eventName, _listeners[eventName][i].listener);
           _listeners[eventName].splice(i, 1);
           break;
         }
       }
     } else {
       delete _listeners[eventName];
     }
   } else {
     //Remove all event listeners
     var eventNames = Object.keys(_listeners);
     for (i = 0; i < eventNames.length; i++) {
       eventName = eventNames[i];
       for (var j = 0; j < _listeners[eventName].length; j++) {
         document.removeEventListener(eventName, _listeners[eventName][j].listener);
       }
     }
     _listeners = {};
   }
 };

 self.removeEventListener = self.off;


 self.one = function(eventName, callback) {
   _listeners[eventName] = _listeners[eventName] || [];

   var listener = function (e) {
     if (!e.myself || e.myself !== self) {
       return;
     }
     callback.apply(self, e.mydata);
     self.off(eventName, callback);
   };
   document.addEventListener(eventName, listener, false);
   _listeners[eventName].push({
     'callback': callback,
     'listener': listener
   });
 };
 self.addEventListenerOnce = self.one;

 self.errorHandler = function(msg) {
   if (params_.debug) console.error(msg);
   self.trigger('error', msg);
   return false;
 };

  return self;
};

var Socket = function() {
  BaseClass.apply(this);
  Object.defineProperty(this, "type", {
    value: "Socket",
    writable: false
  });
};

var params_ = {};

Socket.prototype = new BaseClass();

Socket.prototype.emit = function(eventName) {

  var self = this;
  var args = Array.prototype.slice.call(arguments, 0);
  var callback = null;

  if (typeof args[args.length - 1] === "function") {
    callback = args.pop();
  }

  args.unshift(!!callback);

  var success = function(res) {

    if (callback) {

      if (!res) {
        callback.call(self);
        return;
      }

      var args = Array.prototype.slice.call(arguments, 0);
      if (params_.debug) {
        console.log(res);
        console.log(args);
        console.log(arguments);
      }

      callback.apply(self, args);
    }
  };

  var error = function(err) {
    self.trigger("error", err);
  };

  exec.call(cordova, success, error, PLUGIN_NAME, "emit", args);
};

var connect = function(params, callbackSuccess, callbackError) {

  params_ = params;

  var socket = new Socket();
  var socketOn_ = socket.on;
  var socketOne_ = socket.one;
  var socketOff_ = socket.off;

  socket.on = function(eventName, callback) {

    if (typeof eventName !== "string" && typeof callback !== "function") {
      return;
    }

    socketOn_.call(socket, eventName, callback);

    var cnt = 0;

    exec(function(res) {

      if (cnt == 0) {
        callback._hashCode = res;
      } else {
        if (params_.debug) console.log(res);

        var args = Array.prototype.slice.call(arguments, 0);

        if (params_.debug) console.log(args);

        callback.apply(socket, args);
      }

      cnt = 1;

    }, function(err) {
      socket.trigger("error", err);
    }, PLUGIN_NAME, "on", [eventName, true]);

  };

  socket.one = function(eventName, callback) {

    if (typeof eventName !== "string" && typeof callback !== "function") {
      return;
    }

    socketOne_.call(socket, eventName, function() {

      var args = Array.prototype.slice.call(arguments, 0);

      socket.off(eventName);

      callback.apply(socket, args);
    });

    exec(function(res) {

      socket.off(eventName);

      var args = Array.prototype.slice.call(arguments, 0);

      callback.apply(socket, args);

    }, function(err) {
      socket.trigger("error", err);
    }, PLUGIN_NAME, "on", [eventName, false]);

  };

  socket.off = function(eventName, callback) {

    var args = [];

    if (eventName) {
      args.push(eventName);
      if (typeof callback == "function" && "_hashCode" in callback) {
        args.push(callback._hashCode);
      }
    }

    exec(null, function(err) {
      socket.trigger("error", err);
    }, PLUGIN_NAME, "off", args);

    socketOff_.apply(socket, args);
  };

  exec(function() {

    if (params_.debug) console.log("---connected");

    if (typeof callbackSuccess === "function") {
      callbackSuccess(socket);
    }

  }, function(err) {

    if (typeof callbackError === "function") {
      callbackError(err);
    }

    socket.trigger("error", err);

  }, PLUGIN_NAME, "connect", [params]);

  if (params_.debug) console.log('try connect to socket...');

  return socket;
};

module.exports = {
  connect: connect
};