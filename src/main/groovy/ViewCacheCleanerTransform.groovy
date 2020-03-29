import com.android.build.api.transform.Context
import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInput
import com.android.build.api.transform.TransformOutputProvider
import com.android.build.gradle.internal.pipeline.TransformManager
import javassist.ClassPool
import org.gradle.api.Project

/**
 * @author RePlugin Team
 */
public class ViewCacheCleanerTransform extends Transform {

    @Override
    String getName() {
        return "ViewCacheCleanerTransform"
    }

    private Project project
    private def globalScope

    /* 需要处理的 jar 包 */
    def includeJars = [] as Set
    def map = [:]

    private ClassInjector  classInjector = new ClassInjector()

    public RePluginInjectTransform(Project p) {
        this.project = p
        def appPlugin = project.plugins.getPlugin(AppPlugin)
        // taskManager 在 2.1.3 中为 protected 访问类型的，在之后的版本为 private 访问类型的，
        // 使用反射访问
        def taskManager = BasePlugin.metaClass.getProperty(appPlugin, "taskManager")
        this.globalScope = taskManager.globalScope;
    }

    @Override
    void transform(Context context,
                   Collection<TransformInput> inputs,
                   Collection<TransformInput> referencedInputs,
                   TransformOutputProvider outputProvider,
                   boolean isIncremental) throws IOException, TransformException, InterruptedException {

        /* 初始化 ClassPool */
        Object pool = initClassPool(inputs)

        doInject(inputs, pool, classInjector, null)

        repackage()

        /* 拷贝 class 和 jar 包 */
        copyResult(inputs, outputProvider)

        Util.newSection()
    }

    /**
     * 拷贝处理结果
     */
    def copyResult(def inputs, def outputs) {
        // Util.newSection()
        inputs.each { TransformInput input ->
            input.directoryInputs.each { DirectoryInput dirInput ->
                copyDir(outputs, dirInput)
            }
            input.jarInputs.each { JarInput jarInput ->
                copyJar(outputs, jarInput)
            }
        }
    }

    /**
     * 将解压的 class 文件重新打包，然后删除 class 文件
     */
    def repackage() {
        Util.newSection()
        println '>>> Repackage...'
        includeJars.each {
            File jar = new File(it)
            String JarAfterzip = map.get(jar.getParent() + File.separatorChar + jar.getName())
            String dirAfterUnzip = JarAfterzip.replace('.jar', '')
            // println ">>> 压缩目录 $dirAfterUnzip"

            Util.zipDir(dirAfterUnzip, JarAfterzip)

            // println ">>> 删除目录 $dirAfterUnzip"
            FileUtils.deleteDirectory(new File(dirAfterUnzip))
        }
    }

    /**
     * 执行注入操作
     */
    def doInject(Collection<TransformInput> inputs, ClassPool pool,
                 ClassInjector injector, Object config) {
        try {
            inputs.each { TransformInput input ->
                input.directoryInputs.each {
                    handleDir(pool, it, injector, config)
                }
                input.jarInputs.each {
                    handleJar(pool, it, injector, config)
                }
            }
        } catch (Throwable t) {
            println t.toString()
        }
    }

    /**
     * 初始化 ClassPool
     */
    def initClassPool(Collection<TransformInput> inputs) {
        Util.newSection()
        def pool = new ClassPool(true)
        // 添加编译时需要引用的到类到 ClassPool, 同时记录要修改的 jar 到 includeJars
        Util.getClassPaths(project, globalScope, inputs, includeJars, map).each {
            println "    $it"
            pool.insertClassPath(it)
        }
        pool
    }

    /**
     * 处理 jar
     */
    def handleJar(ClassPool pool, JarInput input, ClassInjector injector, Object config) {
        File jar = input.file
        if (jar.absolutePath in includeJars) {
            println "--->>> Handle Jar: ${jar.absolutePath}"
            String dirAfterUnzip = map.get(jar.getParent() + File.separatorChar + jar.getName()).replace('.jar', '')
            injector.injectClass(pool, dirAfterUnzip, config)
        }
    }

    /**
     * 拷贝 Jar
     */
    def copyJar(TransformOutputProvider output, JarInput input) {
        File jar = input.file
        String jarPath = map.get(jar.absolutePath);
        if (jarPath != null) {
            jar = new File(jarPath)
        }

        if (!jar.exists()) {
            return
        }

        String destName = input.name
        def hexName = DigestUtils.md5Hex(jar.absolutePath)
        if (destName.endsWith('.jar')) {
            destName = destName.substring(0, destName.length() - 4)
        }
        File dest = output.getContentLocation(destName + '_' + hexName, input.contentTypes, input.scopes, Format.JAR)
        FileUtils.copyFile(jar, dest)
    }

    def handleDir(ClassPool pool, DirectoryInput input, ClassInjector injector, Object config) {
        println "--->>> Handle Dir: ${input.file.absolutePath}"
        injector.injectClass(pool, input.file.absolutePath, config)
    }

    def copyDir(TransformOutputProvider output, DirectoryInput input) {
        File dest = output.getContentLocation(input.name, input.contentTypes, input.scopes, Format.DIRECTORY)
        FileUtils.copyDirectory(input.file, dest)
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    @Override
    Set<QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    @Override
    boolean isIncremental() {
        return false
    }
}
