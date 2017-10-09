package org.easydarwin.blogdemos;

import java.io.IOException;
import java.net.Socket;

/**
 * @CreadBy ：DramaScript
 * @date 2017/8/24
 */
public class SocketInstance {

    private static Socket socket;
    private static final String HOST = "192.168.155.209";
    private static final int PORT = 4321;

    public static Socket getSocket() {
        if (socket == null) {
            try {
                socket = new Socket(HOST, PORT);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return socket;
        } else {
            return socket;
        }
    }
}
