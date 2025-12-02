#TCP Echo Server Program
from socket import *

port = 2500
BUFSIZE = 1024

sock = socket(AF_INET, SOCK_STREAM)
sock.bind(('localhost', port)) #종단점 주소(튜플)과 소켓 결합."는 임의 주소
sock.listen(1)
conn,(remotehost, remoteport)= sock.accept() #연결소켓, 연결주소(IP주소, 포트번호) 반환
print('connected by', remotehost, remoteport)
while True:
    data = conn.recv(BUFSIZE) #데이터 수신
    if not data: #data="이면 종료. "는 False임
        break
    print("Received message: ", data.decode()) #수신 데이터 출력, 바이트형으로 수신되므로 문자열로 변환
    conn.send(data) #수신 데이터를 되돌려 전송
conn.close() #소켓을 닫음