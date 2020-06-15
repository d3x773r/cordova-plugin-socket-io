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

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;

import java.lang.reflect.Method;

import static android.content.Context.BIND_AUTO_CREATE;

public class SocketIO extends CordovaPlugin {

  private SocketService socketService;

  private final ServiceConnection connection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
      SocketService.SocketServiceBinder binder = (SocketService.SocketServiceBinder) service;
      SocketIO.this.socketService = binder.getService();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
    }
  };

  @Override
  protected void pluginInitialize() {
    if (!isMyServiceRunning(SocketService.class) || socketService == null) {
      startService();
    }
    super.pluginInitialize();
  }

  public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) {
    final boolean[] rt = new boolean[]{false};

    new Handler().postDelayed(() -> {
      try {
        Method method = socketService.getClass().getDeclaredMethod(
          action,
          JSONArray.class,
          CallbackContext.class
        );
        method.setAccessible(true);
        method.invoke(socketService, args, callbackContext);
        rt[0] = true;
      } catch (Exception e) {
        callbackContext.error(e.getMessage());
        rt[0] = false;
      }
    }, 500);

    return rt[0];
  }

  private void startService() {
    Activity context = cordova.getActivity();
    Intent intent = new Intent(context, SocketService.class);
    context.bindService(intent, connection, BIND_AUTO_CREATE);
    context.startService(intent);
  }

  private void stopService() {
    Activity context = cordova.getActivity();
    Intent intent = new Intent(context, SocketService.class);
    context.stopService(intent);
  }

  private boolean isMyServiceRunning(Class<?> serviceClass) {

    if (socketService == null) {
      return false;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      return socketService.isRunning;
    } else {
      ActivityManager manager = (ActivityManager) cordova.getActivity().getSystemService(Context.ACTIVITY_SERVICE);
      for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
        if (serviceClass.getName().equals(service.service.getClassName())) {
          return true;
        }
      }
    }

    return false;
  }

  @Override
  public void onDestroy() {
    stopService();
    super.onDestroy();
  }
}
