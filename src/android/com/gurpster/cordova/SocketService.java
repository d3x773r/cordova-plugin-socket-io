package com.gurpster.cordova.socket.io;

/*
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
*/

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import io.socket.client.Ack;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class SocketService extends Service {

  private static final String TAG = SocketService.class.getSimpleName();

  private Map<Integer, Emitter.Listener> LISTENERS = new HashMap<>();

  private final IBinder binder = new SocketServiceBinder();

  private Socket socket;

  public boolean isRunning;

  @Override
  public IBinder onBind(Intent intent) {
    isRunning = true;
    return binder;
  }

  public class SocketServiceBinder extends Binder {
    SocketService getService() {
      return SocketService.this;
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    return START_STICKY;
  }

  private void connect(JSONArray args, CallbackContext callbackContext) {

    String uri;

    try {
      uri = args.getString(0);

      IO.Options opts = new IO.Options();
      opts.reconnection = true;
      opts.reconnectionDelay = 60000;
      opts.timeout = -1;

      socket = IO.socket(uri, opts);
      socket.on(Socket.EVENT_CONNECT, args1 -> {
        callbackContext.success();
      });

      socket.on(Socket.EVENT_CONNECT_ERROR, args1 -> {
        callbackContext.error("EVENT_CONNECT_ERROR " + args1[0]);
      });

      socket.connect();
    } catch (Exception e) {
      callbackContext.error("EVENT_CONNECT_ERROR " + e.getMessage());
    }
  }

  private void on(JSONArray args, CallbackContext callbackContext) {

    try {
      String event = args.getString(0);
      final boolean needKeepCallback = args.getBoolean(1);

      Emitter.Listener listener = args1 -> {
        PluginResult pluginResult = makePluginResult(PluginResult.Status.OK, args1);
        pluginResult.setKeepCallback(needKeepCallback);
        callbackContext.sendPluginResult(pluginResult);
      };

      int hashCode = listener.hashCode();
      this.socket.on(event, listener);
      PluginResult pluginResult;

      if (needKeepCallback) {
        pluginResult = new PluginResult(PluginResult.Status.OK, hashCode);
        pluginResult.setKeepCallback(true);
        LISTENERS.put(hashCode, listener);
        callbackContext.sendPluginResult(pluginResult);
      }
    } catch (Exception e) {
      callbackContext.error(e.getMessage());
    }
  }

  private void emit(final JSONArray args, final CallbackContext callbackContext) {

    try {
      boolean needCallback = args.getBoolean(0);
      String event = args.getString(1);

      Object[] params = new Object[args.length() - 2];
      for (int i = 2; i < args.length(); i++) {
        params[i - 2] = args.get(i);
      }

      Class<Socket> socketClass = (Class<Socket>) socket.getClass();
      Method emit;
      if (needCallback) {
        emit = socketClass.getMethod("emit", String.class, Object[].class, Ack.class);
        emit.invoke(socket, event, params, new Ack() {
          @Override
          public void call(Object... args) {
            callbackContext.sendPluginResult(makePluginResult(PluginResult.Status.OK, args));
          }
        });
      } else {
        emit = socketClass.getMethod("emit", String.class, Object[].class);
        emit.invoke(socket, event, params);
      }
    } catch (Exception e) {
      callbackContext.error(e.getMessage());
    }
  }

  private PluginResult makePluginResult(PluginResult.Status status, Object... args) {
    if (args.length == 0) {
      return new PluginResult(status);
    }
    if (args.length == 1) {
      Object args0 = args[0];
      if (args0 instanceof JSONObject) {
        return new PluginResult(status, (JSONObject) args0);
      }
      if (args0 instanceof JSONArray) {
        return new PluginResult(status, (JSONArray) args0);
      }
      if (args0 instanceof Integer) {
        return new PluginResult(status, (Integer) args0);
      }
      if (args0 instanceof String) {
        return new PluginResult(status, (String) args0);
      }
      try {
        return new PluginResult(status, toByteArray(args0));
      } catch (IOException e) {
        return new PluginResult(status, ((String) args0).getBytes());
      }
    }

    JSONArray result = new JSONArray();
    for (int i = 0; i < args.length; i++) {
      result.put(args[i]);
    }
    return new PluginResult(status, result);

  }

  private void off(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    if (args.length() == 0) {
      this.socket.off();
      callbackContext.success();
      return;
    }

    String eventName = args.getString(0);
    if (args.length() == 1) {
      this.socket.off(eventName);
      callbackContext.success();
      return;
    }

    int hashCode = args.getInt(1);
    if (LISTENERS.containsKey(hashCode)) {
      Emitter.Listener listener = LISTENERS.remove(hashCode);
      this.socket.off(eventName, listener);
    }
  }

  public static byte[] toByteArray(Object obj) throws IOException {
    byte[] bytes = null;
    ByteArrayOutputStream bos = null;
    ObjectOutputStream oos = null;
    try {
      bos = new ByteArrayOutputStream();
      oos = new ObjectOutputStream(bos);
      oos.writeObject(obj);
      oos.flush();
      bytes = bos.toByteArray();
    } finally {
      if (oos != null) {
        oos.close();
      }
      if (bos != null) {
        bos.close();
      }
    }
    return bytes;
  }

  @Override
  public void onDestroy() {
    isRunning = false;
    socket.disconnect();
    super.onDestroy();
  }

  public Socket getSocket() {
    return socket;
  }
}
