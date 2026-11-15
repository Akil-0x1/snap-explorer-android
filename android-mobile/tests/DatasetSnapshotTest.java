package local.snapexplorer.mobile;
public final class DatasetSnapshotTest {
 public static void main(String[] args){
  String raw="{\"Friends\":[{\"Username\":\"example\"}]}";
  require(DatasetSnapshot.key("friends.json",raw).equals(DatasetSnapshot.key("bundle/0001/FRIENDS.JSON",raw)),"same filename and bytes deduplicate across variants");
  require(!DatasetSnapshot.key("friends.json",raw).equals(DatasetSnapshot.key("friends.json",raw+" ")),"changed snapshots remain distinct");
  require(!DatasetSnapshot.key("friends.json",raw).equals(DatasetSnapshot.key("blocked.json",raw)),"different dataset names remain distinct");
  System.out.println("3 metadata snapshot tests passed.");
 }
 private static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
}
