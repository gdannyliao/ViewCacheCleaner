public class CommonData {

    /** 保存类文件名和 class 文件路径的关系 */
    def static classAndPath = [:]

    def static String appPackage

    def static putClassAndPath(def className, def classFilePath) {
        classAndPath.put(className, classFilePath)
    }

}
