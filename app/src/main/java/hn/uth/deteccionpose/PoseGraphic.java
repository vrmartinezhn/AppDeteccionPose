package hn.uth.deteccionpose;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;

public class PoseGraphic {
    private final Paint dotPaint;

    public PoseGraphic() {
        dotPaint = new Paint();
        dotPaint.setColor(Color.CYAN);
        dotPaint.setStrokeWidth(10.0f);
    }

    // AHORA RECIBE 5 PARÁMETROS
    public void draw(Canvas canvas, Pose pose, int viewWidth, int viewHeight, boolean isFrontCamera) {
        // ML Kit en modo STREAM usualmente analiza a una resolución de 480x640
        float scaleX = (float) viewWidth / 480f;
        float scaleY = (float) viewHeight / 640f;

        for (PoseLandmark landmark : pose.getAllPoseLandmarks()) {
            float x = landmark.getPosition().x * scaleX;
            float y = landmark.getPosition().y * scaleY;

            // SI ES CÁMARA FRONTAL, INVERTIMOS EL EJE X (Efecto Espejo)
            if (isFrontCamera) {
                x = viewWidth - x;
            }

            canvas.drawCircle(x, y, 10.0f, dotPaint);
        }
    }
}