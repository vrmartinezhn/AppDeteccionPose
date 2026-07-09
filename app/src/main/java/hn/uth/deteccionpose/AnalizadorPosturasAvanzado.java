package hn.uth.deteccionpose;

import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;

public class AnalizadorPosturasAvanzado {

    private boolean valido(PoseLandmark lm){
        return lm != null && lm.getInFrameLikelihood() > 0.7;
    }

    // =========================
    // CUELLO ADELANTADO
    // =========================
    public boolean cuello(Pose pose){
        PoseLandmark nariz = pose.getPoseLandmark(PoseLandmark.NOSE);
        PoseLandmark hombroIzq = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
        PoseLandmark hombroDer = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);

        if (!valido(nariz) || (!valido(hombroIzq) && !valido(hombroDer)))
            return false;

        float hombroX, hombroY;

        if (valido(hombroIzq) && valido(hombroDer)) {
            hombroX = (hombroIzq.getPosition().x + hombroDer.getPosition().x) / 2f;
            hombroY = (hombroIzq.getPosition().y + hombroDer.getPosition().y) / 2f;
        } else {
            PoseLandmark ref = valido(hombroIzq) ? hombroIzq : hombroDer;
            hombroX = ref.getPosition().x;
            hombroY = ref.getPosition().y;
        }

        float dx = nariz.getPosition().x - hombroX;
        float dy = nariz.getPosition().y - hombroY;

        double angulo = Math.toDegrees(Math.atan2(dx, -dy));

        return Math.abs(angulo) > 12; // MÁS SENSIBLE
    }

    // =========================
    // HOMBROS DESBALANCEADOS
    // =========================
    public boolean hombrosCaidos(Pose pose){
        PoseLandmark izq = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
        PoseLandmark der = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);

        if (!valido(izq) || !valido(der)) return false;

        float diffY = Math.abs(izq.getPosition().y - der.getPosition().y);
        float ancho = Math.abs(izq.getPosition().x - der.getPosition().x);

        float ratio = diffY / ancho;

        return ratio > 0.08f; // NORMALIZADO
    }

    // =========================
    // ESPALDA ENCORVADA
    // =========================
    public boolean espaldaEncorvada(Pose pose){
        PoseLandmark hombroIzq = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
        PoseLandmark hombroDer = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);
        PoseLandmark caderaIzq = pose.getPoseLandmark(PoseLandmark.LEFT_HIP);
        PoseLandmark caderaDer = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP);

        if ((!valido(hombroIzq) && !valido(hombroDer)) ||
                (!valido(caderaIzq) && !valido(caderaDer)))
            return false;

        float hombroX, hombroY, caderaX, caderaY;

        if (valido(hombroIzq) && valido(hombroDer)) {
            hombroX = (hombroIzq.getPosition().x + hombroDer.getPosition().x)/2f;
            hombroY = (hombroIzq.getPosition().y + hombroDer.getPosition().y)/2f;
        } else {
            PoseLandmark ref = valido(hombroIzq) ? hombroIzq : hombroDer;
            hombroX = ref.getPosition().x;
            hombroY = ref.getPosition().y;
        }

        if (valido(caderaIzq) && valido(caderaDer)) {
            caderaX = (caderaIzq.getPosition().x + caderaDer.getPosition().x)/2f;
            caderaY = (caderaIzq.getPosition().y + caderaDer.getPosition().y)/2f;
        } else {
            PoseLandmark ref = valido(caderaIzq) ? caderaIzq : caderaDer;
            caderaX = ref.getPosition().x;
            caderaY = ref.getPosition().y;
        }

        float dx = hombroX - caderaX;
        float dy = hombroY - caderaY;

        double angulo = Math.toDegrees(Math.atan2(dx, -dy));

        return Math.abs(angulo) > 15; // MÁS PRECISO
    }

    // =========================
    // RESULTADO FINAL
    // =========================
    public boolean malaPostura(Pose pose){
        return cuello(pose) || hombrosCaidos(pose) || espaldaEncorvada(pose);
    }
}