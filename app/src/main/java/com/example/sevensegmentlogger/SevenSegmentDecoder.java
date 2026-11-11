package com.example.sevensegmentlogger;

import android.graphics.Bitmap;
import android.graphics.RectF;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Decoder for dark reflective LCDs with moving decimal points.
 * First finds horizontal top strokes to calibrate digit columns, then reads
 * each segment relative to the local LCD background, not a global threshold.
 * This is a rule-based experimental reader; validate against the instrument.
 */
public final class SevenSegmentDecoder {
    private SevenSegmentDecoder() {}
    public static final class Settings {
        public int digits = 4;
        public int threshold = 125; // 125 default; lower is more sensitive
        public boolean litIsBright = false;
        public boolean decimalEnabled = true;
        public boolean fixedDecimalAfterFirst = false;
    }
    private static final int[] PATTERNS = {
        0b0111111,0b0000110,0b1011011,0b1001111,0b1100110,
        0b1101101,0b1111101,0b0000111,0b1111111,0b1101111
    };
    // [left,top,right,bottom] in individual digit bounding boxes.
    private static final float[][] SEG = {
        {.27f,.075f,.72f,.155f}, // a: upper horizontal
        {.79f,.19f,.91f,.38f},   // b: upper-right
        {.79f,.62f,.91f,.81f},   // c: lower-right
        {.27f,.845f,.72f,.925f},// d: lower horizontal
        {.09f,.62f,.22f,.81f},   // e: lower-left
        {.09f,.19f,.22f,.38f},   // f: upper-left
        {.27f,.46f,.72f,.54f}   // g: central horizontal
    };
    private static float[] previousLefts;
    private static float previousWidth;
    private static int previousDigits;

    public static synchronized String decode(Bitmap bmp, RectF roi, Settings s) {
        if(bmp==null||roi==null||s.digits<1||s.digits>8) return null;
        int xL=clamp(Math.round(roi.left*bmp.getWidth()),0,bmp.getWidth()-1);
        int xR=clamp(Math.round(roi.right*bmp.getWidth()),1,bmp.getWidth());
        int yT=clamp(Math.round(roi.top*bmp.getHeight()),0,bmp.getHeight()-1);
        int yB=clamp(Math.round(roi.bottom*bmp.getHeight()),1,bmp.getHeight());
        int w=xR-xL,h=yB-yT;
        if(w<s.digits*19||h<35) return null;

        // The horizontal top bars are considerably darker than the digit's
        // inner background at 25-33% height. This cancels uneven LCD glare.
        float[] response=new float[w];
        for(int x=0;x<w;x++) {
            float top=mean(bmp,xL+x,yT+.085f*h,xL+x+1,yT+.16f*h);
            float inside=mean(bmp,xL+x,yT+.25f*h,xL+x+1,yT+.33f*h);
            response[x]=s.litIsBright?top-inside:inside-top;
        }
        List<float[]> runs=new ArrayList<>();
        float runThreshold=12f;
        int start=-1;
        for(int i=0;i<=w;i++) {
            boolean active=i<w&&response[i]>runThreshold;
            if(active&&start<0) start=i;
            if(!active&&start>=0) {
                if(i-start>=Math.max(5,w/(s.digits*12))) {
                    float sum=0;for(int k=start;k<i;k++)sum+=response[k];
                    if(sum/(i-start)>19f) runs.add(new float[]{xL+start,xL+i});
                }
                start=-1;
            }
        }
        // Choose calibrated anchors only when all digit top strokes appear;
        // for digits without a top segment, retain the earlier calibration.
        float[] lefts=new float[s.digits];
        float digitW;
        if(runs.size()==s.digits) {
            float[] lengths=new float[s.digits];
            for(int i=0;i<s.digits;i++) lengths[i]=runs.get(i)[1]-runs.get(i)[0];
            Arrays.sort(lengths);
            digitW=clampF(lengths[lengths.length/2]/.45f,w/(float)(s.digits+2),w/(float)s.digits);
            for(int i=0;i<s.digits;i++) lefts[i]=runs.get(i)[0]-.27f*digitW;
            previousLefts=lefts.clone();previousWidth=digitW;previousDigits=s.digits;
        } else if(previousLefts!=null&&previousDigits==s.digits) {
            lefts=previousLefts.clone();digitW=previousWidth;
            // If camera/ROI changes appreciably, all-bar recalibration is necessary.
            if(lefts[0]<xL-12||lefts[lefts.length-1]>xR+12) {
                previousLefts=null;
                return null;
            }
        } else {
            // Before full calibration, reserve a little width for a decimal gap.
            float margin=w*.02f, gap=w*.035f;
            digitW=(w-2*margin-gap*(s.digits-1))/(float)s.digits;
            for(int d=0;d<s.digits;d++)lefts[d]=xL+margin+d*(digitW+gap);
        }
        float[] dotScores=new float[Math.max(0,s.digits-1)];
        StringBuilder digits=new StringBuilder();
        for(int d=0;d<s.digits;d++) {
            float x=lefts[d];
            // Estimate local background from the brightest areas INSIDE each
            // digit. Sampling the whole display with one threshold caused 8s.
            float bg=percentileRegion(bmp,x+digitW*.19f,yT+h*.19f,
                    x+digitW*.77f,yT+h*.79f,s.litIsBright?.15f:.85f);
            float[] contrast=new float[7];float strongest=0;
            for(int j=0;j<7;j++){
                float[] r=SEG[j];
                float v=medianRegion(bmp,x+r[0]*digitW,yT+r[1]*h,
                        x+r[2]*digitW,yT+r[3]*h);
                contrast[j]=Math.max(0,s.litIsBright?v-bg:bg-v);
                strongest=Math.max(strongest,contrast[j]);
            }
            if(strongest<17) return null;
            float divisor=Math.max(43f,strongest*.80f);
            float[] strength=new float[7];
            for(int j=0;j<7;j++)strength[j]=clampF(contrast[j]/divisor,0f,1f);
            float best=Float.MAX_VALUE,second=Float.MAX_VALUE;int chosen=-1;
            for(int k=0;k<10;k++){
                float loss=0;
                for(int j=0;j<7;j++){
                    float aim=(PATTERNS[k]&(1<<j))!=0?.86f:0f;
                    float err=strength[j]-aim;
                    loss+=err*err;
                }
                if(loss<best){second=best;best=loss;chosen=k;}
                else if(loss<second)second=loss;
            }
            if(best>1.65f||second-best<.035f) return null;
            digits.append(chosen);
        }
        if(!s.decimalEnabled)return digits.toString();
        if(s.fixedDecimalAfterFirst)return digits.insert(1,'.').toString();

        // Detect a distinct small dark dot in the gaps between the digits.
        // Compare low gap intensity to its own upper-region baseline to cancel
        // left/right lighting gradients. A decimal can move between digits.
        int bestDot=-1;float bestDotScore=0,secondDotScore=0;
        for(int d=0;d<s.digits-1;d++) {
            float a=lefts[d]+digitW*.96f;
            float b=lefts[d+1]+digitW*.12f;
            if(b-a<3) { dotScores[d]=0;continue; }
            float baseline=medianRegion(bmp,a,yT+.30f*h,b,yT+.44f*h);
            float dot=medianRegion(bmp,a,yT+.77f*h,b,yT+.92f*h);
            float score=s.litIsBright?dot-baseline:baseline-dot;
            dotScores[d]=score;
            if(score>bestDotScore){secondDotScore=bestDotScore;bestDotScore=score;bestDot=d;}
            else if(score>secondDotScore)secondDotScore=score;
        }
        // Ambiguous decimal is rejected, rather than silently recording an
        // incorrect order of magnitude.
        float minimum=18f+(s.threshold-125)*.10f;
        if(bestDotScore>minimum && secondDotScore>minimum*.8f
                && bestDotScore-secondDotScore<10f)return null;
        if(bestDot>=0&&bestDotScore>minimum)digits.insert(bestDot+1,'.');
        return digits.toString();
    }
    private static float mean(Bitmap b,float x0,float y0,float x1,float y1){
        int xa=clamp((int)Math.floor(x0),0,b.getWidth()-1),xb=clamp((int)Math.ceil(x1),xa+1,b.getWidth());
        int ya=clamp((int)Math.floor(y0),0,b.getHeight()-1),yb=clamp((int)Math.ceil(y1),ya+1,b.getHeight());
        long sum=0;int n=0;
        for(int y=ya;y<yb;y++)for(int x=xa;x<xb;x++){sum+=gray(b.getPixel(x,y));n++;}
        return n>0?sum/(float)n:0;
    }
    private static float medianRegion(Bitmap b,float x0,float y0,float x1,float y1){return percentileRegion(b,x0,y0,x1,y1,.50f);}
    private static float percentileRegion(Bitmap b,float x0,float y0,float x1,float y1,float p){
        int xa=clamp((int)Math.floor(x0),0,b.getWidth()-1),xb=clamp((int)Math.ceil(x1),xa+1,b.getWidth());
        int ya=clamp((int)Math.floor(y0),0,b.getHeight()-1),yb=clamp((int)Math.ceil(y1),ya+1,b.getHeight());
        int[] hist=new int[256];int n=0;
        for(int y=ya;y<yb;y+=2)for(int x=xa;x<xb;x+=2){hist[gray(b.getPixel(x,y))]++;n++;}
        int target=Math.max(1,(int)Math.ceil(n*p)),total=0;
        for(int i=0;i<256;i++){total+=hist[i];if(total>=target)return i;}
        return 255;
    }
    private static int gray(int c){return(77*((c>>16)&255)+150*((c>>8)&255)+29*(c&255))>>8;}
    private static int clamp(int x,int a,int b){return Math.max(a,Math.min(x,b));}
    private static float clampF(float x,float a,float b){return Math.max(a,Math.min(x,b));}
}
