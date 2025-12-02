from socket import *
import struct

group_addr=("224.0.0.255", 5005) #group address
s_sock = socket(AF_INET, SOCK_DGRAM)
#datagram socket 사용
s_sock.settimeout(0.5)
TTL = struct.pack('@i', 2) #TTL=2. 4바이트 정수형으로 표현

s_sock.setsockopt(IPPROTO_IP,
IP_MULTICAST_TTL, TTL)
s_sock.setsockopt(IPPROTO_IP,
IP_MULTICAST_LOOP, False)

while True:
    rmsg = input('Your message: ')
    s_sock.sendto(rmsg.encode(), group_addr)

#브로드캐스트 메시지 전송

    while True:
        try:
            response, addr = s_sock.recvfrom(1024) #모든 수신자로부터 응답 수신
        except timeout: #타임아웃 예외 발생
            break
        else:
            print('{ from {}'.format(response.decode(),addr))