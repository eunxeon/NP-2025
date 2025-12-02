package calendar;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 일정 공유 프로그램 메인 UI
 * - 주간 뷰 + 스크롤
 * - 일정 클릭 → 상세/수정/삭제
 * - 캘린더 추가/수정/삭제
 * - 초대 + 권한(read/write/full) 관리
 */
public class CalendarUI extends JFrame {

    private final int userId;
    private final String userName;

    // ------ 날짜/시간 ------
    private LocalDate currentWeekStart; // 현재 주의 시작(일요일 기준)
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ------ 상단 ------
    private JLabel monthLabel;

    // ------ 좌측: 미니 달력 + 캘린더 목록 ------
    private JTable miniCalendarTable;
    private JLabel miniMonthLabel;
    private JPanel calendarCheckboxPanel;
    private final List<CalendarItem> calendars = new ArrayList<>();

    // ------ 중앙: 주간 시간표 패널 ------
    private CalendarGridPanel gridPanel;
    private final List<ScheduleBlock> scheduleBlocks = new ArrayList<>();

    public CalendarUI(int userId, String userName) {
        this.userId = userId;
        this.userName = userName;

        setTitle("일정 공유 프로그램 - " + userName);
        setSize(1200, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        LocalDate today = LocalDate.now();
        currentWeekStart = today.minusDays((today.getDayOfWeek().getValue()) % 7);

        buildTopBar();
        buildLeftBar();
        buildCenterGrid();
        buildBottomBar();

        setLocationRelativeTo(null);
        setVisible(true);

        updateMonthLabel();
        updateMiniCalendar();
        loadCalendars();
    }

    // ============================================================
    // UI 구성
    // ============================================================

    private void buildTopBar() {
        JPanel top = new JPanel(new BorderLayout());
        JLabel loginLabel = new JLabel("로그인 사용자: " + userName + " (ID:" + userId + ")");
        loginLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        top.add(loginLabel, BorderLayout.WEST);

        JPanel center = new JPanel();
        JButton prevWeekBtn = new JButton("<");
        JButton nextWeekBtn = new JButton(">");
        monthLabel = new JLabel("", SwingConstants.CENTER);

        center.add(prevWeekBtn);
        center.add(monthLabel);
        center.add(nextWeekBtn);
        top.add(center, BorderLayout.CENTER);

        JButton addSchBtn = new JButton("일정 만들기");
        addSchBtn.addActionListener(e -> addSchedule());
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        right.add(addSchBtn);
        top.add(right, BorderLayout.EAST);

        prevWeekBtn.addActionListener(e -> {
            currentWeekStart = currentWeekStart.minusWeeks(1);
            updateMonthLabel();
            updateMiniCalendar();
            loadSchedulesForCurrentView();
        });
        nextWeekBtn.addActionListener(e -> {
            currentWeekStart = currentWeekStart.plusWeeks(1);
            updateMonthLabel();
            updateMiniCalendar();
            loadSchedulesForCurrentView();
        });

        add(top, BorderLayout.NORTH);
    }

    private void buildLeftBar() {
        JPanel left = new JPanel(new BorderLayout());
        left.setPreferredSize(new Dimension(260, 0));

        // ---- 미니 달력 ----
        JPanel miniPanel = new JPanel(new BorderLayout());
        miniMonthLabel = new JLabel("", SwingConstants.CENTER);
        miniMonthLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));

        String[] dayNames = {"일", "월", "화", "수", "목", "금", "토"};
        DefaultTableModel model = new DefaultTableModel(dayNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        for (int i = 0; i < 6; i++) {
            model.addRow(new Object[]{"", "", "", "", "", "", ""});
        }

        miniCalendarTable = new JTable(model);
        miniCalendarTable.setRowSelectionAllowed(false);
        miniCalendarTable.setCellSelectionEnabled(false);
        miniCalendarTable.setDefaultRenderer(Object.class,
                (table, value, isSelected, hasFocus, row, col) -> {
                    JLabel lbl = new JLabel(value == null ? "" : value.toString(), SwingConstants.CENTER);
                    lbl.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
                    return lbl;
                });

        miniPanel.add(miniMonthLabel, BorderLayout.NORTH);
        miniPanel.add(new JScrollPane(miniCalendarTable), BorderLayout.CENTER);
        left.add(miniPanel, BorderLayout.NORTH);

        // ---- 캘린더 체크박스 목록 ----
        JPanel calWrapper = new JPanel(new BorderLayout());
        JLabel calTitle = new JLabel("캘린더", SwingConstants.LEFT);
        calTitle.setBorder(BorderFactory.createEmptyBorder(8, 5, 4, 5));
        calWrapper.add(calTitle, BorderLayout.NORTH);

        calendarCheckboxPanel = new JPanel();
        calendarCheckboxPanel.setLayout(new BoxLayout(calendarCheckboxPanel, BoxLayout.Y_AXIS));

        JScrollPane calScroll = new JScrollPane(calendarCheckboxPanel);
        calWrapper.add(calScroll, BorderLayout.CENTER);

        left.add(calWrapper, BorderLayout.CENTER);

        add(left, BorderLayout.WEST);
    }

    private void buildCenterGrid() {
        gridPanel = new CalendarGridPanel();

        // 일정 클릭 이벤트
        gridPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                ScheduleBlock block = gridPanel.findScheduleAt(e.getX(), e.getY());
                if (block != null) {
                    showScheduleDetail(block);
                }
            }
        });

        // 🔥 스크롤 추가
        JScrollPane scroll = new JScrollPane(gridPanel);
        scroll.getVerticalScrollBar().setUnitIncrement(30);
        add(scroll, BorderLayout.CENTER);
    }

    private void buildBottomBar() {
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton reloadCalBtn = new JButton("캘린더 새로고침");
        JButton addCalBtn = new JButton("캘린더 추가");
        JButton editCalBtn = new JButton("캘린더 수정");
        JButton delCalBtn = new JButton("캘린더 삭제");
        JButton permBtn = new JButton("권한 관리");
        JButton inviteBtn = new JButton("사용자 초대");
        JButton inviteListBtn = new JButton("받은 초대");

        bottom.add(reloadCalBtn);
        bottom.add(addCalBtn);
        bottom.add(editCalBtn);
        bottom.add(delCalBtn);
        bottom.add(permBtn);
        bottom.add(inviteBtn);
        bottom.add(inviteListBtn);

        reloadCalBtn.addActionListener(e -> loadCalendars());
        addCalBtn.addActionListener(e -> createCalendar());
        editCalBtn.addActionListener(e -> editCalendar());
        delCalBtn.addActionListener(e -> deleteCalendar());
        permBtn.addActionListener(e -> managePermissions());
        inviteBtn.addActionListener(e -> inviteUserByEmail());
        inviteListBtn.addActionListener(e -> showReceivedInvites());

        add(bottom, BorderLayout.SOUTH);
    }

    // ============================================================
    // 상단/좌측 표시 업데이트
    // ============================================================

    private void updateMonthLabel() {
        YearMonth ym = YearMonth.of(currentWeekStart.getYear(), currentWeekStart.getMonth());
        monthLabel.setText(ym.getYear() + "년 " + ym.getMonthValue() + "월");
    }

    private void updateMiniCalendar() {
        YearMonth ym = YearMonth.of(currentWeekStart.getYear(), currentWeekStart.getMonth());
        miniMonthLabel.setText(ym.getYear() + "년 " + ym.getMonthValue() + "월");

        LocalDate first = ym.atDay(1);
        int len = ym.lengthOfMonth();

        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 7; c++) {
                miniCalendarTable.setValueAt("", r, c);
            }
        }

        int startCol = first.getDayOfWeek().getValue() % 7;
        int day = 1;
        int row = 0;
        int col = startCol;

        while (day <= len && row < 6) {
            miniCalendarTable.setValueAt(day, row, col);
            day++;
            col++;
            if (col == 7) {
                col = 0;
                row++;
            }
        }
    }

    // ============================================================
    // 서버 통신: 캘린더 & 일정
    // ============================================================

    private void loadCalendars() {
        calendars.clear();
        calendarCheckboxPanel.removeAll();

        try {
            Client client = new Client("localhost", 5000);
            String req = "{"
                    + "\"action\":\"calendar_list\","
                    + "\"user_id\":" + userId
                    + "}";

            String res = client.send(req);
            boolean success = JsonHelper.getBoolean(res, "success", false);
            if (!success) {
                JOptionPane.showMessageDialog(this, "캘린더 조회 실패");
                return;
            }

            String[] arr = JsonHelper.getObjectsArray(res, "calendars");
            for (String obj : arr) {
                CalendarItem item = new CalendarItem();
                item.id = JsonHelper.getInt(obj, "id", -1);
                item.name = JsonHelper.getString(obj, "name");
                item.relation = JsonHelper.getString(obj, "relation");
                item.permission = JsonHelper.getString(obj, "permission");
                if (item.relation == null) item.relation = "";
                if (item.permission == null) item.permission = "read";

                String text;
                if ("owner".equals(item.relation)) {
                    text = "[내] " + item.name + " (owner)";
                } else if ("shared".equals(item.relation)) {
                    text = "[공유] " + item.name + " (" + item.permission + ")";
                } else {
                    text = item.name;
                }

                item.checkBox = new JCheckBox(text, true);
                item.checkBox.addActionListener(e -> loadSchedulesForCurrentView());

                calendars.add(item);
                calendarCheckboxPanel.add(item.checkBox);
            }

            calendarCheckboxPanel.revalidate();
            calendarCheckboxPanel.repaint();

            loadSchedulesForCurrentView();

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "서버 오류\n" + e.getMessage());
        }
    }

    private void loadSchedulesForCurrentView() {
        scheduleBlocks.clear();
        LocalDate weekEnd = currentWeekStart.plusDays(7);

        for (CalendarItem cal : calendars) {
            if (cal.checkBox == null || !cal.checkBox.isSelected()) continue;

            try {
                Client client = new Client("localhost", 5000);
                String req = "{"
                        + "\"action\":\"schedule_list\","
                        + "\"calendar_id\":" + cal.id
                        + "}";

                String res = client.send(req);
                boolean success = JsonHelper.getBoolean(res, "success", false);
                if (!success) continue;

                String[] objs = JsonHelper.getObjectsArray(res, "schedules");
                for (String obj : objs) {
                    ScheduleBlock b = new ScheduleBlock();
                    b.id = JsonHelper.getInt(obj, "id", -1);
                    b.calendarId = cal.id;
                    b.title = JsonHelper.getString(obj, "title");
                    b.place = JsonHelper.getString(obj, "place");
                    if (b.place == null) b.place = "";
                    String timeStr = JsonHelper.getString(obj, "time");

                    try {
                        LocalDateTime dt = LocalDateTime.parse(timeStr, TIME_FMT);
                        LocalDate d = dt.toLocalDate();
                        if (d.isBefore(currentWeekStart) || !d.isBefore(weekEnd)) continue;
                        b.dateTime = dt;
                        scheduleBlocks.add(b);
                    } catch (Exception ignore) {
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        gridPanel.setSchedules(scheduleBlocks, currentWeekStart);
    }

    // ============================================================
    // 캘린더 추가 / 수정 / 삭제 / 권한 관리
    // ============================================================

    private void createCalendar() {
        String name = JOptionPane.showInputDialog(this, "캘린더 이름:");
        if (name == null || name.trim().isEmpty()) return;

        String desc = JOptionPane.showInputDialog(this, "설명(옵션):");
        if (desc == null) desc = "";

        String[] visOptions = {"전체", "비공개"};
        String visibility = (String) JOptionPane.showInputDialog(
                this, "공개 범위 선택",
                "공개 범위",
                JOptionPane.PLAIN_MESSAGE,
                null,
                visOptions,
                visOptions[0]);
        if (visibility == null) visibility = "전체";

        try {
            Client client = new Client("localhost", 5000);
            String req = "{"
                    + "\"action\":\"calendar_add\","
                    + "\"user_id\":" + userId + ","
                    + "\"name\":\"" + Client.escape(name) + "\","
                    + "\"description\":\"" + Client.escape(desc) + "\","
                    + "\"visibility\":\"" + Client.escape(visibility) + "\""
                    + "}";

            String res = client.send(req);
            boolean success = JsonHelper.getBoolean(res, "success", false);
            String msg = JsonHelper.getString(res, "message");
            JOptionPane.showMessageDialog(this, msg == null
                    ? (success ? "캘린더 생성 완료" : "캘린더 생성 실패")
                    : msg);
            if (success) loadCalendars();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private CalendarItem chooseOwnerCalendar(String titleMsg) {
        List<CalendarItem> owners = new ArrayList<>();
        for (CalendarItem c : calendars) {
            if ("owner".equals(c.relation)) {
                owners.add(c);
            }
        }
        if (owners.isEmpty()) {
            JOptionPane.showMessageDialog(this, "내가 소유한 캘린더가 없습니다.");
            return null;
        }

        String[] options = owners.stream()
                .map(c -> c.name + " (ID:" + c.id + ")")
                .toArray(String[]::new);

        String selected = (String) JOptionPane.showInputDialog(
                this,
                titleMsg,
                "캘린더 선택",
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0]);
        if (selected == null) return null;

        for (CalendarItem c : owners) {
            String label = c.name + " (ID:" + c.id + ")";
            if (label.equals(selected)) return c;
        }
        return null;
    }

    private String getPermissionForCalendar(int calendarId) {
        for (CalendarItem c : calendars) {
            if (c.id == calendarId) return c.permission;
        }
        return "read";
    }

    private void editCalendar() {
        CalendarItem cal = chooseOwnerCalendar("수정할 캘린더를 선택하세요.");
        if (cal == null) return;

        String newName = JOptionPane.showInputDialog(this, "새 이름:", cal.name);
        if (newName == null || newName.trim().isEmpty()) return;

        String newDesc = JOptionPane.showInputDialog(this, "새 설명:", "");
        if (newDesc == null) newDesc = "";

        String[] visOptions = {"전체", "비공개"};
        String visibility = (String) JOptionPane.showInputDialog(
                this, "공개 범위 선택",
                "공개 범위",
                JOptionPane.PLAIN_MESSAGE,
                null,
                visOptions,
                visOptions[0]);
        if (visibility == null) visibility = "전체";

        try {
            Client client = new Client("localhost", 5000);
            String req = "{"
                    + "\"action\":\"calendar_update\","
                    + "\"calendar_id\":" + cal.id + ","
                    + "\"user_id\":" + userId + ","
                    + "\"name\":\"" + Client.escape(newName) + "\","
                    + "\"description\":\"" + Client.escape(newDesc) + "\","
                    + "\"visibility\":\"" + Client.escape(visibility) + "\""
                    + "}";

            String res = client.send(req);
            boolean ok = JsonHelper.getBoolean(res, "success", false);
            String msg = JsonHelper.getString(res, "message");
            JOptionPane.showMessageDialog(this,
                    msg == null ? (ok ? "수정 완료" : "수정 실패") : msg);
            if (ok) loadCalendars();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void deleteCalendar() {
        CalendarItem cal = chooseOwnerCalendar("삭제할 캘린더를 선택하세요.");
        if (cal == null) return;

        int c = JOptionPane.showConfirmDialog(
                this,
                "캘린더 \"" + cal.name + "\" 과(와) 관련 일정/공유를 모두 삭제할까요?",
                "캘린더 삭제 확인",
                JOptionPane.YES_NO_OPTION);
        if (c != JOptionPane.YES_OPTION) return;

        try {
            Client client = new Client("localhost", 5000);
            String req = "{"
                    + "\"action\":\"calendar_delete\","
                    + "\"calendar_id\":" + cal.id + ","
                    + "\"user_id\":" + userId
                    + "}";

            String res = client.send(req);
            boolean ok = JsonHelper.getBoolean(res, "success", false);
            String msg = JsonHelper.getString(res, "message");
            JOptionPane.showMessageDialog(this,
                    msg == null ? (ok ? "삭제 완료" : "삭제 실패") : msg);
            if (ok) loadCalendars();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void managePermissions() {
        CalendarItem cal = chooseOwnerCalendar("권한을 관리할 캘린더를 선택하세요.");
        if (cal == null) return;

        try {
            Client client = new Client("localhost", 5000);
            String req = "{"
                    + "\"action\":\"calendar_share_list\","
                    + "\"calendar_id\":" + cal.id + ","
                    + "\"user_id\":" + userId
                    + "}";

            String res = client.send(req);
            boolean ok = JsonHelper.getBoolean(res, "success", false);
            if (!ok) {
                String msg = JsonHelper.getString(res, "message");
                JOptionPane.showMessageDialog(this,
                        msg == null ? "공유 목록 조회 실패" : msg);
                return;
            }

            String[] objs = JsonHelper.getObjectsArray(res, "shares");
            if (objs.length == 0) {
                JOptionPane.showMessageDialog(this, "공유된 사용자가 없습니다.");
                return;
            }

            int[] shareIds = new int[objs.length];
            String[] display = new String[objs.length];

            for (int i = 0; i < objs.length; i++) {
                String obj = objs[i];
                int shareId = JsonHelper.getInt(obj, "share_id", -1);
                String targetName = JsonHelper.getString(obj, "target_name");
                String email = JsonHelper.getString(obj, "target_email");
                String status = JsonHelper.getString(obj, "status");
                String perm = JsonHelper.getString(obj, "permission");

                shareIds[i] = shareId;
                display[i] = targetName + " (" + email + ") - "
                        + status + " / 권한: " + perm;
            }

            JList<String> list = new JList<>(display);
            JScrollPane sc = new JScrollPane(list);
            sc.setPreferredSize(new Dimension(550, 220));

            int r = JOptionPane.showConfirmDialog(
                    this,
                    sc,
                    "공유 사용자 목록",
                    JOptionPane.OK_CANCEL_OPTION);
            if (r != JOptionPane.OK_OPTION) return;

            int idx = list.getSelectedIndex();
            if (idx < 0) {
                JOptionPane.showMessageDialog(this, "사용자를 선택하세요.");
                return;
            }

            int shareId = shareIds[idx];

            String[] perms = {"read", "write", "full"};
            String newPerm = (String) JOptionPane.showInputDialog(
                    this,
                    "새 권한 선택 (read=보기, write=추가만, full=추가/수정/삭제)",
                    "권한 변경",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    perms,
                    perms[0]);
            if (newPerm == null) return;

            String req2 = "{"
                    + "\"action\":\"calendar_set_permission\","
                    + "\"share_id\":" + shareId + ","
                    + "\"user_id\":" + userId + ","
                    + "\"permission\":\"" + newPerm + "\""
                    + "}";
            String res2 = client.send(req2);
            boolean ok2 = JsonHelper.getBoolean(res2, "success", false);
            String msg2 = JsonHelper.getString(res2, "message");
            JOptionPane.showMessageDialog(this,
                    msg2 == null ? (ok2 ? "권한 변경 완료" : "권한 변경 실패") : msg2);

            if (ok2) loadCalendars();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ============================================================
    // 일정 추가 / 상세보기 / 수정 / 삭제
    // ============================================================

    private void addSchedule() {
        // 일정 추가 가능한 캘린더: owner / write / full
        List<CalendarItem> writable = new ArrayList<>();
        for (CalendarItem c : calendars) {
            if ("owner".equals(c.permission)
                    || "write".equals(c.permission)
                    || "full".equals(c.permission)) {
                writable.add(c);
            }
        }

        if (writable.isEmpty()) {
            JOptionPane.showMessageDialog(this, "일정을 추가할 수 있는 캘린더가 없습니다.");
            return;
        }

        String[] items = writable.stream()
                .map(c -> c.name + " (ID:" + c.id + ")")
                .toArray(String[]::new);

        String selected = (String) JOptionPane.showInputDialog(
                this, "일정을 추가할 캘린더 선택",
                "캘린더 선택",
                JOptionPane.PLAIN_MESSAGE,
                null,
                items,
                items[0]);
        if (selected == null) return;

        CalendarItem target = null;
        for (CalendarItem c : writable) {
            String label = c.name + " (ID:" + c.id + ")";
            if (label.equals(selected)) {
                target = c;
                break;
            }
        }
        if (target == null) return;

        String title = JOptionPane.showInputDialog(this, "일정 제목:");
        if (title == null || title.trim().isEmpty()) return;

        String time = JOptionPane.showInputDialog(
                this,
                "시간 (YYYY-MM-DD HH:MM:SS):",
                LocalDateTime.now().withSecond(0).withNano(0).format(TIME_FMT));
        if (time == null || time.trim().isEmpty()) return;

        String place = JOptionPane.showInputDialog(this, "장소(옵션):");
        if (place == null) place = "";

        String memo = JOptionPane.showInputDialog(this, "메모(옵션):");
        if (memo == null) memo = "";

        try {
            Client client = new Client("localhost", 5000);
            String req = "{"
                    + "\"action\":\"schedule_add\","
                    + "\"calendar_id\":" + target.id + ","
                    + "\"user_id\":" + userId + ","
                    + "\"title\":\"" + Client.escape(title) + "\","
                    + "\"time\":\"" + Client.escape(time) + "\","
                    + "\"place\":\"" + Client.escape(place) + "\","
                    + "\"memo\":\"" + Client.escape(memo) + "\""
                    + "}";

            String res = client.send(req);
            boolean ok = JsonHelper.getBoolean(res, "success", false);
            String msg = JsonHelper.getString(res, "message");
            JOptionPane.showMessageDialog(this,
                    msg == null ? (ok ? "일정 등록 완료" : "등록 실패") : msg);
            if (ok) loadSchedulesForCurrentView();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showScheduleDetail(ScheduleBlock b) {
        String msg = "제목: " + b.title
                + "\n시간: " + b.dateTime.format(TIME_FMT)
                + "\n장소: " + b.place
                + "\n\n무엇을 하시겠습니까?";

        Object[] options = {"수정", "삭제", "닫기"};

        int choice = JOptionPane.showOptionDialog(
                this,
                msg,
                "일정 상세보기",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                null,
                options,
                options[0]
        );

        if (choice == 0) {
            editSchedule(b);
        } else if (choice == 1) {
            deleteSchedule(b.id, b.calendarId);
        }
    }

    private void deleteSchedule(int scheduleId, int calendarId) {
        String perm = getPermissionForCalendar(calendarId);
        if (!"owner".equals(perm) && !"full".equals(perm)) {
            JOptionPane.showMessageDialog(this, "이 일정을 삭제할 권한이 없습니다.");
            return;
        }

        int c = JOptionPane.showConfirmDialog(
                this,
                "정말 삭제하시겠습니까?",
                "삭제 확인",
                JOptionPane.YES_NO_OPTION);
        if (c != JOptionPane.YES_OPTION) return;

        try {
            Client client = new Client("localhost", 5000);
            String req = "{"
                    + "\"action\":\"schedule_delete\","
                    + "\"schedule_id\":" + scheduleId + ","
                    + "\"user_id\":" + userId
                    + "}";

            String res = client.send(req);
            boolean ok = JsonHelper.getBoolean(res, "success", false);
            String msg = JsonHelper.getString(res, "message");
            JOptionPane.showMessageDialog(this,
                    msg == null ? (ok ? "삭제 완료" : "삭제 실패") : msg);
            if (ok) loadSchedulesForCurrentView();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void editSchedule(ScheduleBlock b) {
        String perm = getPermissionForCalendar(b.calendarId);
        if (!"owner".equals(perm) && !"full".equals(perm)) {
            JOptionPane.showMessageDialog(this, "이 일정을 수정할 권한이 없습니다.");
            return;
        }

        String newTitle = JOptionPane.showInputDialog(this, "새 제목:", b.title);
        if (newTitle == null) return;

        String newTime = JOptionPane.showInputDialog(
                this,
                "새 시간 (YYYY-MM-DD HH:MM:SS):",
                b.dateTime.format(TIME_FMT));
        if (newTime == null) return;

        String newPlace = JOptionPane.showInputDialog(this, "새 장소:", b.place);
        if (newPlace == null) newPlace = "";

        String newMemo = JOptionPane.showInputDialog(this, "새 메모:");
        if (newMemo == null) newMemo = "";

        try {
            Client client = new Client("localhost", 5000);
            String req = "{"
                    + "\"action\":\"schedule_update\","
                    + "\"schedule_id\":" + b.id + ","
                    + "\"user_id\":" + userId + ","
                    + "\"title\":\"" + Client.escape(newTitle) + "\","
                    + "\"time\":\"" + Client.escape(newTime) + "\","
                    + "\"place\":\"" + Client.escape(newPlace) + "\","
                    + "\"memo\":\"" + Client.escape(newMemo) + "\""
                    + "}";

            String res = client.send(req);
            boolean ok = JsonHelper.getBoolean(res, "success", false);
            String msg = JsonHelper.getString(res, "message");
            JOptionPane.showMessageDialog(this,
                    msg == null ? (ok ? "수정 완료" : "수정 실패") : msg);
            if (ok) loadSchedulesForCurrentView();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ============================================================
    // 공유: 사용자 초대 / 받은 초대
    // ============================================================

    private void inviteUserByEmail() {
        if (calendars.isEmpty()) {
            JOptionPane.showMessageDialog(this, "먼저 공유할 캘린더를 생성하세요.");
            return;
        }

        CalendarItem cal = chooseOwnerCalendar("공유할 캘린더를 선택하세요.");
        if (cal == null) return;

        String email = JOptionPane.showInputDialog(this, "공유할 사용자의 이메일:");
        if (email == null || email.trim().isEmpty()) return;
        email = email.trim();

        try {
            Client client = new Client("localhost", 5000);

            // 1단계: email → user_id 조회
            String findReq = "{"
                    + "\"action\":\"find_user\","
                    + "\"email\":\"" + Client.escape(email) + "\""
                    + "}";
            String findRes = client.send(findReq);

            boolean found = JsonHelper.getBoolean(findRes, "success", false);
            if (!found) {
                String msg = JsonHelper.getString(findRes, "message");
                JOptionPane.showMessageDialog(this,
                        msg == null ? "사용자를 찾을 수 없습니다." : msg);
                return;
            }

            int targetId = JsonHelper.getInt(findRes, "user_id", -1);
            String targetName = JsonHelper.getString(findRes, "name");

            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "사용자 \"" + targetName + "\" (" + email + ")에게\n"
                            + "캘린더 \"" + cal.name + "\" 을(를) 공유하시겠습니까?",
                    "초대 확인",
                    JOptionPane.YES_NO_OPTION
            );
            if (confirm != JOptionPane.YES_OPTION) return;

            // 2단계: 초대 전송
            String inviteReq = "{"
                    + "\"action\":\"invite_send\","
                    + "\"user_id\":" + userId + ","
                    + "\"target_id\":" + targetId + ","
                    + "\"calendar_id\":" + cal.id
                    + "}";
            String inviteRes = client.send(inviteReq);
            boolean success = JsonHelper.getBoolean(inviteRes, "success", false);
            String msg = JsonHelper.getString(inviteRes, "message");
            JOptionPane.showMessageDialog(this,
                    msg == null ? (success ? "초대 전송 완료" : "초대 전송 실패") : msg);

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "서버 오류\n" + ex.getMessage());
        }
    }

    private void showReceivedInvites() {
        try {
            Client client = new Client("localhost", 5000);
            String jsonReq = "{"
                    + "\"action\":\"invite_list\","
                    + "\"target_id\":" + userId
                    + "}";

            String resStr = client.send(jsonReq);
            boolean success = JsonHelper.getBoolean(resStr, "success", false);
            if (!success) {
                String msg = JsonHelper.getString(resStr, "message");
                JOptionPane.showMessageDialog(this,
                        msg == null ? "초대 목록 조회 실패" : msg);
                return;
            }

            String[] objs = JsonHelper.getObjectsArray(resStr, "invites");
            if (objs.length == 0) {
                JOptionPane.showMessageDialog(this, "받은 초대가 없습니다.");
                return;
            }

            int[] shareIds = new int[objs.length];
            String[] inviteTexts = new String[objs.length];

            for (int i = 0; i < objs.length; i++) {
                String obj = objs[i];
                int shareId = JsonHelper.getInt(obj, "id", -1);
                String fromUser = JsonHelper.getString(obj, "from_user");
                String calName = JsonHelper.getString(obj, "calendar_name");
                shareIds[i] = shareId;
                inviteTexts[i] = "초대ID " + shareId + " - " + fromUser + " 님이 "
                        + "\"" + calName + "\" 캘린더를 공유했습니다.";
            }

            JList<String> list = new JList<>(inviteTexts);
            JScrollPane scroll = new JScrollPane(list);
            scroll.setPreferredSize(new Dimension(550, 200));

            int option = JOptionPane.showConfirmDialog(
                    this,
                    scroll,
                    "받은 초대 목록",
                    JOptionPane.YES_NO_OPTION
            );
            if (option != JOptionPane.YES_OPTION) return;

            int idx = list.getSelectedIndex();
            if (idx < 0) {
                JOptionPane.showMessageDialog(this, "처리할 초대를 선택하세요.");
                return;
            }

            int shareId = shareIds[idx];

            Object[] options = {"수락", "거절", "취소"};
            int choice = JOptionPane.showOptionDialog(
                    this,
                    "선택한 초대를 어떻게 처리할까요?",
                    "초대 처리",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[0]
            );
            if (choice == JOptionPane.CANCEL_OPTION || choice == -1) return;

            String status = (choice == JOptionPane.YES_OPTION) ? "accept" : "reject";

            String respReq = "{"
                    + "\"action\":\"invite_response\","
                    + "\"share_id\":" + shareId + ","
                    + "\"status\":\"" + status + "\""
                    + "}";
            String respRes = client.send(respReq);
            boolean ok = JsonHelper.getBoolean(respRes, "success", false);
            String msg = JsonHelper.getString(respRes, "message");
            JOptionPane.showMessageDialog(this,
                    msg == null ? (ok ? "초대 처리 완료" : "초대 처리 실패") : msg);

            if (ok && "accept".equals(status)) {
                loadCalendars();
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "서버 오류\n" + ex.getMessage());
        }
    }

    // ============================================================
    // 내부 클래스들
    // ============================================================

    private static class CalendarItem {
        int id;
        String name;
        String relation;   // owner / shared / ...
        String permission; // owner / read / write / full
        JCheckBox checkBox;
    }

    private static class ScheduleBlock {
        int id;
        int calendarId;
        LocalDateTime dateTime;
        String title;
        String place;
    }

    /**
     * 중앙 Google Calendar 스타일 주간 그리드
     * - 시간 셀 고정 50px
     * - 일정 클릭 감지
     * - JScrollPane 안에 넣어서 스크롤 가능
     */
    private static class CalendarGridPanel extends JPanel {

        private List<ScheduleBlock> schedules = new ArrayList<>();
        private LocalDate weekStart = LocalDate.now();

        public void setSchedules(List<ScheduleBlock> schedules, LocalDate weekStart) {
            this.schedules = new ArrayList<>(schedules);
            this.weekStart = weekStart;
            repaint();
        }

        // 💡 JScrollPane이 전체 높이를 알 수 있도록 preferredSize 지정
        @Override
        public Dimension getPreferredSize() {
            int leftMargin = 70;
            int topMargin = 30;
            int days = 7;
            int hours = 24;
            int colWidth = 140;    // 대략 값
            int rowHeight = 50;    // 고정

            int width = leftMargin + colWidth * days + 20;
            int height = topMargin + rowHeight * hours + 20;
            return new Dimension(width, height);
        }

        // 일정 클릭 감지
        public ScheduleBlock findScheduleAt(int mx, int my) {
            int leftMargin = 70;
            int topMargin = 30;

            int width = getWidth();
            int days = 7;
            int hours = 24;

            int colWidth = (width - leftMargin) / days;
            int rowHeight = 50;  // 고정

            for (ScheduleBlock b : schedules) {
                int dayIndex = (int) ChronoUnit.DAYS.between(weekStart, b.dateTime.toLocalDate());
                if (dayIndex < 0 || dayIndex >= 7) continue;

                LocalTime t = b.dateTime.toLocalTime();
                double hourPos = t.getHour() + t.getMinute() / 60.0;

                int x = leftMargin + dayIndex * colWidth + 3;
                int y = topMargin + (int) (hourPos * rowHeight) + 3;
                int w = colWidth - 6;
                int h = Math.max(rowHeight - 6, rowHeight / 2);

                if (mx >= x && mx <= x + w && my >= y && my <= y + h) {
                    return b;
                }
            }
            return null;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getWidth() <= 0 || getHeight() <= 0) return;

            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            int leftMargin = 70;
            int topMargin = 30;

            int width = getWidth();
            int height = getHeight();

            int days = 7;
            int hours = 24;

            int colWidth = (width - leftMargin) / days;
            int rowHeight = 50;  // 고정

            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, width, height);

            g2.setColor(new Color(220, 220, 220));
            for (int d = 0; d <= days; d++) {
                int x = leftMargin + d * colWidth;
                g2.drawLine(x, topMargin, x, height);
            }

            for (int h = 0; h <= hours; h++) {
                int y = topMargin + h * rowHeight;
                g2.drawLine(leftMargin, y, width, y);
            }

            g2.setColor(Color.DARK_GRAY);
            for (int h = 0; h < hours; h++) {
                String label = h + ":00";
                int y = topMargin + h * rowHeight + 15;
                g2.drawString(label, 10, y);
            }

            for (int i = 0; i < days; i++) {
                LocalDate d = weekStart.plusDays(i);
                String text = d.getMonthValue() + "/" + d.getDayOfMonth();
                int x = leftMargin + i * colWidth + 5;
                g2.drawString(text, x, 20);
            }

            for (ScheduleBlock b : schedules) {
                LocalDate date = b.dateTime.toLocalDate();
                int dayIndex = (int) ChronoUnit.DAYS.between(weekStart, date);
                if (dayIndex < 0 || dayIndex >= 7) continue;

                LocalTime t = b.dateTime.toLocalTime();
                double hourPos = t.getHour() + t.getMinute() / 60.0;

                int x = leftMargin + dayIndex * colWidth + 3;
                int y = topMargin + (int) (hourPos * rowHeight) + 3;
                int w = colWidth - 6;
                int h = Math.max(rowHeight - 6, rowHeight / 2);

                g2.setColor(new Color(135, 206, 250));
                g2.fillRoundRect(x, y, w, h, 10, 10);
                g2.setColor(new Color(70, 130, 180));
                g2.drawRoundRect(x, y, w, h, 10, 10);

                Shape oldClip = g2.getClip();
                g2.setClip(x + 4, y + 4, w - 8, h - 8);
                g2.setColor(Color.BLACK);
                g2.drawString(b.title, x + 8, y + 20);
                g2.setClip(oldClip);
            }
        }
    }
}
