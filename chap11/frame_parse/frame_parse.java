package chap11.frame_parse;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class frame_parse {

    // Python capsule.frame()과 동일
    public static String frame(int addr, int seqNo, String msg) {
        return String.format("%02d:%04d%04d%s",
                addr, seqNo, msg.length(), msg);
    }

    public static void main(String[] args) {

        final int SIZE = 5;

        int addr = 1;
        int seqNo = 1;

        String frameSeq = "";
        String msg = "hello world";

        System.out.println("전송 메시지: " + msg);

        // 메시지 → 5글자씩 프레임화
        for (int i = 0; i < msg.length(); i += SIZE) {
            String part = msg.substring(i, Math.min(i + SIZE, msg.length()));
            frameSeq += frame(addr, seqNo, part);
            seqNo++;
        }

        try {
            Socket sock = new Socket("localhost", 2500);

            // 전송
            OutputStream out = sock.getOutputStream();
            out.write(frameSeq.getBytes(StandardCharsets.UTF_8));
            out.flush();

            // 수신
            InputStream in = sock.getInputStream();
            byte[] buf = new byte[1024];
            int recvLen = in.read(buf);
            String recv = new String(buf, 0, recvLen, StandardCharsets.UTF_8);

            System.out.println("수신 프레임(raw): " + recv);

            // ========== 여기부터 서버 수정 없이 프레임 복원 ==========

            ArrayList<String> frames = new ArrayList<>();

            int idx = 0;
            while (idx < recv.length()) {

                // addr(2) + ':'(1) + seq(4) + length(4) = header 11자리
                if (idx + 11 > recv.length()) break;

                String header = recv.substring(idx, idx + 11);
                String lengthStr = header.substring(7, 11); // seqNo 뒤 4자리
                int length = Integer.parseInt(lengthStr);

                int frameTotalLength = 11 + length; // header + payload

                String oneFrame = recv.substring(idx, idx + frameTotalLength);
                frames.add(oneFrame);

                idx += frameTotalLength;
            }

            // payload 복원
            StringBuilder restored = new StringBuilder();
            for (String f : frames) {
                int length = Integer.parseInt(f.substring(7, 11));
                restored.append(f.substring(11, 11 + length));
            }

            System.out.println("복원 메시지: " + restored);

            sock.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
