package hn.uth.deteccionpose;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
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

import android.graphics.Bitmap;
import android.graphics.Canvas;

public class MainActivity extends AppCompatActivity {

    private PreviewView previewView;
    private ImageView imageDisplay;
    private TextView txtStatus, txtData;
    private PoseDetector poseDetector;
    private Executor executor = Executors.newSingleThreadExecutor();
    private ImageView canvasOverlay;
    private boolean showPoints = false;
    private PoseGraphic poseGraphic = new PoseGraphic();

    // Variables para suavizado (Filtro)
    private float smoothDiffY = 0;
    private final float alpha = 0.4f; // Factor de suavizado (0.1 a 0.3 es ideal)

    private ProcessCameraProvider cameraProvider;

    // Para la Galería
    private final androidx.activity.result.ActivityResultLauncher<String> mGetContent = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) { processStaticImage(uri); }
            });

    // Para Tomar Foto
    private final androidx.activity.result.ActivityResultLauncher<Void> mTakePicture = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.TakePicturePreview(),
            bitmap -> {
                if (bitmap != null) { processBitmap(bitmap); }
            });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializar vistas
        previewView = findViewById(R.id.previewView);
        imageDisplay = findViewById(R.id.imageDisplay);
        txtStatus = findViewById(R.id.txtStatus);
        txtData = findViewById(R.id.txtData);
        canvasOverlay = findViewById(R.id.canvasOverlay);
        Button btnTogglePoints = findViewById(R.id.btnTogglePoints);

        // 1. Configurar ML Kit Pose Detector
        PoseDetectorOptions options = new PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build();
        poseDetector = PoseDetection.getClient(options);

        btnTogglePoints.setOnClickListener(v -> {
            showPoints = !showPoints;
            btnTogglePoints.setText(showPoints ? "Puntos ON" : "Puntos OFF");
            if (!showPoints) canvasOverlay.setImageDrawable(null);
        });

        // Botón En Vivo
        findViewById(R.id.btnLive).setOnClickListener(v -> {
            imageDisplay.setVisibility(View.GONE);
            previewView.setVisibility(View.VISIBLE);
            startCamera(); // Esto vuelve a encender el flujo
        });

        // Botón Tomar Foto
        findViewById(R.id.btnCamera).setOnClickListener(v -> mTakePicture.launch(null));

        // Botón Subir Foto de Galería
        findViewById(R.id.btnGallery).setOnClickListener(v -> mGetContent.launch("image/*"));
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
                // Guardamos la instancia globalmente
                cameraProvider = cameraProviderFuture.get();

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
    private AnalizadorPosturasAvanzado analizador = new AnalizadorPosturasAvanzado();

    private void analyzePose(Pose pose) {

        if (pose.getAllPoseLandmarks().isEmpty()) {
            runOnUiThread(() -> {
                txtStatus.setText("No se detecta a nadie");
                canvasOverlay.setImageDrawable(null);
            });
            return;
        }

        if (showPoints) {
            drawPoseOnCanvas(pose);
        }

        boolean malaPostura = analizador.malaPostura(pose);

        runOnUiThread(() -> {

            txtData.setText("Puntos detectados: " + pose.getAllPoseLandmarks().size());

            if (!malaPostura) {
                txtStatus.setText("¡Postura Correcta! 😊");
                txtStatus.setTextColor(Color.GREEN);
            } else {
                txtStatus.setText("Corrige tu postura ⚠️");
                txtStatus.setTextColor(Color.RED);
            }
        });
    }

    private void drawPoseOnCanvas(Pose pose) {
        if (canvasOverlay.getWidth() == 0 || canvasOverlay.getHeight() == 0) return;

        Bitmap bitmap = Bitmap.createBitmap(canvasOverlay.getWidth(), canvasOverlay.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // IMPORTANTE: ML Kit en modo STREAM suele procesar a 480x640.
        // Para que los puntos sean exactos, usamos las dimensiones de la vista.
        // Si los puntos se mueven al revés, invertimos el eje X en el PoseGraphic.
        poseGraphic.draw(canvas, pose, canvasOverlay.getWidth(), canvasOverlay.getHeight(), true);

        runOnUiThread(() -> canvasOverlay.setImageBitmap(bitmap));
    }

    private void processStaticImage(android.net.Uri uri) {
        try {
            android.graphics.ImageDecoder.Source source = android.graphics.ImageDecoder.createSource(this.getContentResolver(), uri);
            Bitmap bitmap = android.graphics.ImageDecoder.decodeBitmap(source);
            processBitmap(bitmap);
        } catch (Exception e) {
            Toast.makeText(this, "Error al cargar imagen", Toast.LENGTH_SHORT).show();
        }
    }

    private void processBitmap(Bitmap bitmap) {
        stopCamera(); // Detiene el flujo de video

        // Preparar UI
        previewView.setVisibility(View.GONE);
        imageDisplay.setVisibility(View.VISIBLE);
        imageDisplay.setImageBitmap(bitmap);
        canvasOverlay.setImageDrawable(null); // Limpiar puntos viejos

        // Procesar con ML Kit
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        poseDetector.process(image)
                .addOnSuccessListener(pose -> {
                    analyzePose(pose); // Reutilizamos tu lógica de análisis
                    if (showPoints) {
                        // En fotos estáticas (galería/cámara) no invertimos el eje X (false)
                        drawPoseOnCanvasStatic(pose);
                    }
                });
    }

    // Metodo especial para dibujar en fotos fijas sin efecto espejo
    private void drawPoseOnCanvasStatic(Pose pose) {
        Bitmap bitmap = Bitmap.createBitmap(canvasOverlay.getWidth(), canvasOverlay.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        poseGraphic.draw(canvas, pose, canvasOverlay.getWidth(), canvasOverlay.getHeight(), false);
        runOnUiThread(() -> canvasOverlay.setImageBitmap(bitmap));
    }

    private void stopCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll(); // Esto detiene el flujo de la cámara y el análisis
        }
    }


}