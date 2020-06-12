import pyaudio
import socket
import threading


class AudioThread:
    def __init__(self, ip, port):
        p = pyaudio.PyAudio()
        self.stream = p.open(
            format=p.get_format_from_width(2),
            channels=1,
            rate=44100,
            output=True
        )

        server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server_socket.bind((ip, port))
        server_socket.listen(5)
        self.socket = server_socket

        print(f"listening audio at {ip}:{port}")
        self.is_running = True
        thread = threading.Thread(target=self.thread_func)
        thread.start()

    def thread_func(self):
        while True:  # 接受多个用户
            client_socket, address = self.socket.accept()
            print("audio client:", address)
            while True:  # 获取声音帧
                data = client_socket.recv(1000)
                if data == b'':
                    break
                self.stream.write(data)
            print("audio close:", address)
