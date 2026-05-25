
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions;
import java.lang.reflect.Method;

public class ReflectMLKit {
    public static void main(String[] args) {
        try {
            Class<?> builderClass = AccuratePoseDetectorOptions.Builder.class;
            System.out.println("Methods in AccuratePoseDetectorOptions.Builder:");
            for (Method method : builderClass.getDeclaredMethods()) {
                System.out.println(" - " + method.getName());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
