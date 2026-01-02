package ru.cbgr.adapter.xwiki.utils;

import java.util.List;

public final class FloatVectorMapper {

    private FloatVectorMapper() {}

    public static float[] toFloatArray(List<Double> list) {
        if (list == null) return new float[0];
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i).floatValue();
        }
        return arr;
    }
}
