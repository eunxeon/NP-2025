from socket import*

addr =('172.26.244.85',10000) # 브로드캐스트 주소 튜플

#브로드캐스트를 위한 소켓 설정
sock = socket(AF_INET, SOCK_DGRAM)
sock.setsockopt(SOL_SOCKET, SO_REUSEADDR, 1)
sock.setsockopt(SOL_SOCKET, SO_BROADCAST, 1) #브로드캐스트 옵션 설정

while True:
    smsg = input('Broadcast Message:')
    sock.sendto(smsg.encode(), (addr)) #브로드캐스트 메시지 전송