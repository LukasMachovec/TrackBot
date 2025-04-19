import numpy as np
import cv2
import base64
from PIL import Image
import io
import os
import pkg_resources

class ObjectDetection:
    def __init__(self):
        #weights_path="assets/yolov4-tiny.weights"
        #cfg_path="assets/my_own_yolov4.cfg"
        
        #cfg_path = os.path.join(os.getcwd(), "my_own_yolov4.cfg")
        #weights_path = os.path.join(os.getcwd(), "yolov4-tiny.weights")
        
        cfg_path = pkg_resources.resource_filename(__name__, "my_own_yolov4.cfg")
        weights_path = pkg_resources.resource_filename(__name__, "yolov4-tiny.weights")
        
        self.nmsThreshold = 0.4
        self.confThreshold = 0.5
        self.image_size = 608

        net = cv2.dnn.readNet(weights_path, cfg_path)
        self.model = cv2.dnn_DetectionModel(net)

        self.classes = ["Person"]
        self.model.setInputParams(size=(self.image_size, self.image_size), scale=1/255)

    def detect(self, frame):
        return self.model.detect(frame, nmsThreshold=self.nmsThreshold, confThreshold=self.confThreshold)

def main(data):
    decoded_data = base64.b64decode(data)
    np_data = np.fromstring(decoded_data, np.uint8)
    img = cv2.imdecode(np_data, cv2.IMREAD_UNCHANGED)
    #img_gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

    od = ObjectDetection()

    (class_ids, scores, boxes) = od.detect(img)

    try:
        bbox_tuple = boxes[0]
        bbox_string = str(bbox_tuple)
        new_bbox_string = bbox_string[2:-1]  # removes the [] brackets left by the tuple
    except:
        return None



    return new_bbox_string

    # cv2.rectangle(img, (x, y), (x + w, y + h), (0, 255, 0), 3, 1)
    #
    # pil_im = Image.fromarray(img)
    # buff = io.BytesIO()
    # pil_im.save(buff, format="PNG")
    # img_str = base64.b64encode(buff.getvalue())
    # return "" + str(img_str, 'utf-8')

