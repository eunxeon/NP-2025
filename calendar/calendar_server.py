import socket
import json
import pymysql
import threading
from datetime import datetime

# ----------------------------------------------------
# MySQL Connect
# ----------------------------------------------------
def get_conn():
    return pymysql.connect(
        host='172.26.240.1',      # WSL → Windows MySQL IP
        user='root',
        password='1234',
        db='calendar',
        charset='utf8mb4',
        cursorclass=pymysql.cursors.DictCursor
    )


# ----------------------------------------------------
# datetime → 문자열 변환 유틸
# ----------------------------------------------------
def convert_datetime(obj):
    if obj is None:
        return None
    if isinstance(obj, datetime):
        return obj.strftime("%Y-%m-%d %H:%M:%S")
    return obj

def convert_row(row):
    if row is None:
        return None
    return {k: convert_datetime(v) for k, v in row.items()}

def convert_rows(rows):
    return [convert_row(r) for r in rows]


# ----------------------------------------------------
# 권한 조회 유틸
#   - owner: Calendar.user_id == user_id
#   - Share.permission: read / write / full
# ----------------------------------------------------
def get_permission(conn, user_id, calendar_id):
    cur = conn.cursor()
    try:
        # 1) 해당 캘린더 존재 여부 및 owner 확인
        cur.execute("SELECT user_id FROM Calendar WHERE id=%s", (calendar_id,))
        row = cur.fetchone()
        if not row:
            return None
        if row["user_id"] == user_id:
            return "owner"

        # 2) 공유 정보에서 permission 확인
        cur.execute("""
            SELECT permission
            FROM Share
            WHERE calendar_id=%s AND target_id=%s AND status='accept'
        """, (calendar_id, user_id))
        row = cur.fetchone()
        if not row:
            return None
        perm = row["permission"] or "read"
        return perm
    finally:
        cur.close()


# ----------------------------------------------------
# Request Handler (기존 그대로)
# ----------------------------------------------------
def handle_request(req):
    action = req.get("action")

    # =========================================================
    # 1. 로그인 / 회원가입
    # =========================================================

    # 1.1 회원가입
    if action == "register":
        email = req["email"]
        pw = req["pw"]
        name = req["name"]

        conn = get_conn()
        cursor = conn.cursor()
        try:
            cursor.execute("SELECT id FROM Users WHERE email=%s", (email,))
            if cursor.fetchone():
                return {"success": False, "message": "이미 존재하는 이메일"}

            cursor.execute(
                "INSERT INTO Users(email, pw, name) VALUES (%s, %s, %s)",
                (email, pw, name)
            )
            conn.commit()
            return {"success": True, "message": "회원가입 완료"}

        finally:
            cursor.close()
            conn.close()

    # 1.2 로그인
    if action == "login":
        email = req["email"]
        pw = req["pw"]

        conn = get_conn()
        cursor = conn.cursor()
        try:
            cursor.execute(
                "SELECT id, name FROM Users WHERE email=%s AND pw=%s",
                (email, pw)
            )
            row = cursor.fetchone()
            if row:
                row = convert_row(row)
                return {
                    "success": True,
                    "user_id": row["id"],
                    "id": row["id"],        # 자바에서 id / user_id 둘 다 읽어도 됨
                    "name": row["name"]
                }
            else:
                return {"success": False, "message": "로그인 실패"}

        finally:
            cursor.close()
            conn.close()

    # 1.3 이메일로 사용자 찾기 (초대용)
    if action == "find_user":
        email = req["email"]

        conn = get_conn()
        cursor = conn.cursor()
        try:
            cursor.execute("SELECT id, name FROM Users WHERE email=%s", (email,))
            row = cursor.fetchone()
            if not row:
                return {"success": False, "message": "사용자를 찾을 수 없습니다."}
            row = convert_row(row)
            return {
                "success": True,
                "user_id": row["id"],
                "name": row["name"]
            }

        finally:
            cursor.close()
            conn.close()

    # =========================================================
    # 2. 캘린더 관리
    # =========================================================

    # 2.1 캘린더 생성
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
                "calendar_id": cursor.lastrowid,
                "message": "캘린더 생성 완료"
            }

        finally:
            cursor.close()
            conn.close()

    # 2.2 캘린더 조회 (내 것 + 공유된 것)
    if action == "calendar_list":
        user_id = req["user_id"]

        conn = get_conn()
        cursor = conn.cursor()
        try:
            cursor.execute("""
                SELECT 
                    c.id,
                    c.user_id,
                    c.name,
                    c.description,
                    c.visibility,
                    CASE 
                        WHEN c.user_id = %s THEN 'owner'
                        WHEN s.status = 'accept' THEN 'shared'
                        ELSE s.status
                    END AS relation,
                    CASE
                        WHEN c.user_id = %s THEN 'owner'
                        ELSE IFNULL(s.permission, 'read')
                    END AS permission
                FROM Calendar c
                LEFT JOIN Share s
                  ON c.id = s.calendar_id AND s.target_id = %s
                WHERE c.user_id = %s
                   OR (s.target_id = %s AND s.status = 'accept')
            """, (user_id, user_id, user_id, user_id, user_id))
            rows = cursor.fetchall()
            rows = convert_rows(rows)
            return {"success": True, "calendars": rows}

        finally:
            cursor.close()
            conn.close()

    # 2.3 캘린더 수정 (owner만 가능)
    if action == "calendar_update":
        calendar_id = req["calendar_id"]
        name = req["name"]
        description = req.get("description", "")
        visibility = req.get("visibility", "전체")
        user_id = req["user_id"]

        conn = get_conn()
        cursor = conn.cursor()
        try:
            cursor.execute("SELECT user_id FROM Calendar WHERE id=%s", (calendar_id,))
            row = cursor.fetchone()
            if not row or row["user_id"] != user_id:
                return {"success": False, "message": "캘린더 수정 권한이 없습니다."}

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

    # 2.4 캘린더 삭제 (owner만 가능, 관련 일정/공유도 삭제)
    if action == "calendar_delete":
        calendar_id = req["calendar_id"]
        user_id = req["user_id"]

        conn = get_conn()
        cursor = conn.cursor()
        try:
            cursor.execute("SELECT user_id FROM Calendar WHERE id=%s", (calendar_id,))
            row = cursor.fetchone()
            if not row or row["user_id"] != user_id:
                return {"success": False, "message": "캘린더 삭제 권한이 없습니다."}

            # 관련 일정 / 공유 먼저 삭제
            cursor.execute("DELETE FROM Schedule WHERE calendar_id=%s", (calendar_id,))
            cursor.execute("DELETE FROM Share WHERE calendar_id=%s", (calendar_id,))
            cursor.execute("DELETE FROM Calendar WHERE id=%s", (calendar_id,))
            conn.commit()
            return {"success": True, "message": "캘린더 삭제 완료"}

        finally:
            cursor.close()
            conn.close()

    # 2.5 공개 범위 변경 (owner만 가능)
    if action == "calendar_visibility":
        calendar_id = req["calendar_id"]
        visibility = req["visibility"]
        user_id = req["user_id"]

        conn = get_conn()
        cursor = conn.cursor()
        try:
            cursor.execute("SELECT user_id FROM Calendar WHERE id=%s", (calendar_id,))
            row = cursor.fetchone()
            if not row or row["user_id"] != user_id:
                return {"success": False, "message": "공개 범위 변경 권한이 없습니다."}

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

    # =========================================================
    # 3. 일정 관리 (권한 체크 포함)
    # =========================================================

    # 3.1 일정 등록 (owner / write / full)
    if action == "schedule_add":
        calendar_id = req["calendar_id"]
        title = req["title"]
        time = req["time"]
        place = req.get("place", "")
        memo = req.get("memo", "")
        user_id = req["user_id"]

        conn = get_conn()
        cursor = conn.cursor()
        try:
            perm = get_permission(conn, user_id, calendar_id)
            if perm not in ("owner", "write", "full"):
                return {"success": False, "message": "일정 추가 권한이 없습니다."}

            cursor.execute("""
                INSERT INTO Schedule(calendar_id, title, time, place, memo)
                VALUES (%s, %s, %s, %s, %s)
            """, (calendar_id, title, time, place, memo))
            conn.commit()
            return {
                "success": True,
                "schedule_id": cursor.lastrowid,
                "message": "일정 등록 완료"
            }

        finally:
            cursor.close()
            conn.close()

    # 3.2 일정 조회 (기존과 동일 - calendar_list 권한으로 제한)
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
            rows = convert_rows(rows)
            return {"success": True, "schedules": rows}

        finally:
            cursor.close()
            conn.close()

    # 3.3 일정 수정 (owner / full)
    if action == "schedule_update":
        schedule_id = req["schedule_id"]
        title = req["title"]
        time = req["time"]
        place = req.get("place", "")
        memo = req.get("memo", "")
        user_id = req["user_id"]

        conn = get_conn()
        cursor = conn.cursor()
        try:
            cursor.execute("SELECT calendar_id FROM Schedule WHERE id=%s", (schedule_id,))
            row = cursor.fetchone()
            if not row:
                return {"success": False, "message": "일정을 찾을 수 없습니다."}
            calendar_id = row["calendar_id"]

            perm = get_permission(conn, user_id, calendar_id)
            if perm not in ("owner", "full"):
                return {"success": False, "message": "일정 수정 권한이 없습니다."}

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

    # 3.4 일정 삭제 (owner / full)
    if action == "schedule_delete":
        schedule_id = req["schedule_id"]
        user_id = req["user_id"]

        conn = get_conn()
        cursor = conn.cursor()
        try:
            cursor.execute("SELECT calendar_id FROM Schedule WHERE id=%s", (schedule_id,))
            row = cursor.fetchone()
            if not row:
                return {"success": False, "message": "일정을 찾을 수 없습니다."}
            calendar_id = row["calendar_id"]

            perm = get_permission(conn, user_id, calendar_id)
            if perm not in ("owner", "full"):
                return {"success": False, "message": "일정 삭제 권한이 없습니다."}

            cursor.execute("DELETE FROM Schedule WHERE id=%s", (schedule_id,))
            conn.commit()
            return {"success": True, "message": "일정 삭제 완료"}

        finally:
            cursor.close()
            conn.close()

    # =========================================================
    # 4. 초대 / 공유 + 권한 관리
    # =========================================================

    # 4.1 사용자 초대 보내기 (기본 permission = read)
    if action == "invite_send":
        user_id = req["user_id"]       # 보낸 사람(캘린더 owner)
        target_id = req["target_id"]   # 받을 사람
        calendar_id = req["calendar_id"]

        conn = get_conn()
        cursor = conn.cursor()
        try:
            # owner인지 확인
            cursor.execute("SELECT user_id FROM Calendar WHERE id=%s", (calendar_id,))
            row = cursor.fetchone()
            if not row or row["user_id"] != user_id:
                return {"success": False, "message": "초대 권한이 없습니다."}

            cursor.execute("""
                INSERT INTO Share(user_id, target_id, calendar_id, status, permission)
                VALUES (%s, %s, %s, 'pending', 'read')
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
                SELECT 
                    s.id,
                    s.user_id,
                    s.calendar_id,
                    s.status,
                    s.permission,
                    u.name AS from_user,
                    c.name AS calendar_name
                FROM Share s
                JOIN Users u ON s.user_id = u.id
                JOIN Calendar c ON s.calendar_id = c.id
                WHERE s.target_id=%s AND s.status='pending'
            """, (target_id,))
            rows = cursor.fetchall()
            rows = convert_rows(rows)
            return {"success": True, "invites": rows}

        finally:
            cursor.close()
            conn.close()

    # 4.3 초대 응답 (수락 / 거절)
    if action == "invite_response":
        share_id = req["share_id"]
        status = req["status"]   # "accept" 또는 "reject"

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

    # 4.4 캘린더별 공유 사용자 목록 조회 (owner 전용)
    if action == "calendar_share_list":
        calendar_id = req["calendar_id"]
        user_id = req["user_id"]

        conn = get_conn()
        cursor = conn.cursor()
        try:
            cursor.execute("SELECT user_id FROM Calendar WHERE id=%s", (calendar_id,))
            row = cursor.fetchone()
            if not row or row["user_id"] != user_id:
                return {"success": False, "message": "권한 관리 권한이 없습니다."}

            cursor.execute("""
                SELECT 
                    s.id AS share_id,
                    s.target_id,
                    s.status,
                    s.permission,
                    u.name AS target_name,
                    u.email AS target_email
                FROM Share s
                JOIN Users u ON s.target_id = u.id
                WHERE s.calendar_id=%s
            """, (calendar_id,))
            rows = cursor.fetchall()
            rows = convert_rows(rows)
            return {"success": True, "shares": rows}

        finally:
            cursor.close()
            conn.close()

    # 4.5 공유 권한 변경 (owner 전용)
    if action == "calendar_set_permission":
        share_id = req["share_id"]
        permission = req["permission"]
        user_id = req["user_id"]

        if permission not in ("read", "write", "full"):
            return {"success": False, "message": "잘못된 권한 값입니다."}

        conn = get_conn()
        cursor = conn.cursor()
        try:
            cursor.execute("""
                SELECT s.calendar_id, c.user_id
                FROM Share s
                JOIN Calendar c ON s.calendar_id = c.id
                WHERE s.id=%s
            """, (share_id,))
            row = cursor.fetchone()
            if not row or row["user_id"] != user_id:
                return {"success": False, "message": "권한 설정 권한이 없습니다."}

            cursor.execute("""
                UPDATE Share
                SET permission=%s
                WHERE id=%s
            """, (permission, share_id))
            conn.commit()
            return {"success": True, "message": "권한이 변경되었습니다."}

        finally:
            cursor.close()
            conn.close()

    # -------------------------
    # 기본 응답
    # -------------------------
    return {"success": False, "message": f"알 수 없는 action: {action}"}


# ----------------------------------------------------
# TCP Server 클래스 (객체지향 + 멀티스레드)
# ----------------------------------------------------
class CalendarTCPServer:
    def __init__(self, host="", port=5000):
        self.host = host
        self.port = port
        self.server_socket = None

    def start(self):
        """서버 소켓 생성 및 클라이언트 접속 대기 루프"""
        self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.server_socket.bind((self.host, self.port))
        self.server_socket.listen(5)

        print(f"[Server] Python Socket 서버 실행 중... PORT={self.port}")

        while True:
            conn, addr = self.server_socket.accept()
            print(f"[클라이언트 접속] {addr}")

            # 클라이언트마다 스레드 생성
            t = threading.Thread(
                target=self.handle_client,
                args=(conn, addr),
                daemon=True
            )
            t.start()

    def handle_client(self, conn, addr):
        """각 클라이언트를 처리하는 함수 (스레드용)"""
        try:
            data = conn.recv(4096)
            if not data:
                return

            text = data.decode()
            print(f"[수신 {addr}] {text}")

            try:
                req_json = json.loads(text)
            except Exception as e:
                print("[JSON 파싱 에러]", e)
                res = {"success": False, "message": "JSON 파싱 실패"}
                conn.send(json.dumps(res, ensure_ascii=False).encode())
                return

            res = handle_request(req_json)
            conn.send(json.dumps(res, ensure_ascii=False).encode())

        except Exception as e:
            print(f"[클라이언트 처리 중 에러 {addr}] {e}")
            # 여기서 추가로 로그 쌓고 싶으면 가능
        finally:
            conn.close()
            print(f"[클라이언트 종료] {addr}")


# ----------------------------------------------------
# main 구동부
# ----------------------------------------------------
if __name__ == "__main__":
    server = CalendarTCPServer(host="", port=5000)
    server.start()
