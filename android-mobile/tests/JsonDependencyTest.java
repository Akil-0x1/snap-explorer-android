package local.snapexplorer.mobile;
import org.json.JSONObject;
/** Host-only dependency regression; this JSON-java JAR is not bundled in Android. */
public final class JsonDependencyTest {
 public static void main(String[] args){
  if(new JSONObject("{\"n\":123}").getInt("n")!=123)throw new AssertionError("Normal numeric records must remain readable");
  StringBuilder digits=new StringBuilder();for(int i=0;i<100000;i++)digits.append('9');
  Object integer=new JSONObject("{\"n\":"+digits+"}").get("n");
  if(!(integer instanceof String))throw new AssertionError("Oversized integer must avoid expensive BigInteger construction; update the host JSON dependency");
  Object decimal=new JSONObject("{\"n\":0."+digits+"}").get("n");
  if(!(decimal instanceof String))throw new AssertionError("Oversized fraction must avoid expensive BigDecimal construction");
  System.out.println("3 host JSON dependency security checks passed.");
 }
}
