
import javassist.CannotCompileException
import javassist.expr.ExprEditor
import javassist.expr.MethodCall

public class ClassExprEditor extends ExprEditor {
    public def filePath

    @Override
    void edit(MethodCall m) throws CannotCompileException {
        String clsName = m.getClassName()
        String methodName = m.getMethodName()

//        println "edit class=$clsName method=$methodName sign=${m.getSignature()}"
        handleLayoutInflater(m, clsName, methodName)
        handleLifeCycle(m, clsName)
    }

    private static void handleLifeCycle(MethodCall call, String className) {
        if (className == 'androidx.lifecycle.LifecycleRegistry' && call.methodName == 'sync') {
            println("catch androidx.lifecycle.LifecycleRegistry.sync() at$className, ${call.lineNumber}")
            call.replace('{' +
                    'try {' +
                    '   $proceed($$);' +
                    '} catch (java.lang.IllegalStateException e) {' +
                    '   android.util.Log.i("LifecycleRegistry", e.toString());' +
                    '}' +
                    '}')
        }
    }

    private static void handleLayoutInflater(MethodCall m, String clsName, String methodName) {
        if (methodName == 'setContentView' && m.signature == '(I)V') {
            println "replace Activity.setContentView"
            m.replace('{' +
                    'if ($0 instanceof android.app.Activity) {' +
                    '   android.view.LayoutInflater liccInflater = $0.getLayoutInflater();' +
                    Constant.CHECKER_CLASS_NAME + '.bind(liccInflater);' +
                    '}' +
                    '$proceed($$);' +
                    '}')
        } else if (clsName.equalsIgnoreCase('android.view.LayoutInflater')) {
            if (methodName == 'from' && m.signature == '(Landroid/content/Context;)Landroid/view/LayoutInflater;') {
                println "replace LayoutInflater.from()"
                m.replace('{' +
                        '$_ = $proceed($$);' +
                        'if ($_ != null) {' +
                        Constant.CHECKER_CLASS_NAME + '.bind($_);' +
                        '}' +
                        '}')
            } else if (methodName == 'createView') {
                //如果调用的是LayoutInflater.createView()，要检查一下缓存，避免self cannot be cast to self异常
                println "replace LayoutInflater.createView()"
                m.replace('{' +
                        Constant.CHECKER_CLASS_NAME + '.checkClass($1, $0.getContext());' +
                        '$_ = $proceed($$);' +
                        '}')
            } else if (methodName == 'setFactory' || methodName == 'setFactory2') {
                //FIXME 这里可能有隐患，我们的context给出的inflater都是设置过factory的，如果后面的代码又设置了一次，就会崩溃。只是目前没有替换context，所以似乎没有出现这个问题
                println "replace LayoutInflater.setFactory()"
                //这里是为了保证我们自己的Factory一定是后set进去的
                m.replace('{' +
                        Constant.CHECKER_CLASS_NAME + '.bind($0, $1);' +
                        '}')
            }
        } else if (methodName == 'getLayoutInflater' && m.signature == '()Landroid/view/LayoutInflater;') {
            println "replace Activity.getLayoutInflater()"
            m.replace('{' +
                    '$_ = $proceed($$);' +
                    'if ($0 instanceof android.app.Activity)' +
                    Constant.CHECKER_CLASS_NAME + '.bind($_);' +
                    '}')
        } else if (clsName == "android.view.View" && methodName == 'inflate' && m.signature == '(Landroid/content/Context;ILandroid/view/ViewGroup;)Landroid/view/View;') {
            println "replace View.inflate(Context, int, ViewGroup)"
            m.replace('{' +
                    'android.view.LayoutInflater liccInflater = android.view.LayoutInflater.from($1);' +
                    Constant.CHECKER_CLASS_NAME + '.bind(liccInflater);' +
                    '$_ = liccInflater.inflate($2, $3);' +
                    '}')
        } else if (methodName == 'getSystemService' && m.signature == '(Ljava/lang/String;)Ljava/lang/Object;') {
            println("replace Context.getSystemService()")
            m.replace('{' +
                    '$_ = $proceed($$);' +
                    'if ($_ != null && \"layout_inflater\".equals($1) && ($0 instanceof android.content.Context)) {' +
                    Constant.CHECKER_CLASS_NAME + '.bind((android.view.LayoutInflater) $_);' +
                    '}' +
                    '}')
        }
    }
}
