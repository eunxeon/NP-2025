# broadcast_server.py
from socket import *

sock=socket(AF_INET, SOCK_DGRAM)# 브로드캐스팅을 위해 UDP 소켓 사용
sock.setsockopt(SOL_SOCKET, SO_REUSEADDR, 1) #주소 재사용
sock.bind(('', 10000)) #local host, port 10000

while True:
    msg, addr = sock.recvfrom(1024) # receive message
    print(msg.decode())