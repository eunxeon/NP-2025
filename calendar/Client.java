package calendar;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Client {

    private final String host;
    private final int port;

    public Client(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String send(String json) throws Exception {
        try (Socket sock = new Socket(host, port)) {
            OutputStream out = sock.getOutputStream();
            out.write(json.getBytes(StandardCharsets.UTF_8));
            out.flush();

            InputStream in = sock.getInputStream();
            byte[] buf = new byte[8192];
            int n = in.read(buf);
            if (n == -1) return "";

            return new String(buf, 0, n, StandardCharsets.UTF_8);
        }
    }

    // JSON 문자열에서 사용할 escape
    public static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
