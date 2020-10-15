package com.example.eyeoflight;

import android.graphics.Bitmap;

import com.example.eyeoflight.env.ImageUtils;

import org.opencv.calib3d.Calib3d;
import org.opencv.calib3d.StereoBM;
import org.opencv.calib3d.StereoSGBM;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.Queue;

public class RangingHelper {


    public enum Method {BM, SGBM}

    private static Mat M1 = new Mat(3, 3, CvType.CV_64F);
    private static Mat M2 = new Mat(3, 3, CvType.CV_64F);
    private static Mat D1 = new Mat(1, 5, CvType.CV_64F);
    private static Mat D2 = new Mat(1, 5, CvType.CV_64F);
    private static Mat R = new Mat(3, 3, CvType.CV_64F);
    private static Mat T = new Mat(3, 1, CvType.CV_64F);

    static {

        M1.put(0, 0, 483.403229464143,0.322305162469764,319.906546252916,0,483.199210854371,255.601277516644, 0,0,1);
        D1.put(0, 0, 0.116178139136145,-0.148310225488464,-0.000596821006937809,0.000834701531831373,0);
        M2.put(0, 0, 483.196357871702,0.328237800729554,329.281575268190,0,482.895658913392,232.569296329284, 0,0,1);
        D2.put(0, 0, 0.109538850436669,-0.138854715390536,0.000474344111747174,-0.000826384066605694,0);
        R.put(0, 0, 0.999959755905920,4.864674150632188e-04,0.008958231858315,-4.744024301004220e-04,0.999998977732052,-0.001348879974295,-0.008958878886756,0.001344575882879,0.999958964460436);
        T.put(0, 0, -45.310675018572240,-0.141487157845971,-0.085642355461151);
    }

    private float scale = 1.0f;
    private final int maxDisparity = 128;//16的倍数
    private final int minDisparity = 0;
    private final int blocksize = 15;//奇数

    private Mat baseMat;
    private Mat left;
    private Mat right;
    private Mat disparity = new Mat();

    private Mat R1 = new Mat();
    private Mat R2 = new Mat();
    private Mat P1 = new Mat();
    private Mat P2 = new Mat();
    private Mat Q = new Mat();
    private Rect roi1 = new Rect();
    private Rect roi2 = new Rect();

    private Mat map11 = new Mat();
    private Mat map12 = new Mat();
    private Mat map21 = new Mat();
    private Mat map22 = new Mat();

    private Mat xyz = new Mat();

    private float disparity_multiplier = 1.0f;

    private Size imgSize;

    private boolean isLoaded = false;

    private Method method = Method.BM;



    public RangingHelper() {

    }

    public synchronized void setImage(Bitmap bitmap) {
        baseMat = ImageUtils.bitmapToMat(bitmap);
        Rect leftRect = new Rect(0, 0, 640, 480);
        Rect rightRect = new Rect(640, 0, 640, 480);
        left = new Mat(baseMat, leftRect);
        right = new Mat(baseMat, rightRect);
    }

    public void setImage(Mat left, Mat right) {
        this.left = left;
        this.right = right;
    }


    public synchronized void calculate() {
        if (method == Method.BM) {
            Mat temp = left.clone();
            Imgproc.cvtColor(temp, left, Imgproc.COLOR_BGR2GRAY);
            temp = right.clone();
            Imgproc.cvtColor(temp, right, Imgproc.COLOR_BGR2GRAY);
        }
        adjustImage();
        switch (method) {
            case BM:
                stereoBM();
                break;
            case SGBM:
                stereoSGBM();
                break;
        }
        compute();
    }

    public synchronized void calculate(Method method) {
        this.method = method;
        calculate();
    }

    public Bitmap getDisparityBitmap() {
        Mat disp = new Mat();
        disparity.convertTo(disp, CvType.CV_8U, 255 / (maxDisparity * 16.));

        return ImageUtils.matToBitmap(disp);
    }

    public Bitmap getLeftBitmap() {
        return ImageUtils.matToBitmap(left);
    }

    public Bitmap getRightBitmap() {
        return ImageUtils.matToBitmap(right);
    }

    public double getDis(int row, int cal) {
        if (row < 0 || row >= imgSize.height || cal < 0 || cal >= imgSize.width)
            return 16.f;
        double[] ans = getLoc(row, cal);
        return ans[2] / 10000;
    }


    public double getDis(float startRow, float endRow, float startCol, float endCol) {
        double ans;
        int midRow = (int) ((startRow + endRow) / 2);
        int midCol = (int) ((startCol + endCol) / 2);
        for (int col = midCol; col <= endCol; col -= 5) {
            for (int row = midRow; row >= startRow; row -= 5) {
                ans = getDis(row, col);
                if (ans < 16.f)
                    return ans;
            }
            for (int row = midRow + 1; row <= endRow; row += 5) {
                ans = getDis(row, col);
                if (ans < 16.f)
                    return ans;
            }
        }
        for (int col = midCol + 1; col <= endCol; col += 5) {
            for (int row = midRow; row >= startRow; row -= 5) {
                ans = getDis(row, col);
                if (ans < 16.f)
                    return ans;
            }
            for (int row = midRow + 1; row <= endRow; row += 5) {
                ans = getDis(row, col);
                if (ans < 16.f)
                    return ans;
            }
        }
        return 16.f;
    }

    private double[] getLoc(int row, int cal) {
        return xyz.get(row, cal);
    }

    private void adjustImage() {

        if (!isLoaded) {

            //调整图片大小
            if (scale != 1.0f) {
                int method = scale < 1 ? Imgproc.INTER_AREA : Imgproc.INTER_CUBIC;
                Mat temp1 = new Mat(), temp2 = new Mat();
                Imgproc.resize(left, temp1, new Size(), scale, scale, method);
                Imgproc.resize(right, temp2, new Size(), scale, scale, method);

                left = temp1;
                right = temp2;

                multiply(M1, scale);
                multiply(M2, scale);
            }

            imgSize = left.size();

            Calib3d.stereoRectify(M1, D1, M2, D2, imgSize, R, T, R1, R2, P1, P2, Q, Calib3d.CALIB_ZERO_DISPARITY, -1, imgSize, roi1, roi2);

            Calib3d.initUndistortRectifyMap(M1, D1, R1, P1, imgSize, CvType.CV_16SC2, map11, map12);
            Calib3d.initUndistortRectifyMap(M2, D2, R2, P2, imgSize, CvType.CV_16SC2, map21, map22);

            isLoaded = true;
        }

        Mat img1r = new Mat(), img2r = new Mat();
        Imgproc.remap(left, img1r, map11, map12, Imgproc.INTER_LINEAR);
        Imgproc.remap(right, img2r, map21, map22, Imgproc.INTER_LINEAR);

        left = img1r;
        right = img2r;
    }

    private void stereoBM() {
        StereoBM bm = StereoBM.create();

        bm.setROI1(roi1);
        bm.setROI2(roi2);
        bm.setPreFilterCap(31);
        bm.setBlockSize(blocksize);
        bm.setMinDisparity(minDisparity);
        bm.setNumDisparities(maxDisparity);
        bm.setTextureThreshold(10);
        bm.setUniquenessRatio(15);
        bm.setSpeckleWindowSize(100);
        bm.setSpeckleRange(32);
        bm.setDisp12MaxDiff(1);


        bm.compute(left, right, disparity);
        if (disparity.type() == CvType.CV_16S)
            disparity_multiplier = 16.0f;
//        disp.convertTo(disparity, CvType.CV_8U, 255 / (maxDisparity * 16.));
    }

    private void stereoSGBM() {
        StereoSGBM sgbm = StereoSGBM.create();

        int cn = left.channels();
        sgbm.setPreFilterCap(63);
        sgbm.setBlockSize(blocksize);
        sgbm.setP1(8 * cn * blocksize * blocksize);
        sgbm.setP2(32 * cn * blocksize * blocksize);
        sgbm.setMinDisparity(0);
        sgbm.setNumDisparities(maxDisparity);
        sgbm.setUniquenessRatio(10);
        sgbm.setSpeckleWindowSize(100);
        sgbm.setSpeckleRange(32);
        sgbm.setDisp12MaxDiff(1);
        sgbm.setMode(StereoSGBM.MODE_SGBM);

        disparity_multiplier = 1.0f;
        sgbm.compute(left, right, disparity);
        if (disparity.type() == CvType.CV_16S)
            disparity_multiplier = 16.0f;
//        disp.convertTo(disparity, CvType.CV_8U, 255 / (maxDisparity * 16.));
    }

    private void compute() {
        Mat floatDisp = new Mat();
        disparity.convertTo(floatDisp, CvType.CV_32F, 1.0f / disparity_multiplier);
        Calib3d.reprojectImageTo3D(floatDisp, xyz, Q, true);
        if (method == Method.BM) {
            Mat temp = xyz.clone();
            Core.multiply(temp, new Scalar(1, 1, 1), xyz, 16);
        }
    }1

    }


}
