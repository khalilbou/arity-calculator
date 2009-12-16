// Copyright (C) 2009 Mihai Preda

package calculator;

import android.view.View;
import android.view.MotionEvent;
import android.view.VelocityTracker;

import android.widget.TextView;
import android.widget.EditText;
import android.widget.Scroller;

import android.content.Context;
import android.text.Editable;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.Region;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;

import android.util.AttributeSet;

import android.util.Log;
import arity.calculator.R;

import org.javia.arity.*;

public class GraphView extends View implements Grapher {
    private int width, height;
    private Matrix matrix = new Matrix();
    private Paint paint = new Paint(), textPaint = new Paint(), fillPaint = new Paint();
    private Function function;
    private Data next = new Data(), graph = new Data(), endGraph = new Data();
    private float gwidth = 8;
    private float currentX, currentY;
    private float lastMinX;
    private boolean isFullScreen;
    private VelocityTracker velocityTracker;
    private Scroller scroller;
    private boolean active;
    private float lastTouchX, lastTouchY;
    private float points[];
    private float boundMinY, boundMaxY;

    /*
    private static final int
        COL_AXIS = 0xffa0a0a0,
        COL_GRID = 0xff202020,
        COL_TEXT = 0xffffffff,
        COL_GRAPH = 0xff00ff00;
    */

    private static final int
        COL_AXIS = 0xff008000,
        COL_GRID = 0xff004000,
        COL_TEXT = 0xff00ff00,
        COL_GRAPH = 0xffffffff;

    private static final int
        COL_ZOOM = 0x40ffffff,
        COL_ZOOM_TEXT1 = 0xd0ffffff,
        COL_ZOOM_TEXT2 = 0x30ffffff;

    public GraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        isFullScreen = false;
        init(context);
    }

    public GraphView(Context context) {
        super(context);
        isFullScreen = true;
        init(context);
    }

    private void init(Context context) {
        scroller = new Scroller(context);
        paint.setAntiAlias(false);
        textPaint.setAntiAlias(true);
    }

    public void setFunction(Function f) {
        this.function = f;
        graph.clear();
        invalidate();
    }

    public void onPause() {
    }

    public void onResume() {
    }

    protected void onSizeChanged(int w, int h, int ow, int oh) {
        width = w;
        height = h;
        graph.clear();
        points = new float[w+w];
    }

    protected void onDraw(Canvas canvas) {
        if (function == null) {
            return;
        }
        if (scroller.computeScrollOffset()) {
            final float scale = gwidth / width;
            currentX = scroller.getCurrX() * scale;
            currentY = scroller.getCurrY() * scale;
            if (!scroller.isFinished()) {
                invalidate();
            }
        }
        drawGraph(canvas);
    }

    private static final float 
        NEG_INF = Float.NEGATIVE_INFINITY, 
        POS_INF = Float.POSITIVE_INFINITY;

    private float eval(float x) {
        float v = (float) function.eval(x);
        if (v == NEG_INF) {
            return -10000f;
        }
        if (v == POS_INF) {
            return 10000f;
        }
        return v;
    }

    /*
    private void computeGraph2(float minX) {
        float step = gwidth / width;
        int end = width + width;
        float x = minX;
        for (int i = 0; i < end; x += step, i+=2) {
            points[i] = x;
            points[i+1] = (float) function.eval(x);
        }
    }
    */

    private Data computeGraph(float minX, float maxX, float minY, float maxY) {
        long t1 = System.currentTimeMillis();
        final float scale = width / gwidth;
        final float maxStep = Math.min(15.8976f / scale, 1.373f);
        final float minStep = .3f / scale;
        // Calculator.log("step min " + minStep + " max " + maxStep);
        final float ythresh = 2/scale;
        // final float ythresh2 = 1.5f * ythresh;
        next.clear();
        endGraph.clear();
        if (!graph.empty()) {
            // Calculator.log("last " + lastMinX + " min " + minX);
            if (minX >= lastMinX) {
                graph.eraseBefore(minX);
            } else {
                graph.eraseAfter(maxX);
                maxX = graph.firstX();
                Data save = endGraph;
                endGraph = graph;
                graph = save;
            }
        }
        lastMinX = minX;
        if (graph.empty()) {
            graph.push(minX, eval(minX));
        }
        float leftX = graph.topX();
        float leftY = graph.topY();
        float rightX = 0, rightY = 0;
        boolean advance = false;
        int nEval = 1;
        while (true) {
            if (advance) {
                leftX = rightX;
                leftY = rightY;
                next.pop();
            }
            advance = true;
            if (next.empty()) {
                float x = leftX + maxStep;
                next.push(x, eval(x));
                ++nEval;
            }
            rightX = next.topX();
            rightY = next.topY();
            if (leftX > maxX) {
                break;
            }
            if (leftY != leftY && rightY != rightY) { // NaN
                continue;
            }
            /*
            if ((leftY < minY && rightY > maxY) || 
                (leftY > maxY && rightY < minY)) {
                graph.push(rightX, Float.NaN);
                graph.push(rightX, rightY);
                continue;
            }
            */

            float span = rightX - leftX;
            float middleX = (leftX + rightX) / 2;
            float middleY = eval(middleX);
            ++nEval;
            boolean isKinky = (middleY < leftY && middleY < rightY) || (leftY < middleY && rightY < middleY);
            if (span < minStep) {
                // Calculator.log("minStep");
                if (isKinky) {
                    graph.push(rightX, Float.NaN);
                }
                graph.push(rightX, rightY);
                continue;
            }
            if (isKinky && ((leftY < minY && rightY > maxY) || (leftY > maxY && rightY < minY))) {
                graph.push(rightX, Float.NaN);
                graph.push(rightX, rightY);
                Calculator.log("+-inf");
                continue;
            }

            /*
            if ((leftY < minY && middleY > maxY) ||
                (leftY > maxY && middleY < minY)) {
                graph.push(middleX, Float.NaN);
                graph.push(middleX, middleY);
                leftX = middleX;
                leftY = middleY;
                advance = false;
                continue;
            }
            */

            float diff = Math.abs(leftY + rightY - middleY - middleY);
            Calculator.log("" + span + ' ' + leftY + ' ' + middleY + ' ' + rightY + ' ' + diff + ' ' + ythresh);
            if (diff < ythresh) {
                // Calculator.log("+ ythresh");
                graph.push(rightX, rightY);
            } else {                
                next.push(middleX, middleY);
                advance = false;
            }
        }
        if (!endGraph.empty()) {
            graph.append(endGraph);
        }
        long t2 = System.currentTimeMillis();
        Calculator.log("graph points " + graph.size + " evals " + nEval + " time " + (t2-t1));
        return graph;
    }
    
    private static Path path = new Path();
    private Path graphToPath(Data graph) {
        boolean first = true;
        int size = graph.size;
        float[] xs = graph.xs;
        float[] ys = graph.ys;
        path.rewind();
        for (int i = 0; i < size; ++i) {
            float y = ys[i];
            float x = xs[i];
            // Calculator.log("path " + x + ' ' + y);
            if (y == y) { // !NaN
                if (first) {
                    path.moveTo(x, y);
                    first = false;
                } else {
                    path.lineTo(x, y);
                }
            } else {
                first = true;
            }
        }
        return path;
    }

    private static final float NTICKS = 15;
    private static float stepFactor(float w) {
        float f = 1;
        while (w / f > NTICKS) {
            f *= 10;
        }
        while (w / f < NTICKS / 10) {
            f /= 10;
        }
        float r = w / f;
        if (r < NTICKS / 5) {
            return f / 5;
        } else if (r < NTICKS / 2) {
            return f / 2;
        } else {
            return f;
        }
    }

    private static StringBuilder b = new StringBuilder();
    private static char[] buf = new char[20];
    private static StringBuilder format(float fv) {
        int pos = 0;
        boolean addDot = false;
        int v = Math.round(fv * 100);
        boolean isNeg = v < 0;
        v = isNeg ? -v : v;
        for (int i = 0; i < 2; ++i) {
            int digit = v % 10;
            v /= 10;
            if (digit != 0 || addDot) {
                buf[pos++] = (char) ('0' + digit);
                addDot = true;
            }
        }
        if (addDot) {
            buf[pos++] = '.';
        }
        if (v == 0) {
            buf[pos++] = '0';
        }
        while (v != 0) {
            buf[pos++] = (char) ('0' + (v % 10));
            v /= 10;
        }
        if (isNeg) {
            buf[pos++] = '-';
        }
        b.setLength(0);
        b.append(buf, 0, pos);
        b.reverse();
        return b;
    }

    private void drawGraph(Canvas canvas) {
        long t1 = System.currentTimeMillis();
        float minX = currentX - gwidth/2;
        float maxX = minX + gwidth;
        float ywidth = gwidth * height / width;
        float minY = currentY - ywidth/2;
        float maxY = minY + ywidth;
        if (minY < boundMinY || maxY > boundMaxY) {
            float halfw = ywidth/2;
            boundMinY = minY - halfw;
            boundMaxY = maxY + halfw;
            graph.clear();
            Calculator.log("graph reset");
        }

        canvas.drawColor(0xff000000);
                
        paint.setStrokeWidth(0);
        paint.setAntiAlias(false);
        paint.setStyle(Paint.Style.STROKE);

        final float h2 = height/2f;
        final float scale = width / gwidth;
        
        float x0 = -minX * scale;
        boolean drawYAxis = true;
        if (x0 < 25) {
            x0 = 25;
            // drawYAxis = false;
        } else if (x0 > width - 3) {
            x0 = width - 3;
            // drawYAxis = false;
        }
        float y0 = maxY * scale;
        if (y0 < 3) {
            y0 = 3;
        } else if (y0 > height - 15) {
            y0 = height - 15;
        }

        final float tickSize = 3;
        final float y2 = y0 + tickSize;
        paint.setColor(COL_GRID);
        float step = stepFactor(gwidth);
        // Calculator.log("width " + gwidth + " step " + step);
        float v = ((int) (minX/step)) * step;
        textPaint.setColor(COL_TEXT);
        textPaint.setTextSize(12);
        textPaint.setTextAlign(Paint.Align.CENTER);
        float stepScale = step * scale;
        for (float x = (v - minX) * scale; x <= width; x += stepScale, v += step) {
            canvas.drawLine(x, 0, x, height, paint);
            if (!(-.001f < v && v < .001f)) {
                StringBuilder b = format(v);
                canvas.drawText(b, 0, b.length(), x, y2+10, textPaint);
            }
        }
        
        final float x1 = x0 - tickSize;
        v = ((int) (minY/step)) * step;
        textPaint.setTextAlign(Paint.Align.RIGHT);
        for (float y = height - (v - minY) * scale; y >= 0; y -= stepScale, v += step) {
            canvas.drawLine(0, y, width, y, paint);
            if (!(-.001f < v && v < .001f)) {
                StringBuilder b = format(v);
                canvas.drawText(b, 0, b.length(), x1, y+4, textPaint);
            }
        }

        paint.setColor(COL_AXIS);
        if (drawYAxis) {
            canvas.drawLine(x0, 0, x0, height, paint);
        }
        canvas.drawLine(0, y0, width, y0, paint);
        long t3 = System.currentTimeMillis();
        
        matrix.reset();
        matrix.preTranslate(-currentX, -currentY);
        matrix.postScale(scale, -scale);
        matrix.postTranslate(width/2, height/2);

        paint.setColor(COL_GRAPH);
        paint.setStrokeWidth(0);
        paint.setAntiAlias(false);

        Data graph = computeGraph(minX, maxX, boundMinY, boundMaxY);
        Path path = graphToPath(graph);
        long t5 = System.currentTimeMillis();
        path.transform(matrix);
        long t4 = System.currentTimeMillis();
        canvas.drawPath(path, paint);

        /*
        computeGraph2(minX);
        matrix.mapPoints(points);
        paint.setAntiAlias(false);
        canvas.drawPoints(points, paint);
        */
        
        if (isFullScreen) {
            fillPaint.setColor(COL_ZOOM);
            canvas.drawRect(width-130, height-55, width-30, height-10, fillPaint);
            textPaint.setTextSize(22);
            textPaint.setTextAlign(Paint.Align.CENTER);
            
            textPaint.setColor(canZoomOut() ? COL_ZOOM_TEXT1 : COL_ZOOM_TEXT2);
            canvas.drawText("\u2212", width-105, height-22, textPaint);
            
            textPaint.setColor(canZoomIn() ? COL_ZOOM_TEXT1 : COL_ZOOM_TEXT2);
            canvas.drawText("+", width-55, height-22, textPaint);
        }
        long t2 = System.currentTimeMillis();
        // Calculator.log("total draw time " + (t2 - t1) + ' ' + (t3-t1) + ' ' + (t5 - t3) + ' ' + (t4 - t5) + ' ' + (t2-t4));
    }

    private boolean canZoomIn() {
        return gwidth > 1f;
    }

    private boolean canZoomOut() {
        return gwidth < 50;
    }

    private void zoomOut() {
        if (canZoomOut()) {
            gwidth *= 2;
            invalidate();
            graph.clear();
            boundMinY = boundMaxY = 0;
        }
    }

    private void zoomIn() {
        if (canZoomIn()) {
            gwidth /= 2;
            invalidate();
            graph.clear();
            boundMinY = boundMaxY = 0;
        }
    }

    public boolean onTouchEvent(MotionEvent event) {
        if (!isFullScreen) {
            return super.onTouchEvent(event);
        }

        int action = event.getAction();
        float x = event.getX();
        float y = event.getY();
        if (!active && action != MotionEvent.ACTION_DOWN) {
            return true;
        }        
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            if (!scroller.isFinished()) {
                scroller.abortAnimation();
            }
            active = false;
            if (y > height-60) {
                if (x > width - 140 && x < width-80) {
                    zoomOut();
                    return true;
                } else if (x > width-80 && x < width-20) {
                    zoomIn();
                    return true;
                }
            }
            active = true;
            if (velocityTracker == null) {
                velocityTracker = VelocityTracker.obtain();
            }
            velocityTracker.addMovement(event);
            lastTouchX = x;
            lastTouchY = y;
            break;

        case MotionEvent.ACTION_MOVE:
            if (velocityTracker == null) {
                velocityTracker = VelocityTracker.obtain();
            }
            velocityTracker.addMovement(event);
            float deltaX = x - lastTouchX;
            float deltaY = y - lastTouchY;
            if (deltaX < -1 || deltaX > 1 || deltaY < -1 || deltaY > 1) {
                scroll(-deltaX, deltaY);
                lastTouchX = x;
                lastTouchY = y;
                invalidate();
            }
            break;

        case MotionEvent.ACTION_UP:
            velocityTracker.computeCurrentVelocity(1000);
            final float scale = width / gwidth;
            float sx = -velocityTracker.getXVelocity();
            float sy = velocityTracker.getYVelocity();
            final float asx = Math.abs(sx);
            final float asy = Math.abs(sy);
            if (asx < asy / 3) {
                sx = 0;
            } else if (asy < asx / 3) {
                sy = 0;
            }
            scroller.fling(Math.round(currentX * scale),
                           Math.round(currentY * scale),
                           Math.round(sx), Math.round(sy), -10000, 10000, -10000, 10000);
            invalidate();
            // no break

        case MotionEvent.ACTION_CANCEL:
            if (velocityTracker != null) {
                velocityTracker.recycle();
                velocityTracker = null;
            }
            break;
            
        default: 
            // ignore
        }
        return true;
    }

    private void scroll(float deltaX, float deltaY) {
        final float scale = gwidth / width;
        float dx = deltaX * scale;
        float dy = deltaY * scale;
        final float adx = Math.abs(dx);
        final float ady = Math.abs(dy);
        if (adx < ady / 3) {
            dx = 0;
        } else if (ady < adx / 3) {
            dy = 0;
        }
        currentX += dx;
        currentY += dy;
        invalidate();
    }
}
