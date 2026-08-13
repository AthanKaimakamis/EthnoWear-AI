package fmi.ethnowear.support;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

public final class RepositoryTestProxies {

    private RepositoryTestProxies() {
    }

    public static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    public static <T> T rejecting(Class<T> type) {
        return proxy(type, (ignored, method, arguments) -> {
            throw new AssertionError("Unexpected repository call: " + method.getName());
        });
    }
}
