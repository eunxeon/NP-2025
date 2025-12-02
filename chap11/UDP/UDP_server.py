import socket
port = 2500
BUFFSIZE = 1024
sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.bind(("", port))
while True:
    data, addr = sock.recvfrom(BUFFSIZE)
    print("Received message: ", data.decode())
    
    sock.sendto(data,addr)