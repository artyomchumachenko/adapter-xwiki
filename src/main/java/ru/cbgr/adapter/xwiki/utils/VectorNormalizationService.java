package ru.cbgr.adapter.xwiki.utils;

import org.springframework.stereotype.Service;

@Service
public class VectorNormalizationService {

    /**
     * Нормализует вектор согласно типу оператора индекса.
     *
     * @param indexOp Название оператора индекса (например, "vector_l2_ops" или "vector_cosine_ops").
     * @param vector  Массив входных значений вектора.
     * @return Нормализованный вектор.
     */
    public float[] normalizeVector(String indexOp, float[] vector) {
        if (indexOp.contains("l2")) {
            return normalizeL2(vector);
        } else if (indexOp.contains("cosine")) {
            // В данном примере нормализация для cosine может быть аналогична L2
            return normalizeL2(vector);
        } else {
            throw new IllegalArgumentException("Неизвестный тип нормализации для оператора: " + indexOp);
        }
    }

    /**
     * Выполняет L2-нормализацию вектора.
     *
     * @param vector Входной вектор.
     * @return Нормализованный вектор.
     */
    private float[] normalizeL2(float[] vector) {
        double norm = 0.0;
        for (float v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        if (norm == 0) {
            return vector;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
        return vector;
    }
}
