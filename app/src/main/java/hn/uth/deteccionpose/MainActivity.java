package hn.uth.deteccionpose;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.PoseLandmark;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private PreviewView previewView;
    private ImageView imageDisplay;
    private TextView txtStatus, txtData;
    private PoseDetector poseDetector;
    private Executor executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializar vistas
        previewView = findViewById(R.id.previewView);
        imageDisplay = findViewById(R.id.imageDisplay);
        txtStatus = findViewById(R.id.txtStatus);
        txtData = findViewById(R.id.txtData);

        // 1. Configurar ML Kit Pose Detector
        PoseDetectorOptions options = new PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build();
        poseDetector = PoseDetection.getClient(options);

        // Botón En Vivo
        findViewById(R.id.btnLive).setOnClickListener(v -> {
            imageDisplay.setVisibility(View.GONE);
            previewView.setVisibility(View.VISIBLE);
            checkPermissionsAndStartCamera();
        });

        // Botones de Foto y Galería (Pendientes de implementar lógica de captura)
        findViewById(R.id.btnCamera).setOnClickListener(v -> Toast.makeText(this, "Función de foto", Toast.LENGTH_SHORT).show());
        findViewById(R.id.btnGallery).setOnClickListener(v -> Toast.makeText(this, "Función de galería", Toast.LENGTH_SHORT).show());
    }

    private void checkPermissionsAndStartCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 100);
        } else {
            startCamera();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(executor, image -> {
                    @SuppressLint("UnsafeOptInUsageError")
                    InputImage inputImage = InputImage.fromMediaImage(image.getImage(), image.getImageInfo().getRotationDegrees());

                    poseDetector.process(inputImage)
                            .addOnSuccessListener(this::analyzePose)
                            .addOnCompleteListener(task -> image.close());
                });

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzePose(Pose pose) {
        if (pose.getAllPoseLandmarks().isEmpty()) {
            runOnUiThread(() -> txtStatus.setText("No se detecta a nadie"));
            return;
        }

        // Obtener hombro y oreja para detectar postura "cusca"
        PoseLandmark leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
        PoseLandmark leftEar = pose.getPoseLandmark(PoseLandmark.LEFT_EAR);

        if (leftShoulder != null && leftEar != null) {
            // Calculamos la distancia vertical entre la oreja y el hombro
            float diffY = Math.abs(leftShoulder.getPosition().y - leftEar.getPosition().y);

            StringBuilder sb = new StringBuilder();
            sb.append("DATOS ML KIT:\n");
            sb.append("Hombro Y: ").append((int)leftShoulder.getPosition().y).append("\n");
            sb.append("Oreja Y: ").append((int)leftEar.getPosition().y).append("\n");
            sb.append("Diferencia: ").append((int)diffY);

            runOnUiThread(() -> {
                txtData.setText(sb.toString());
                // Si la oreja está muy cerca del hombro en el eje Y, significa que la cabeza está caída/hacia adelante
                if (diffY > 120) {
                    txtStatus.setText("¡Postura Correcta! :)");
                    txtStatus.setTextColor(Color.GREEN);
                } else {
                    txtStatus.setText("Por favor corrige tu postura :(");
                    txtStatus.setTextColor(Color.RED);
                }
            });
        }
    }
}