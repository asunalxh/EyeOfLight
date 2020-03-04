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

public class RangingHelper {


    public enum Method {BM, SGBM}

    private static Mat M1 = new Mat(3, 3, CvType.CV_64F);
    private static Mat M2 = new Mat(3, 3, CvType.CV_64F);
    private static Mat D1 = new Mat(1, 5, CvType.CV_64F);
    private static Mat D2 = new Mat(1, 5, CvType.CV_64F);
    private static Mat R = new Mat(3, 3, CvType.CV_64F);
    private static Mat T = new Mat(3, 1, CvType.CV_64F);

    static {

        M1.put(0, 0, 485.692525983955, -0.257649066990371, 318.777093535048, 0, 485.377199954270, 255.829500045440, 0, 0, 1);
        D1.put(0, 0, 0.0686246599879822, -0.0201084944179203, 7.32007629519905e-05, 9.19724783774552e-05, 0);
        M2.put(0, 0, 483.943941538688, -0.216185125596302, 327.645270123473, 0, 483.727519328844, 231.254535626299, 0, 0, 1);
        D2.put(0, 0, 0.0416716214468043, 0.118442166661037, 0.000578525154314817, -0.00289414781415480, 0);
        R.put(0, 0, 0.999963773670686, 6.551732301961800e-04, 0.008486583194662, -6.286941270569738e-04, 0.999994927650522, -0.003122405630659, -0.008488585864329, 0.003116957052351, 0.999959113408522);
        T.put(0, 0, -60.587855906352770, 0.086753945901300, -0.865576259426846);
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

    private DecimalFormat decimalFormat = new DecimalFormat("#.0");


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

    public synchronized double getDis(int row, int cal) {
        if (row < 0 || row >= imgSize.height || cal < 0 || cal >= imgSize.width)
            return 16.f;
        double[] ans = getLoc(row, cal);
        return Double.parseDouble(decimalFormat.format(ans[2] / 10000));
    }

    public synchronized double getDis(float startRow, float endRow, float startCol, float endCol) {
        int midRow = (int) ((startRow + endRow) / 2);
        int midCol = (int) ((startCol + endCol) / 2);
        for (int col = midCol; col <=endCol; col-=2) {
            for (int row = midRow; row >= startRow; row-=2) {
                double ans = getDis(row, col);
                if (ans < 16.f)
                    return ans;
            }
            for (int row = midRow + 1; row <= endRow; row+=2) {
                double ans = getDis(row, col);
                if (ans < 16.f)
                    return ans;
            }
        }
        for (int col = midCol+1; col <= endCol; col+=2) {
            for (int row = midRow; row >= startRow; row-=2) {
                double ans = getDis(row, col);
                if (ans < 16.f)
                    return ans;
            }
            for (int row = midRow + 1; row <= endRow; row+=2) {
                double ans = getDis(row, col);
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
    }

    private void multiply(Mat mat, double scale) {
        Mat temp = mat.clone();
        Core.multiply(temp, new Scalar(1), mat, scale);
    }


}
