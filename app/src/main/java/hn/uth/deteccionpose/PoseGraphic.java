package hn.uth.deteccionpose;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;

public class PoseGraphic {

    private final Paint dotPaint;

    private final Paint linePaint;

    public PoseGraphic() {
        dotPaint = new Paint();
        dotPaint.setColor(Color.CYAN);
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setStrokeWidth(10f);

        linePaint = new Paint();
        linePaint.setColor(Color.GREEN);
        linePaint.setStrokeWidth(6f);

    }

    private void drawLine(Canvas canvas, PoseLandmark p1, PoseLandmark p2,
                          float scaleX, float scaleY, int viewWidth, boolean mirror) {

        if (p1 == null || p2 == null) return;
        if (p1.getInFrameLikelihood() < 0.7 || p2.getInFrameLikelihood() < 0.7) return;

        float x1 = p1.getPosition().x * scaleX;
        float y1 = p1.getPosition().y * scaleY;
        float x2 = p2.getPosition().x * scaleX;
        float y2 = p2.getPosition().y * scaleY;

        if (mirror) {
            x1 = viewWidth - x1;
            x2 = viewWidth - x2;
        }

        canvas.drawLine(x1, y1, x2, y2, linePaint);
    }



    public void draw(Canvas canvas, Pose pose, int viewWidth, int viewHeight, boolean mirror){

        // Tamaño REAL del input de ML Kit (aprox en STREAM_MODE)
        float imageWidth = 480f;
        float imageHeight = 640f;

        float scaleX = viewWidth / imageWidth;
        float scaleY = viewHeight / imageHeight;

        drawLine(canvas,
                pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER),
                pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER),
                scaleX, scaleY, viewWidth, mirror);

        drawLine(canvas,
                pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER),
                pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW),
                scaleX, scaleY, viewWidth, mirror);

        drawLine(canvas,
                pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW),
                pose.getPoseLandmark(PoseLandmark.LEFT_WRIST),
                scaleX, scaleY, viewWidth, mirror);

        drawLine(canvas,
                pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER),
                pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW),
                scaleX, scaleY, viewWidth, mirror);

        drawLine(canvas,
                pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW),
                pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST),
                scaleX, scaleY, viewWidth, mirror);

        drawLine(canvas,
                pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER),
                pose.getPoseLandmark(PoseLandmark.LEFT_HIP),
                scaleX, scaleY, viewWidth, mirror);

        drawLine(canvas,
                pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER),
                pose.getPoseLandmark(PoseLandmark.RIGHT_HIP),
                scaleX, scaleY, viewWidth, mirror);

        drawLine(canvas,
                pose.getPoseLandmark(PoseLandmark.LEFT_HIP),
                pose.getPoseLandmark(PoseLandmark.RIGHT_HIP),
                scaleX, scaleY, viewWidth, mirror);

        drawLine(canvas,
                pose.getPoseLandmark(PoseLandmark.LEFT_HIP),
                pose.getPoseLandmark(PoseLandmark.LEFT_KNEE),
                scaleX, scaleY, viewWidth, mirror);

        drawLine(canvas,
                pose.getPoseLandmark(PoseLandmark.LEFT_KNEE),
                pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE),
                scaleX, scaleY, viewWidth, mirror);

        drawLine(canvas,
                pose.getPoseLandmark(PoseLandmark.RIGHT_HIP),
                pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE),
                scaleX, scaleY, viewWidth, mirror);

        drawLine(canvas,
                pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE),
                pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE),
                scaleX, scaleY, viewWidth, mirror);

        for (PoseLandmark lm : pose.getAllPoseLandmarks()) {

            if (lm.getInFrameLikelihood() < 0.7) continue;

            float x = lm.getPosition().x * scaleX;
            float y = lm.getPosition().y * scaleY;

            if (mirror) {
                x = viewWidth - x;
            }

            canvas.drawCircle(x, y, 10f, dotPaint);
        }
    }
}