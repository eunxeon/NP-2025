package calendar;

import javax.swing.*;
import java.awt.*;

public class LoginUI extends JFrame {

    private JTextField emailField;
    private JPasswordField pwField;

    public LoginUI() {
        setTitle("로그인");
        setSize(400, 250);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new GridLayout(4, 1, 10, 10));

        JPanel p1 = new JPanel(new FlowLayout());
        p1.add(new JLabel("이메일:"));
        emailField = new JTextField(20);
        p1.add(emailField);

        JPanel p2 = new JPanel(new FlowLayout());
        p2.add(new JLabel("비밀번호:"));
        pwField = new JPasswordField(20);
        p2.add(pwField);

        JButton loginBtn = new JButton("로그인");
        JButton registerBtn = new JButton("회원가입");

        JPanel p3 = new JPanel();
        p3.add(loginBtn);
        p3.add(registerBtn);

        add(new JLabel("일정 공유 프로그램", SwingConstants.CENTER));
        add(p1);
        add(p2);
        add(p3);

        loginBtn.addActionListener(e -> doLogin());
        registerBtn.addActionListener(e -> new RegisterUI());

        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void doLogin() {
        String email = emailField.getText().trim();
        String pw = new String(pwField.getPassword());

        if (email.isEmpty() || pw.isEmpty()) {
            JOptionPane.showMessageDialog(this, "이메일/비밀번호를 입력하세요.");
            return;
        }

        try {
            Client client = new Client("localhost", 5000);
            String jsonReq =
                    "{"
                            + "\"action\":\"login\","
                            + "\"email\":\"" + Client.escape(email) + "\","
                            + "\"pw\":\"" + Client.escape(pw) + "\""
                            + "}";

            String resStr = client.send(jsonReq);
            // System.out.println("LOGIN RESPONSE = " + resStr);

            boolean success = JsonHelper.getBoolean(resStr, "success", false);
            if (success) {
                int userId = JsonHelper.getInt(resStr, "user_id", -1);
                if (userId < 0) {
                    userId = JsonHelper.getInt(resStr, "id", -1);
                }
                String name = JsonHelper.getString(resStr, "name");
                JOptionPane.showMessageDialog(this, "로그인 성공: " + name);

                new CalendarUI(userId, name == null ? "" : name);
                dispose();
            } else {
                String msg = JsonHelper.getString(resStr, "message");
                JOptionPane.showMessageDialog(this, msg == null ? "로그인 실패" : msg);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "서버 연결 실패\n" + ex.getMessage());
        }
    }
}
