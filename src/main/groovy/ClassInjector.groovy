import javassist.ClassPool
import javassist.expr.ExprEditor
import org.gradle.api.Project

import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

/**
 * @author RePlugin Team
 */
public class ClassInjector {
    protected Project project

    protected String variantDir

    @Override
    public Object name() {
        return getClass().getSimpleName()
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public void setVariantDir(String variantDir) {
        this.variantDir = variantDir;
    }
    // 表达式编辑器
    ExprEditor editor

    @Override
    def injectClass(ClassPool pool, String dir, Map config) {

        println "ClassInjector handle $dir"

        def path = Paths.get(dir)
        if (!Files.exists(path)) {
            println "$dir not exist, ignore"
            return
        }

        if (editor == null) {
            editor = new ClassExprEditor()
        }

        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

                String filePath = file.toString()
                if (!filePath.endsWith(".class") || filePath.contains(Constant.CHECKER_NAME))
                    return super.visitFile(file, attrs)

                editor.filePath = filePath

                def stream, ctCls
                try {
                    stream = new FileInputStream(filePath)
                    ctCls = pool.makeClass(stream);

                    // println ctCls.name
                    if (ctCls.isFrozen()) {
                        ctCls.defrost()
                    }

                    ctCls.getDeclaredMethods().each {
                        it.instrument(editor)

//                        println "ClassInjector check ${ctCls.name}.${it.name}()"
                        if (ctCls.name == "androidx.lifecycle.ReportFragment" && it.name == "get") {
                            println ("change androidx.lifecycle.ReportFragment get()")
                            it.setBody('{' +
                                    'android.app.Fragment fgm = $1.getFragmentManager().findFragmentByTag(REPORT_FRAGMENT_TAG);' +
//                                    'java.lang.ClassLoader cl = fgm  != null? fgm.getClass().getClassLoader() : null;' +
//                                    'System.out.println(\"ReportFragment get() fgm cl=\" + (cl == null ? null : (cl.toString() + cl.hashCode())) + \" acti cl=\" + $1.getClassLoader() + $1.getClassLoader().hashCode()' +
//                                    '+ \" class cl=\" + androidx.lifecycle.ReportFragment.class.getClassLoader() + androidx.lifecycle.ReportFragment.class.getClassLoader().hashCode());' +
                                    'if (fgm != null && fgm.getClass().getClassLoader() != androidx.lifecycle.ReportFragment.class.getClassLoader()) {' +
                                    '   androidx.lifecycle.ReportFragment newFgm = new androidx.lifecycle.ReportFragment();' +
                                    '   android.app.FragmentManager manager = $1.getFragmentManager();\n' +
                                    '   manager.beginTransaction().add(newFgm, REPORT_FRAGMENT_TAG).commit();\n' +
                                    '   manager.executePendingTransactions();\n' +
                                    '   return newFgm;' +
                                    '}' +
                                    'return (androidx.lifecycle.ReportFragment) fgm;' +
                                    '}')
                        }
                    }

//                    ctCls.getMethods().each {
//                        println "check method ${it.longName}"
//                        it.instrument(editor)
//                    }

                    ctCls.writeFile(dir)
                } catch (Throwable t) {
                    println "    [Warning] --> ${t.toString()}"
                    t.printStackTrace()
                } finally {
                    if (ctCls != null) {
                        ctCls.detach()
                    }
                    if (stream != null) {
                        stream.close()
                    }
                }

                return super.visitFile(file, attrs)
            }
        })
    }
}
