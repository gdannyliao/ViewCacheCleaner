package com.ggdsn.view;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;

public class ViewCacheCleaner implements LayoutInflater.Factory2 {
    private static final String MY_NAME = ViewCacheCleaner.class.getName();
    private static final ClassLoader BOOT_CLASS_LOADER = LayoutInflater.class.getClassLoader();
    private static Map sConstructorMap;

    static {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            try {
                Field field = LayoutInflater.class.getDeclaredField("sConstructorMap");
                field.setAccessible(true);
                sConstructorMap = (Map) field.get(null);
                field.setAccessible(false);
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private static final boolean verifyClassLoader(Object constructor, Context context) {
        final ClassLoader constructorLoader = ((Constructor) constructor).getDeclaringClass().getClassLoader();
        if (constructorLoader == BOOT_CLASS_LOADER) {
            // fast path for boot class loader (most common case?) - always ok
            return true;
        }

        if (context == null) {
            return true;
        }
        // in all normal cases (no dynamic code loading), we will exit the following loop on the
        // first iteration (i.e. when the declaring classloader is the contexts class loader).
        ClassLoader cl = context.getClassLoader();
        do {
            if (constructorLoader == cl) {
                return true;
            }
            cl = cl.getParent();
        } while (cl != null);
        return false;
    }

    public static void checkClass(String name, Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Object constructor = sConstructorMap.get(name);
            if (constructor != null && !verifyClassLoader(constructor, context)) {
                System.out.println("remove constructor " + constructor + " classloader=" + ((Constructor) constructor).getDeclaringClass().getClassLoader());
                sConstructorMap.remove(name);
            }
        }
    }


    private static boolean isMe(LayoutInflater.Factory factory) {
        return MY_NAME.equals(factory.getClass().getName());
    }

    private static void setFactorySetFlag(LayoutInflater inflater, boolean setOrNot) {
        try {
            Field field = LayoutInflater.class.getDeclaredField("mFactorySet");
            if (!field.isAccessible())
                field.setAccessible(true);
            field.set(inflater, setOrNot);
        } catch (NoSuchFieldException e) {
            Log.w("", e.getMessage());
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private static void setFactoryNull(LayoutInflater inflater) {
        try {
            Field field = LayoutInflater.class.getDeclaredField("mFactory");
            if (!field.isAccessible())
                field.setAccessible(true);
            field.set(inflater, null);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private static void setFactory2Null(LayoutInflater inflater) {
        try {
            Field field = LayoutInflater.class.getDeclaredField("mFactory2");
            if (!field.isAccessible())
                field.setAccessible(true);
            field.set(inflater, null);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private static void removeFactory(LayoutInflater inflater) {
        setFactorySetFlag(inflater, false);
        setFactoryNull(inflater);
    }

    private static void removeFactory2(LayoutInflater inflater) {
        removeFactory(inflater);
        setFactory2Null(inflater);
    }

    private LayoutInflater.Factory factory;
    private LayoutInflater.Factory2 factory2;

    public ViewCacheCleaner() {

    }

    public ViewCacheCleaner(LayoutInflater.Factory factory) {
        this.factory = factory;
    }

    public ViewCacheCleaner(LayoutInflater.Factory2 factory) {
        this.factory2 = factory;
    }

    public static void bind(LayoutInflater original) {
        if (original == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) return;

        LayoutInflater.Factory2 factory2 = original.getFactory2();
        if (factory2 != null) {
            if (!isMe(factory2)) {
                //不是的话，就把我们插入进去
                removeFactory2(original);
                original.setFactory2(new ViewCacheCleaner(factory2));
            }
            return;
        }

        //factory 和factory2只能同时设置一个
        LayoutInflater.Factory factory = original.getFactory();
        if (factory != null) {
            if (!isMe(factory)) {
                removeFactory(original);
                original.setFactory(new ViewCacheCleaner(factory));
            }
            return;
        }

        //如果factory都没有设置过，则用我们自己的
        original.setFactory2(new ViewCacheCleaner());
    }

    public static void bind(LayoutInflater original, LayoutInflater.Factory factory) {
        if (original == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            original.setFactory(factory);
            return;
        }

        LayoutInflater.Factory oldFactory = original.getFactory();
        if (oldFactory != null) {
            if (isMe(oldFactory)) {
                //如果已经被我们设置过factory，只需要更新引用的factory
                if (original.getContext().getClassLoader() != ((java.lang.Object) oldFactory).getClass().getClassLoader()) {
                    removeFactory(original);
                    original.setFactory(new ViewCacheCleaner(factory));
                } else {
                    //需要判断是否是同一个类加载器加载的，否则在更新factory时会崩溃
                    ((ViewCacheCleaner) oldFactory).factory = factory;
                }
            } else {
                //让它崩溃，显示原始的崩溃信息
                original.setFactory(factory);
            }
            return;
        }

        original.setFactory(new ViewCacheCleaner(factory));
    }

    public static void bind(LayoutInflater original, LayoutInflater.Factory2 factory2) {
        if (original == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            original.setFactory2(factory2);
            return;
        }

        LayoutInflater.Factory2 oldFactory2 = original.getFactory2();
        if (oldFactory2 != null) {
            if (isMe(oldFactory2)) {
                if (original.getContext().getClassLoader() != ((java.lang.Object) oldFactory2).getClass().getClassLoader()) {
                    removeFactory2(original);
                    original.setFactory2(new ViewCacheCleaner(factory2));
                } else
                    ((ViewCacheCleaner) oldFactory2).factory2 = factory2;
            } else {
                original.setFactory2(factory2);
            }
            return;
        }

        original.setFactory2(new ViewCacheCleaner(factory2));
    }


    @Override
    public View onCreateView(String name, Context context, AttributeSet attrs) {
        checkClass(name, context);
        LayoutInflater.Factory factory = this.factory;
        if (factory != null && !isMe(factory)) return factory.onCreateView(name, context, attrs);
        return null;
    }

    @Override
    public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
        checkClass(name, context);
        LayoutInflater.Factory2 factory2 = this.factory2;
        if (factory2 != null && !isMe(factory2)) return factory2.onCreateView(parent, name, context, attrs);
        return null;
    }
}