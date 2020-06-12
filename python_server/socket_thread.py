from PyQt5 import QtWidgets
from PyQt5.QtCore import QThread, pyqtSignal

import subprocess
import sys
import socket
import _thread
import time
import signal
import cv2
import numpy as np
import datetime
import av
from av.codec.context import CodecContext


class SocketThread(QThread):
    signal = pyqtSignal(int)
    signal_socket_close = pyqtSignal(int)

    def __init__(self, ip, port):
        super().__init__()

        server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server_socket.bind((ip, port))
        server_socket.listen(5)
        self.socket = server_socket
        print("listening", ip, 'at', port)

        self.codec = CodecContext.create('h264', 'r')
        self.img = None

    def run(self):
        while True:  # 接受多个用户
            client_socket, address = self.socket.accept()
            print("video client: ", address)

            while True:  # 获取视频帧
                data = client_socket.recv(1000)
                if data == b'':
                    break
                packets = self.codec.parse(data)
                for packet in packets:
                    frames = self.codec.decode(packet)
                    for frame in frames:
                        img = frame.to_ndarray(format='rgb24')
                        self.img = img
                        self.signal.emit(1)

            print("video close: ", address)
            self.signal_socket_close.emit(1)
