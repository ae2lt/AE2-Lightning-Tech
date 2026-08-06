package com.moakiee.ae2lt.logic;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class ConnectionEndpoints {
    private ConnectionEndpoints() {
    }

    public static <T, D, P, F> int indexOfEndpoint(
            List<T> source,
            D dimension,
            P pos,
            F face,
            Function<T, D> dimensionGetter,
            Function<T, P> posGetter,
            Function<T, F> faceGetter) {
        for (int i = 0; i < source.size(); i++) {
            var connection = source.get(i);
            if (Objects.equals(dimensionGetter.apply(connection), dimension)
                    && Objects.equals(posGetter.apply(connection), pos)
                    && Objects.equals(faceGetter.apply(connection), face)) {
                return i;
            }
        }
        return -1;
    }
}
