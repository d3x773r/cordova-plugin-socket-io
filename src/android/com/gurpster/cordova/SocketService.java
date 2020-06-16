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
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import io.socket.client.Ack;
import io.socket.client.IO;
import io.socket.client.Manager;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import io.socket.engineio.client.Transport;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.util.*;

public class SocketService extends Service {

  private static final String TAG = SocketService.class.getSimpleName();

  private Map<Integer, Emitter.Listener> LISTENERS = new HashMap<>();

  private final IBinder binder = new SocketServiceBinder();

  private Socket socket;

  private SharedPreferences sharedPreferences;

  public boolean isRunning;

  @Override
  public IBinder onBind(Intent intent) {
    isRunning = true;
    sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
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

  private Emitter.Listener onTransport = new Emitter.Listener() {
    @Override
    public void call(Object... args) {

      Transport transport = (Transport) args[0];
      transport.on(Transport.EVENT_REQUEST_HEADERS, new Emitter.Listener() {
        @Override
        public void call(Object... args) {
          if (sharedPreferences.contains("socket_headers")) {
            Map<String, List<String>> defaultHeaders = (Map<String, List<String>>) args[0];
            String headers = sharedPreferences.getString("socket_headers", "");
            if (headers != null && !headers.isEmpty()) {
              try {
                JSONObject jsonObject = new JSONObject(headers.trim());
                Iterator<String> keys = jsonObject.keys();
                while (keys.hasNext()) {
                  String key = keys.next();
                  if (jsonObject.has(key)) {
                    Log.d("key:", key + " value: " + jsonObject.getString(key));
                    defaultHeaders.put(key, Collections.singletonList(jsonObject.getString(key)));
                  }
                }
              } catch (JSONException e) {
                Log.d(TAG, e.getMessage());
              }
            }
          }
        }
      }).on(Transport.EVENT_RESPONSE_HEADERS, new Emitter.Listener() {
        @Override
        public void call(Object... args) {
        }
      });
    }
  };

  private void connect(JSONArray args, CallbackContext callbackContext) {

    try {

      JSONObject object = args.getJSONObject(0);

      String url = object.getString("url");

      JSONObject headers = object.has("headers") ? object.getJSONObject("headers") : null;
      if (headers != null) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("socket_headers", headers.toString());
        editor.apply();
      }

      IO.Options opts = getOptions(object);

      socket = IO.socket(url, opts);
//      if (sharedPreferences.contains("socket_headers")) {
//        socket.io().on(Manager.EVENT_TRANSPORT, onTransport);
//      }
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

  private IO.Options getOptions(JSONObject jsonObject) throws JSONException {
    IO.Options options = new IO.Options();

    options.reconnection = jsonObject.has("reconnection") ? jsonObject.getBoolean("reconnection") : options.reconnection;
    options.reconnectionDelay = jsonObject.has("reconnectionDelay") ? jsonObject.getInt("reconnectionDelay") : options.reconnectionDelay;
    options.timeout = jsonObject.has("timeout") ? jsonObject.getInt("timeout") : options.timeout;
    options.forceNew = jsonObject.has("forceNew") ? jsonObject.getBoolean("forceNew") : options.forceNew;
    options.query = jsonObject.has("query") ? jsonObject.getString("query") : options.query;
    options.multiplex = jsonObject.has("multiplex") ? jsonObject.getBoolean("multiplex") : options.multiplex;
    options.randomizationFactor = jsonObject.has("randomizationFactor") ? jsonObject.getDouble("randomizationFactor") : options.randomizationFactor;
    options.reconnectionAttempts = jsonObject.has("reconnectionAttempts") ? jsonObject.getInt("reconnectionAttempts") : options.reconnectionAttempts;
    options.reconnectionDelayMax = jsonObject.has("reconnectionDelayMax") ? jsonObject.getInt("reconnectionDelayMax") : options.reconnectionDelayMax;
    options.rememberUpgrade = jsonObject.has("rememberUpgrade") ? jsonObject.getBoolean("rememberUpgrade") : options.rememberUpgrade;
    options.upgrade = jsonObject.has("multiplex") ? jsonObject.getBoolean("multiplex") : options.multiplex;
    options.host = jsonObject.has("host") ? jsonObject.getString("host") : options.host;
    options.hostname = jsonObject.has("hostname") ? jsonObject.getString("hostname") : options.hostname;
    options.path = jsonObject.has("path") ? jsonObject.getString("path") : options.path;
    options.policyPort = jsonObject.has("policyPort") ? jsonObject.getInt("policyPort") : options.policyPort;
    options.port = jsonObject.has("port") ? jsonObject.getInt("") : options.port;
    options.secure = jsonObject.has("secure") ? jsonObject.getBoolean("") : options.secure;
    options.timestampParam = jsonObject.has("timestampParam") ? jsonObject.getString("timestampParam") : options.timestampParam;
    options.timestampRequests = jsonObject.has("timestampRequests") ? jsonObject.getBoolean("timestampRequests") : options.timestampRequests;

    return options;
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
