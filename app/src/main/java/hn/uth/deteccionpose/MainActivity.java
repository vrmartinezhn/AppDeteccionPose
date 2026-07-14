package hn.uth.deteccionpose;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Actividad principal de la app.
 *
 * APIs que se usan aquí:
 * - CameraX (androidx.camera.*): maneja el ciclo de vida de la cámara sin que tengamos
 *   que lidiar manualmente con Camera2. Usamos 3 piezas: Proce2ssCameraProvider (obtiene
 *   y controla la cámara), Preview (muestra el video en pantalla) e ImageAnalysis
 *   (nos entrega cada frame para procesarlo,
 *   en este caso con ML Kit).
 * - ML Kit Pose Detection (com.google.mlkit.vision.pose.*): detecta los puntos del
 *   cuerpo (landmarks) en cada frame o imagen estática.
 * - Activity Result API (androidx.activity.result.*): forma moderna de pedir permisos
 *   y de lanzar actividades externas (cámara nativa, selector de galería) sin usar los
 *   métodos antiguos onActivityResult/requestPermissions, que son más fáciles de usar mal.
 */
public class MainActivity extends AppCompatActivity {

    // --- Vistas del layout ---
    private PreviewView previewView;      // Muestra el feed de la cámara en vivo
    private ImageView imageDisplay;       // Muestra una foto estática (galería o cámara)
    private ImageView canvasOverlay;      // Encima de la cámara/foto: dibuja los puntos del esqueleto
    private TextView txtStatus, txtData;

    // --- ML Kit ---
    private PoseDetector poseDetector;
    // Ejecuta el análisis de cada frame en un hilo aparte, para no congelar la UI
    private final Executor executor = Executors.newSingleThreadExecutor();

    // --- Estado ---
    private boolean showPoints = false;                 // controla si se dibuja el esqueleto
    private final PoseGraphic poseGraphic = new PoseGraphic();
    private final AnalizadorPosturasAvanzado analizador = new AnalizadorPosturasAvanzado();
    private ProcessCameraProvider cameraProvider;        // referencia para poder detener la cámara

    // ------------------------------------------------------------------
    // Launchers de Activity Result API (reemplazan a startActivityForResult)
    // ------------------------------------------------------------------

    // Pide permiso de cámara y, según la respuesta, arranca la cámara o avisa al usuario
    private final ActivityResultLauncher<String> mRequestCameraPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    startCamera();
                } else {
                    Toast.makeText(this,
                            "Se necesita el permiso de cámara para analizar la postura",
                            Toast.LENGTH_LONG).show();
                }
            });

    // Abre el selector de imágenes de la galería
    private final ActivityResultLauncher<String> mGetContent = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) processStaticImage(uri);
            });

    // Abre la cámara nativa para tomar una foto y la devuelve como Bitmap
    private final ActivityResultLauncher<Void> mTakePicture = registerForActivityResult(
            new ActivityResultContracts.TakePicturePreview(),
            bitmap -> {
                if (bitmap != null) processBitmap(bitmap);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Primero enlazamos las vistas (findViewById). Esto SIEMPRE va antes de
        //    cualquier código que pueda usarlas (como startCamera(), que necesita previewView).
        previewView = findViewById(R.id.previewView);
        imageDisplay = findViewById(R.id.imageDisplay);
        canvasOverlay = findViewById(R.id.canvasOverlay);
        txtStatus = findViewById(R.id.txtStatus);
        txtData = findViewById(R.id.txtData);
        Button btnTogglePoints = findViewById(R.id.btnTogglePoints);

        // 2. Configuramos ML Kit en modo STREAM (optimizado para video en vivo, frame a frame)
        PoseDetectorOptions options = new PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build();
        poseDetector = PoseDetection.getClient(options);

        // 3. Recién ahora pedimos permiso / arrancamos cámara, con las vistas ya listas
        checkPermissionsAndStartCamera();

        // 4. Listeners de los botones
        btnTogglePoints.setOnClickListener(v -> {
            showPoints = !showPoints;
            btnTogglePoints.setText(showPoints ? "Puntos ON" : "Puntos OFF");
            if (!showPoints) canvasOverlay.setImageDrawable(null);
        });

        findViewById(R.id.btnLive).setOnClickListener(v -> {
            imageDisplay.setVisibility(View.GONE);
            previewView.setVisibility(View.VISIBLE);
            startCamera();
        });

        findViewById(R.id.btnCamera).setOnClickListener(v -> mTakePicture.launch(null));

        findViewById(R.id.btnGallery).setOnClickListener(v -> mGetContent.launch("image/*"));
    }

    /**
     * Revisa si ya tenemos el permiso de cámara concedido.
     * Si sí, arranca directo. Si no, lanza el diálogo del sistema mediante el launcher
     * (mRequestCameraPermission), que ya trae su propio callback definido arriba.
     *
     * IMPORTANTE: para que el diálogo aparezca, el AndroidManifest.xml debe declarar:
     * <uses-permission android:name="android.permission.CAMERA" />
     * Si esa línea falta, Android deniega el permiso automáticamente sin mostrar nada.
     */
    private void checkPermissionsAndStartCamera() {
        int estado = ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA);
        android.util.Log.d("PermisoCamara", "Estado actual: " +
                (estado == android.content.pm.PackageManager.PERMISSION_GRANTED ? "CONCEDIDO" : "NO concedido"));

        if (estado == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            android.util.Log.d("PermisoCamara", "Lanzando diálogo...");
            mRequestCameraPermission.launch(android.Manifest.permission.CAMERA);
        }
    }


    /**
     * Arranca CameraX: obtiene el proveedor de cámara, configura la vista previa (Preview)
     * y el análisis de frames (ImageAnalysis), y los "ata" al ciclo de vida de la Activity
     * (bindToLifecycle) para que CameraX libere la cámara sola cuando la Activity se destruya.
     */
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        // Si llega un frame nuevo antes de terminar de procesar el anterior,
                        // descarta el viejo y se queda solo con el más reciente
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(executor, image -> {
                    @SuppressLint("UnsafeOptInUsageError")
                    InputImage inputImage = InputImage.fromMediaImage(
                            image.getImage(), image.getImageInfo().getRotationDegrees());

                    poseDetector.process(inputImage)
                            .addOnSuccessListener(this::analyzePose)
                            .addOnCompleteListener(task -> image.close()); // libera el frame siempre
                });

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /**
     * Se llama con cada resultado de ML Kit (ya sea de un frame de cámara o de una foto).
     * Actualiza el texto de estado y, si showPoints está activo, dibuja el esqueleto.
     */
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

        // Contamos solo los landmarks que el modelo está razonablemente seguro
        // que SÍ están dentro del cuadro (no estimados/adivinados)
        long puntosVisiblesReales = pose.getAllPoseLandmarks().stream()
                .filter(landmark -> landmark.getInFrameLikelihood() > 0.7f)
                .count();

        runOnUiThread(() -> {
            txtData.setText("Puntos visibles: " + puntosVisiblesReales + " / 33 totales");

            if (!malaPostura) {
                txtStatus.setText("¡Postura Correcta! 😊");
                txtStatus.setTextColor(Color.GREEN);
            } else {
                txtStatus.setText("Corrige tu postura ⚠️");
                txtStatus.setTextColor(Color.RED);
            }
        });
    }

    /** Dibuja el esqueleto para el feed en vivo (con espejo, porque es cámara frontal) */
    private void drawPoseOnCanvas(Pose pose) {
        if (canvasOverlay.getWidth() == 0 || canvasOverlay.getHeight() == 0) return;

        Bitmap bitmap = Bitmap.createBitmap(canvasOverlay.getWidth(), canvasOverlay.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        poseGraphic.draw(canvas, pose, canvasOverlay.getWidth(), canvasOverlay.getHeight(), 480f, 640f,true);
        runOnUiThread(() -> canvasOverlay.setImageBitmap(bitmap));
    }

    /** Dibuja el esqueleto para fotos estáticas (sin espejo) */
    private void drawPoseOnCanvasStatic(Pose pose, Bitmap originalBitmap) {

        if (canvasOverlay.getWidth() == 0 || canvasOverlay.getHeight() == 0 || originalBitmap == null) return;

        Bitmap bitmap = Bitmap.createBitmap(canvasOverlay.getWidth(), canvasOverlay.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        float imgWidth = originalBitmap.getWidth();
        float imgHeight = originalBitmap.getHeight();
        poseGraphic.draw(canvas, pose, canvasOverlay.getWidth(), canvasOverlay.getHeight(), imgWidth, imgHeight, false);
        runOnUiThread(() -> canvasOverlay.setImageBitmap(bitmap));
    }

    /** Decodifica la imagen elegida en la galería (Uri) a Bitmap y la procesa */
    private void processStaticImage(android.net.Uri uri) {
        try {
            android.graphics.ImageDecoder.Source source =
                    android.graphics.ImageDecoder.createSource(getContentResolver(), uri);
            Bitmap bitmap = android.graphics.ImageDecoder.decodeBitmap(source);
            processBitmap(bitmap);
        } catch (Exception e) {
            Toast.makeText(this, "Error al cargar imagen", Toast.LENGTH_SHORT).show();
        }
    }

    /** Punto común para procesar una foto (de cámara o galería): detiene el video y corre ML Kit */
    private void processBitmap(Bitmap bitmap) {
        stopCamera();

        previewView.setVisibility(View.GONE);
        imageDisplay.setVisibility(View.VISIBLE);
        imageDisplay.setImageBitmap(bitmap);
        canvasOverlay.setImageDrawable(null); // limpia puntos de una detección anterior

        InputImage image = InputImage.fromBitmap(bitmap, 0);
        poseDetector.process(image)
                .addOnSuccessListener(pose -> {
                    analyzePose(pose);
                    if (showPoints) {

                        drawPoseOnCanvasStatic(pose, bitmap);
                    }
                });
    }

    /** Libera la cámara. CameraX ya la libera solo al destruirse la Activity,
     *  pero la llamamos explícitamente al pasar a modo "foto" para apagar el feed. */
    private void stopCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }
}