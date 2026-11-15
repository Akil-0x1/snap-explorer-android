package local.snapexplorer.mobile;

import java.io.IOException;
import java.time.ZoneId;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public final class MetadataAnalyticsTest {
    static int passed;
    interface Check { void run() throws Exception; }
    static void test(String name,Check check)throws Exception{check.run();passed++;System.out.println("PASS "+name);}
    static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    static MetadataAnalytics.Result analyze(String json)throws Exception{return MetadataAnalytics.analyze("json/search_history.json",json,ZoneId.of("UTC"));}
    public static void main(String[]args)throws Exception{
        test("Records count object rows rather than fields",()->{MetadataAnalytics.Result r=analyze("{\"History\":[{\"Date\":\"2026-11-01 12:00:00 UTC\",\"Type\":\"SEARCH\",\"Username\":\"sam\",\"Query\":\"coffee\"},{\"Date\":\"2026-11-02\",\"Type\":\"SEARCH\",\"Username\":\"sam\"}]}");require(r.records==2&&r.daily.size()==2&&r.categories.get("SEARCH")==2&&r.contacts.get("sam")==2,"row metrics");});
        test("Scalar arrays do not become events",()->require(analyze("{\"Names\":[\"alex\",\"sam\"],\"Values\":[1,2,3]}").records==0,"scalar arrays"));
        test("Nested wrappers expose records",()->{MetadataAnalytics.Result r=analyze("{\"Download\":{\"History\":{\"Saved\":[{\"Created\":\"2026-11-01\",\"Action\":\"Viewed\"}]}}}");require(r.records==1&&r.categories.get("Viewed")==1,"nested records");});
        test("Labels on wrappers are not events",()->{MetadataAnalytics.Result r=analyze("{\"Source\":\"Snapchat\",\"Rows\":[{\"Date\":\"2026-11-01\"},{\"Date\":\"2026-11-02\"}]}");require(r.records==2&&!r.categories.containsKey("Snapchat"),"wrapper labels");});
        test("Attachments and location objects do not inflate a record",()->{MetadataAnalytics.Result r=analyze("[{\"Created\":\"2026-11-01\",\"From\":\"alex\",\"Location\":{\"Latitude\":1,\"Longitude\":2},\"Attachments\":[{\"Type\":\"Image\"},{\"Type\":\"Image\"}]}]");require(r.records==1&&r.categories.isEmpty(),"nested attachment data");});
        test("Unlabelled object rows count without fabricated metrics",()->{MetadataAnalytics.Result r=analyze("{\"Searches\":[{\"Query\":\"tea\"},{\"Query\":\"coffee\"}]}");require(r.records==2&&r.daily.isEmpty()&&r.categories.isEmpty()&&r.contacts.isEmpty(),"no fabricated fields");});
        test("Account basic information is one snapshot",()->{MetadataAnalytics.Result r=MetadataAnalytics.analyze("account.json","{\"Basic Information\":{\"Username\":\"me\",\"Email\":\"local@example.invalid\"}}",null);require(r.records==1&&r.contacts.get("me")==1&&r.daily.isEmpty(),"snapshot");});
        test("Timezone determines daily bucket",()->{MetadataAnalytics.Result r=MetadataAnalytics.analyze("history.json","[{\"Date\":\"2026-11-01 23:30:00 UTC\"}]",ZoneId.of("Asia/Kolkata"));require(r.daily.containsKey("2026-11-02"),"local date bucket");});
        test("Numeric epoch seconds and milliseconds",()->{MetadataAnalytics.Result r=analyze("[{\"Timestamp\":1793534400},{\"Timestamp\":1793534400000}]");require(r.records==2&&r.daily.get("2026-11-01")==2,"numeric epoch");});
        test("Numeric epoch zero is a real date",()->{MetadataAnalytics.Result r=analyze("[{\"Timestamp\":0}]");require(r.daily.get("1970-01-01")==1,"epoch zero");});
        test("Unknown date does not hide later valid date field",()->{MetadataAnalytics.Result r=analyze("[{\"Date\":\"unknown\",\"Created\":\"2026-11-01\"}]");require(r.daily.get("2026-11-01")==1,"date candidates");});
        test("Case-insensitive known fields",()->{MetadataAnalytics.Result r=analyze("[{\"created\":\"2026-11-01\",\"media type\":\"Image\",\"from\":\"sam\"}]");require(r.categories.get("Image")==1&&r.contacts.get("sam")==1,"field case");});
        test("Sort order is deterministic and ranked",()->{MetadataAnalytics.Result r=analyze("[{\"Type\":\"b\"},{\"Type\":\"a\"},{\"Type\":\"b\"},{\"Type\":\"c\"}]");require(r.categories.keySet().toString().equals("[b, a, c]"),"ranked categories");});
        test("File labels are readable",()->require(analyze("[]").label.equals("Search history"),"dataset label"));
        test("Depth limit enforced before recursive parsing",()->{StringBuilder json=new StringBuilder();for(int i=0;i<33;i++)json.append('[');for(int i=0;i<33;i++)json.append(']');try{analyze(json.toString());}catch(IOException expected){return;}throw new AssertionError("depth limit");});
        test("Quoted braces are not nesting",()->{StringBuilder value=new StringBuilder();for(int i=0;i<100;i++)value.append('{');MetadataAnalytics.Result r=analyze(new JSONArray().put(new JSONObject().put("Query",value.toString())).toString());require(r.records==1,"opaque string");});
        test("Record ceiling enforced",()->{JSONArray records=new JSONArray();for(int i=0;i<200001;i++)records.put(new JSONObject().put("Type","x"));try{analyze(records.toString());}catch(IOException expected){return;}throw new AssertionError("record limit");});
        test("Missing metrics remain empty",()->{MetadataAnalytics.Result r=analyze("[{\"Query\":\"coffee\",\"Detail\":\"No date, sender, type or duration was exported\"}]");require(r.records==1&&r.categories.isEmpty()&&r.contacts.isEmpty()&&r.daily.isEmpty(),"metric absence");});
        System.out.println(passed+" metadata analytics tests passed.");
    }
}
