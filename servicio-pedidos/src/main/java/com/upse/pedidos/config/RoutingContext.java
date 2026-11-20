package com.upse.pedidos.config;

public class RoutingContext {

    private static final ThreadLocal<Boolean> CRITICAL_READ = new ThreadLocal<>();
    private static final ThreadLocal<DataSourceType> LAST_SELECTED = new ThreadLocal<>();

    public static void setCriticalRead(boolean isCritical) {
        CRITICAL_READ.set(isCritical);
    }

    public static boolean isCriticalRead() {
        return Boolean.TRUE.equals(CRITICAL_READ.get());
    }

    public static void setLastSelected(DataSourceType type) {
        LAST_SELECTED.set(type);
    }

    public static DataSourceType getLastSelected() {
        return LAST_SELECTED.get();
    }

    public static void clear() {
        CRITICAL_READ.remove();
        LAST_SELECTED.remove();
    }
}
