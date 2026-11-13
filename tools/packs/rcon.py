"""rcon.py <port> <command...>: send one command over RCON (password 'cctest'), print the reply."""
import socket, struct, sys


def pkt(i, t, body):
    b = body.encode('utf-8') + b'\x00\x00'
    return struct.pack('<iii', len(b) + 8, i, t) + b


def recv(s):
    hdr = b''
    while len(hdr) < 4:
        c = s.recv(4 - len(hdr))
        if not c:
            raise IOError('closed')
        hdr += c
    n = struct.unpack('<i', hdr)[0]
    d = b''
    while len(d) < n:
        c = s.recv(n - len(d))
        if not c:
            raise IOError('closed')
        d += c
    i, t = struct.unpack('<ii', d[:8])
    return i, t, d[8:-2].decode('utf-8', 'replace')


def main():
    port = int(sys.argv[1])
    cmd = ' '.join(sys.argv[2:])
    s = socket.create_connection(('127.0.0.1', port), timeout=600)
    s.sendall(pkt(1, 3, 'cctest'))
    i, t, _ = recv(s)
    if i == -1:
        print('auth failed')
        sys.exit(1)
    s.sendall(pkt(2, 2, cmd))
    i, t, body = recv(s)
    print(body)


if __name__ == '__main__':
    main()
