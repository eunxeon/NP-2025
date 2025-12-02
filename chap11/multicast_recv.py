from socket import *
import struct

BUFFER = 1024
group_addr = "224.0.0.255"
r_sock = socket(AF_INET,SOCK_DGRAM)
r_sock.setsockopt(SOL_SOCKET, SO_REUSEADDR, 1)
r_sock.bind(("",5005))

mreq = struct.pack("4sl",inet_aton(group_addr),INADDR_ANY) #그룹주소
r_sock.setsockopt(IPPROTO_IP,IP_ADD_MEMBERSHIP,mreq) #그룹가입
print("Ready to receive")

while True:
    rmsg, addr = r_sock.recvfrom(BUFFER)
    print("Received {} from ({}, {})".format(rmsg.decode(), *addr))
    r_sock.sendto('ACK'.encode(), addr) #응답 전송

