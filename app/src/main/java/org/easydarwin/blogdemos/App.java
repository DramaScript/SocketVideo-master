package org.easydarwin.blogdemos;

import android.app.Application;
import android.util.Log;

import java.io.IOException;
import java.net.Socket;

public class App extends Application {

    private static App sInstance;

    private Socket socket;
    private  final String HOST = "192.168.156.72";
    private  final int PORT = 4321;

    public  Socket getSocket() {
        if (socket == null) {
            try {
                socket = new Socket(HOST, PORT);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return socket;
        } else {            return socket;
        }
    }

    public static App getInstance() {
        return sInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
    }

}
