import socket
import capsule
SIZE = 5
sock = socket.socket()
sock.connect(('localhost', 2500))

HEAD = 0x05 #시작문자
addr = 1
seqNo=1
frame_seq=""
msg = "hello world"
print("전송 메시지: ", msg)
for i in range(0, len(msg), SIZE):
    frame_seq += capsule.frame(HEAD, addr, seqNo, msg[i:i+SIZE])
    seqNo += 1 #순서번호 증가
sock.send(frame_seq.encode()) #프레임 전송
msg = sock.recv(1024).decode() #프레임 수신
print("수신 프레임: ",msg)
r_frame=msg.split(chr(0x05)) #프레임 분할
del r_frame[0] #것 번째 프레임 요소는 "
p_msg=''
for field in r_frame: #메시지 복원
    p_msg += field[11:(12+int(field[8:11]))]
print("복원 메시지: ",p_msg)
sock.close()