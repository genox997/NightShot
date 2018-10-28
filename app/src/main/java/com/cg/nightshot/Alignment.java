package com.cg.nightshot;

import android.graphics.Bitmap;
import android.provider.MediaStore;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.Features2d;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.Video;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;


public class Alignment {




    public static Mat alignImages(Bitmap A, Bitmap B,MatOfPoint corners) {
        MatOfPoint2f newcorners = new MatOfPoint2f();
        MatOfPoint2f prevcorners = new MatOfPoint2f();
        MatOfPoint2f prevcorners1 = new MatOfPoint2f();

    MatOfFloat err = new MatOfFloat();
    MatOfByte status = new MatOfByte();

    Mat matA = new Mat(A.getHeight(), A.getWidth(), CvType.CV_8UC1);
    Mat matB = new Mat(B.getHeight(), B.getWidth(), CvType.CV_8UC1);

        Utils.bitmapToMat(A, matA);
        Utils.bitmapToMat(B, matB);
        Imgproc.cvtColor(matA, matA, Imgproc.COLOR_RGB2GRAY);
        Imgproc.cvtColor(matB, matB, Imgproc.COLOR_RGB2GRAY);

try{


        corners.convertTo(prevcorners, CvType.CV_32FC2);

        Video.calcOpticalFlowPyrLK(matA, matB, prevcorners, newcorners, status, err);
        Video.calcOpticalFlowPyrLK(matB, matA, newcorners, prevcorners1, status, err);
        int col= prevcorners.cols();
        int righe= prevcorners.rows();


        Log.d("col:",Integer.toString(col));
        Log.d("righe:",Integer.toString(righe));

        Mat homography = Calib3d.findHomography( newcorners,prevcorners,Calib3d.LMEDS,5);

        matA=null;
        matB=null;
        System.gc();
        Mat matC=new Mat(A.getHeight(), A.getWidth(), CvType. CV_64FC3);
        Utils.bitmapToMat(B, matC);


        Imgproc.warpPerspective(matC,matC,homography,new Size(matC.cols(), matC.rows()));


        System.gc();
        return matC;

    }
catch (Exception e){
        return null;
    }
}






    public static MatOfPoint TrackFeatures(Bitmap A) {
        double qualityLevel = 0.3;
        double minDistance = 7;
        int blockSize = 7;
        boolean useHarrisDetector = false;
        double k = 0.06;
        int cornerCount = 500;
        MatOfPoint corners = new MatOfPoint();

        Mat matA = new Mat(A.getHeight(), A.getWidth(), CvType.CV_8UC1);
        Utils.bitmapToMat(A, matA);
        Imgproc.cvtColor(matA, matA, Imgproc.COLOR_RGB2GRAY);
        Imgproc.goodFeaturesToTrack(matA, corners, cornerCount, qualityLevel, minDistance, new Mat(), blockSize, useHarrisDetector, k);

        return corners;
    }



}

