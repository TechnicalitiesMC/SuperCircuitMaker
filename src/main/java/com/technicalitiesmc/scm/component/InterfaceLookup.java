package com.technicalitiesmc.scm.component;

import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.lib.math.VecDirectionFlags;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class InterfaceLookup<T extends CircuitComponentBase<T>> {

    private static final InterfaceLookup EMPTY = new InterfaceLookup(Map.of());

    private final Map<Class, BiFunction> suppliers;

    private InterfaceLookup(Map<Class, BiFunction> suppliers) {
        this.suppliers = suppliers;
    }

    public <V> V get(T target, VecDirection direction, Class<V> type) {
        var supplier = suppliers.get(type);
        return supplier == null ? null : (V) supplier.apply(target, direction);
    }

    // Builder

    public static <T extends CircuitComponentBase<T>> InterfaceLookup<T> empty() {
        return EMPTY;
    }

    public static <T extends CircuitComponentBase<T>> Builder<T> builder() {
        return new Builder<>();
    }

    public static class Builder<T extends CircuitComponentBase<T>> {

        private final Map<Class, BiFunction> suppliers = new IdentityHashMap<>();

        private Builder() {
        }

        public <V> Builder<T> with(Class<V> type, BiFunction<T, VecDirection, V> supplier) {
            suppliers.put(type, supplier);
            return this;
        }

        public <V> Builder<T> with(Class<V> type, Function<T, V> supplier) {
            return with(type, (component, $) -> supplier.apply(component));
        }

        public <V> Builder<T> with(Class<V> type, Supplier<V> supplier) {
            return with(type, ($, $$) -> supplier.get());
        }

        public <V> Builder<T> with(Class<V> type, VecDirectionFlags sides, BiFunction<T, VecDirection, V> supplier) {
            return with(type, (component, dir) -> sides.has(dir) ? supplier.apply(component, dir) : null);
        }

        public <V> Builder<T> with(Class<V> type, VecDirectionFlags sides, Function<T, V> supplier) {
            return with(type, (component, dir) -> sides.has(dir) ? supplier.apply(component) : null);
        }

        public <V> Builder<T> with(Class<V> type, VecDirectionFlags sides, Supplier<V> supplier) {
            return with(type, ($, dir) -> sides.has(dir) ? supplier.get() : null);
        }

        public InterfaceLookup<T> build() {
            return new InterfaceLookup<>(suppliers);
        }

    }

}
