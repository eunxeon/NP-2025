import socket
import json
import pymysql

# -------------------------
# MySQL Connect (WSL → Windows MySQL)
# -------------------------
def get_conn():
    return pymysql.connect(
        host='10.255.255.254',     # ← WSL resolv.conf에서 얻은 Windows IP
        user='root',
        password='1234',
        db='calendar',
        charset='utf8mb4',
        cursorclass=pymysql.cursors.DictCursor
    )

# -------------------------
# Request 처리 함수
# -------------------------
def handle_request(req):
    action = req.get("action")

    # =========================================================
    # 1. 로그인/회원가입
    # =========================================================

    # 1.1 회원가입
    if action == "register":
        email = req['email']
        pw = req['pw']
        name = req['name']

        conn = get_conn()
        cursor = conn.cursor()
        try:
            cursor.execute("SELECT id FROM Users WHERE email=%s", (email,))
            if cursor.fetchone():
                return {"success": False, "message": "이미 존재하는 이메일"}

            cursor.execute(
                "INSERT INTO Users(email, pw, name) VALUES (%s, %s, %s)",
                (email, pw, name),
            )
            conn.commit()
            return {"success": True, "message": "회원가입 완료"}
        finally:
            cursor.close()
            conn.close()

    # 1.2 로그인
    if action == "login":
        email = req['email']
        pw = req['pw']

        conn = get_conn()
        cursor = conn.cursor()
        try:
            cursor.execute(
                "SELECT id, name FROM Users WHERE email=%s AND pw=%s",
                (email, pw)
            )
            row = cursor.fetchone()
            if row:
                return {
                    "success": True,
                    "user_id": row["id"],
                    "name": row["name"]
                }
            else:
                return {"success": False, "message": "로그인 실패"}
        finally:
            cursor.close()
            conn.close()

    # =========================================================
    # 2. 캘린더 관리
    # =========================================================

    # 2.1 캘린더 등록
    if action == "calendar_add":
        user_id = req["user_id"]
        name = req["name"]
        description = req.get("description", "")
        visibility = req.get("visibility", "전체")

        conn = get_conn()
        cursor = conn.cursor()
        try:
            cursor.execute("""
                INSERT INTO Calendar(user_id, name, description, visibility)
                VALUES (%s, %s, %s, %s)
            """, (user_id, name, description, visibility))
            conn.commit()
            return {
                "success": True,
                "message": "캘린더 생성 완료",
                "calendar_id": cursor.lastrowid
            }
        finally:
            cursor.close()
            conn.close()

    # 2.2 캘린더 조회
    if action == "calendar_list":
        user_id = req["user_id"]

        conn = get_conn()
        cursor = conn.cursor()
        try:
            cursor.execute("""
                SELECT c.id, c.user_id, c.name, c.description, c.visibility,
                       CASE 
                         WHEN c.user_id = %s THEN 'owner'
                         WHEN s.status = 'accept' THEN 'shared'
                         ELSE s.status
                       END AS relation
                FROM Calendar c
                LEFT JOIN Share s
                  ON c.id = s.calendar_id AND s.target_id = %s
                WHERE c.user_id = %s
                   OR (s.target_id = %s AND s.status='accept')
            """, (user_id, user_id, user_id, user_id))
            rows = cursor.fetchall()
            return {"success": True, "calendars": rows}
        finally:
            cursor.close()
            conn.close()

    # 2.3 캘린더 수정
    if action == "calendar_update":
        calendar_id = req["calendar_id"]
        name = req["name"]
        description = req.get("description", "")
        visibility = req.get("visibility", "전체")

        conn = get_conn()
        cursor = conn.cursor()
        try:
            cursor.execute("""
                UPDATE Calendar
                SET name=%s, description=%s, visibility=%s
                WHERE id=%s
            """, (name, description, visibility, calendar_id))
            conn.commit()
            return {"success": True, "message": "캘린더 수정 완료"}
        finally:
            cursor.close()
            conn.close()

    # 2.4 캘린더 삭제
    if action == "calendar_delete":
        calendar_id = req["calendar_id"]

        conn = get_conn()
        cursor = conn.cursor()
        try:
            cursor.execute("DELETE FROM Calendar WHERE id=%s", (calendar_id,))
            conn.commit()
            return {"success": True, "message": "캘린더 삭제 완료"}
        finally:
            cursor.close()
            conn.close()

    # =========================================================
    # 3. 일정 관리
    # =========================================================

    # 3.1 일정 등록
    if action == "schedule_add":
        calendar_id = req["calendar_id"]
        title = req["title"]
        time = req["time"]
        place = req.get("place", "")
        memo  = req.get("memo", "")

        conn = get_conn()
        cursor = conn.cursor()
        try:
            cursor.execute("""
                INSERT INTO Schedule(calendar_id, title, time, place, memo)
                VALUES (%s, %s, %s, %s, %s)
            """, (calendar_id, title, time, place, memo))
            conn.commit()
            return {
                "success": True,
                "message": "일정 등록 완료",
                "schedule_id": cursor.lastrowid
            }
        finally:
            cursor.close()
            conn.close()

    # 3.2 일정 조회
    if action == "schedule_list":
        calendar_id = req["calendar_id"]

        conn = get_conn()
        cursor = conn.cursor()
        try:
            cursor.execute("""
                SELECT id, title, time, place, memo
                FROM Schedule
                WHERE calendar_id=%s
                ORDER BY time
            """, (calendar_id,))
            rows = cursor.fetchall()
            return {"success": True, "schedules": rows}
        finally:
            cursor.close()
            conn.close()

    # 3.3 일정 수정
    if action == "schedule_update":
        schedule_id = req["schedule_id"]
        title = req["title"]
        time = req["time"]
        place = req.get("place", "")
        memo  = req.get("memo", "")

        conn = get_conn()
        cursor = conn.cursor()
        try:
            cursor.execute("""
                UPDATE Schedule
                SET title=%s, time=%s, place=%s, memo=%s
                WHERE id=%s
            """, (title, time, place, memo, schedule_id))
            conn.commit()
            return {"success": True, "message": "일정 수정 완료"}
        finally:
            cursor.close()
            conn.close()

    # 3.4 일정 삭제
    if action == "schedule_delete":
        schedule_id = req["schedule_id"]

        conn = get_conn()
        cursor = conn.cursor()
        try:
            cursor.execute("DELETE FROM Schedule WHERE id=%s", (schedule_id,))
            conn.commit()
            return {"success": True, "message": "일정 삭제 완료"}
        finally:
            cursor.close()
            conn.close()

    # =========================================================
    # 4. 초대/공유
    # =========================================================

    # 4.1 사용자 초대
    if action == "invite_send":
        user_id = req["user_id"]
        target_id = req["target_id"]
        calendar_id = req["calendar_id"]

        conn = get_conn()
        cursor = conn.cursor()
        try:
            cursor.execute("""
                INSERT INTO Share(user_id, target_id, calendar_id)
                VALUES (%s, %s, %s)
            """, (user_id, target_id, calendar_id))
            conn.commit()
            return {"success": True, "message": "초대 전송 완료"}
        finally:
            cursor.close()
            conn.close()

    # 4.2 내가 받은 초대 목록
    if action == "invite_list":
        target_id = req["target_id"]

        conn = get_conn()
        cursor = conn.cursor()
        try:
            cursor.execute("""
                SELECT s.id, s.user_id, s.calendar_id, s.status,
                       u.name AS from_user,
                       c.name AS calendar_name
                FROM Share s
                JOIN Users u ON s.user_id = u.id
                JOIN Calendar c ON s.calendar_id = c.id
                WHERE s.target_id=%s AND s.status='pending'
            """, (target_id,))
            rows = cursor.fetchall()
            return {"success": True, "invites": rows}
        finally:
            cursor.close()
            conn.close()

    # 4.3 초대 응답
    if action == "invite_response":
        share_id = req["share_id"]
        status = req["status"]  # accept/reject

        conn = get_conn()
        cursor = conn.cursor()
        try:
            cursor.execute("""
                UPDATE Share
                SET status=%s
                WHERE id=%s
            """, (status, share_id))
            conn.commit()
            return {"success": True, "message": "초대 처리 완료"}
        finally:
            cursor.close()
            conn.close()

    # 4.4 공개 범위 변경
    if action == "calendar_visibility":
        calendar_id = req["calendar_id"]
        visibility = req["visibility"]

        conn = get_conn()
        cursor = conn.cursor()
        try:
            cursor.execute("""
                UPDATE Calendar
                SET visibility=%s
                WHERE id=%s
            """, (visibility, calendar_id))
            conn.commit()
            return {"success": True, "message": "공개 범위 변경 완료"}
        finally:
            cursor.close()
            conn.close()

    # -------------------------
    # 기본 응답
    # -------------------------
    return {"success": False, "message": f"알 수 없는 action: {action}"}


# -------------------------
# TCP 서버 시작
# -------------------------
HOST = ''
PORT = 5000

server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
server.bind((HOST, PORT))
server.listen(5)

print(f"[Server] Python Socket 서버 실행 중... PORT={PORT}")

while True:
    conn, addr = server.accept()
    print(f"[클라이언트 접속] {addr}")

    data = conn.recv(4096).decode()
    print("[수신]", data)

    try:
        req_json = json.loads(data)
    except Exception as e:
        print("[JSON 파싱 에러]", e)
        conn.send(json.dumps({"success": False, "message": "JSON 파싱 실패"}).encode())
        conn.close()
        continue

    res = handle_request(req_json)
    conn.send(json.dumps(res, ensure_ascii=False).encode())
    conn.close()
