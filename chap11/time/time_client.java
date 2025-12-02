package chap11.time;

import java.net.Socket;

public class time_client {
    public static void main(String[] args) {
        try {
            Socket sock = new Socket("localhost", 5000);
            byte[] buf = new byte[1024];
            int n = sock.getInputStream().read(buf);
            System.out.println("현재 시각: " + new String(buf, 0, n));
            sock.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
