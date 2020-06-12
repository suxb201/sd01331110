import sys
from socket_thread import SocketThread
from audio_thread import AudioThread
from PyQt5.QtGui import QImage, QPixmap
from PyQt5 import QtWidgets
from PyQt5.QtWidgets import QHBoxLayout, QApplication, QLabel
import qrcode
import cv2
import numpy as np
from PyQt5.QtCore import *


class App(QtWidgets.QDialog):

    def __init__(self, ip, video_port, audio_port):
        super().__init__()
        self.ip = ip
        self.video_port = video_port
        self.audio_port = audio_port
        self.setWindowTitle("echo")
        self.setGeometry(100, 100, 1080, 1920)

        self.img_label = QLabel(self)
        self.img_label.setAlignment(Qt.AlignCenter)
        self.layout = QHBoxLayout()
        self.layout.addWidget(self.img_label)
        self.setLayout(self.layout)

        self.socket_thread = SocketThread(ip, video_port)
        self.socket_thread.start()
        self.socket_thread.signal.connect(self.flush_img)
        self.socket_thread.signal_socket_close.connect(self.show_qrcode)
        self.audio_thread = AudioThread(ip, audio_port)

        self.show_qrcode()

    def flush_img(self):
        if self.socket_thread.img is None:
            return

        img = self.socket_thread.img
        qt_img = QImage(img.data, img.shape[1], img.shape[0], QImage.Format_RGB888)
        self.img_label.setPixmap(QPixmap.fromImage(qt_img))

    def show_qrcode(self):
        qr = qrcode.make(f"{self.ip},{self.video_port},{self.audio_port}")
        image = qr.get_image().convert("L")
        img = cv2.cvtColor(np.array(image), cv2.COLOR_GRAY2RGB)
        img = cv2.resize(img, (512, 512), interpolation=cv2.INTER_NEAREST)
        self.socket_thread.img = img
        self.flush_img()


if __name__ == '__main__':
    app = QApplication(sys.argv)

    ex = App('192.168.1.102', 10009, 10010)
    ex.show()
    sys.exit(app.exec_())
