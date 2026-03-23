import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class BucketReflect {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = Class.forName("io.github.bucket4j.Bandwidth");
        for (Method m : clazz.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers())) {
                System.out.println(m.toString());
            }
        }
    }
}
