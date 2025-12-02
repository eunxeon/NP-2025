package calendar;

import javax.swing.*;
import java.awt.*;

public class RegisterUI extends JFrame {

    private JTextField emailField;
    private JTextField nameField;
    private JPasswordField pwField;
    private JPasswordField pwField2;

    public RegisterUI() {
        setTitle("회원가입");
        setSize(400, 300);
        setLayout(new GridLayout(5, 1, 10, 10));

        JPanel p1 = new JPanel(new FlowLayout());
        p1.add(new JLabel("이메일:"));
        emailField = new JTextField(20);
        p1.add(emailField);

        JPanel p2 = new JPanel(new FlowLayout());
        p2.add(new JLabel("이름:"));
        nameField = new JTextField(20);
        p2.add(nameField);

        JPanel p3 = new JPanel(new FlowLayout());
        p3.add(new JLabel("비밀번호:"));
        pwField = new JPasswordField(20);
        p3.add(pwField);

        JPanel p4 = new JPanel(new FlowLayout());
        p4.add(new JLabel("비밀번호 확인:"));
        pwField2 = new JPasswordField(20);
        p4.add(pwField2);

        JButton submitBtn = new JButton("가입하기");
        submitBtn.addActionListener(e -> doRegister());

        add(p1);
        add(p2);
        add(p3);
        add(p4);
        add(submitBtn);

        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void doRegister() {
        String email = emailField.getText().trim();
        String name = nameField.getText().trim();
        String pw1 = new String(pwField.getPassword());
        String pw2 = new String(pwField2.getPassword());

        if (email.isEmpty() || name.isEmpty() || pw1.isEmpty()) {
            JOptionPane.showMessageDialog(this, "모든 항목을 입력하세요.");
            return;
        }
        if (!pw1.equals(pw2)) {
            JOptionPane.showMessageDialog(this, "비밀번호가 일치하지 않습니다.");
            return;
        }

        try {
            Client client = new Client("localhost", 5000);
            String jsonReq =
                    "{"
                            + "\"action\":\"register\","
                            + "\"email\":\"" + Client.escape(email) + "\","
                            + "\"pw\":\"" + Client.escape(pw1) + "\","
                            + "\"name\":\"" + Client.escape(name) + "\""
                            + "}";

            String resStr = client.send(jsonReq);
            boolean success = JsonHelper.getBoolean(resStr, "success", false);
            String msg = JsonHelper.getString(resStr, "message");
            if (success) {
                JOptionPane.showMessageDialog(this, msg == null ? "회원가입 완료" : msg);
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, msg == null ? "회원가입 실패" : msg);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "서버 연결 실패\n" + ex.getMessage());
        }
    }
}
