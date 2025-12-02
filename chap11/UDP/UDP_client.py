import socket
BUFFSIZE = 1024
port = 2500

#서버와 통신 유형의 소켓 생성
sock = socket.socket(socket.AF_INET,socket.SOCK_DGRAM)

msg = input() #Hello UDP server
sock.sendto(msg.encode(),('172.26.244.85', port)) #메시지 송신
data, addr = sock.recvfrom (BUFFSIZE) #메시지 수신
print("Server says: ", data.decode())