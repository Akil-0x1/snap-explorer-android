package local.snapexplorer.mobile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Independent, dependency-free portable formats. Untrusted text is always escaped. */
public final class ExportFormats {
    public static String html(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "�").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
    public static String json(String raw) {
        if (raw == null) return "null";
        StringBuilder b = new StringBuilder("\"");
        for(int i=0;i<raw.length();i++) {
            char c=raw.charAt(i);
            switch(c) { case '"':b.append("\\\"");break;case '\\':b.append("\\\\");break;case '\n':b.append("\\n");break;case '\r':b.append("\\r");break;case '\t':b.append("\\t");break;
                default:if(c<32 || c=='\u2028' || c=='\u2029')b.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else b.append(c); }
        }
        return b.append('"').toString();
    }
    public static String csv(String raw) {
        if(raw==null)raw="";
        // Spreadsheet applications can execute formulas even in a quoted CSV cell.
        String stripped=raw.replaceFirst("^[\\s\\p{Z}\\p{Cntrl}\\uFEFF]+", "");
        if(!stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0))>=0)raw="'"+raw;
        return "\""+raw.replace("\"","\"\"")+"\"";
    }
    public static String sha256(String value) {
        try { return hex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e){throw new IllegalStateException(e);}
    }
    public static String hex(byte[] bytes) {StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    public static void entry(ZipOutputStream zip,String name,String text)throws IOException {
        zip.putNextEntry(new ZipEntry(name));zip.write(text.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
    }
    public static void csv(Writer writer,List<String[]> rows)throws IOException {
        for(String[] row:rows){for(int i=0;i<row.length;i++){if(i>0)writer.write(',');writer.write(csv(row[i]));}writer.write("\r\n");}writer.flush();
    }
    public static void spreadsheet(OutputStream out,String format,List<String[]> rows)throws IOException {
        ZipOutputStream zip=new ZipOutputStream(out);
        if(format.equals("ods")) {
            byte[] mime="application/vnd.oasis.opendocument.spreadsheet".getBytes(StandardCharsets.US_ASCII);
            CRC32 crc=new CRC32();crc.update(mime);ZipEntry first=new ZipEntry("mimetype");first.setMethod(ZipEntry.STORED);first.setSize(mime.length);first.setCompressedSize(mime.length);first.setCrc(crc.getValue());zip.putNextEntry(first);zip.write(mime);zip.closeEntry();
            entry(zip,"META-INF/manifest.xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><manifest:manifest xmlns:manifest=\"urn:oasis:names:tc:opendocument:xmlns:manifest:1.0\" manifest:version=\"1.2\"><manifest:file-entry manifest:full-path=\"/\" manifest:media-type=\"application/vnd.oasis.opendocument.spreadsheet\"/><manifest:file-entry manifest:full-path=\"content.xml\" manifest:media-type=\"text/xml\"/></manifest:manifest>");
            StringBuilder xml=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><office:document-content xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" xmlns:table=\"urn:oasis:names:tc:opendocument:xmlns:table:1.0\" xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\" office:version=\"1.2\"><office:body><office:spreadsheet><table:table table:name=\"Archive statistics\">");
            for(String[] row:rows){xml.append("<table:table-row>");for(String cell:row)xml.append("<table:table-cell office:value-type=\"string\"><text:p>").append(html(cell)).append("</text:p></table:table-cell>");xml.append("</table:table-row>");}
            entry(zip,"content.xml",xml.append("</table:table></office:spreadsheet></office:body></office:document-content>").toString());
        } else {
            entry(zip,"[Content_Types].xml","<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/drawings/drawing1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawing+xml\"/><Override PartName=\"/xl/charts/chart1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawingml.chart+xml\"/></Types>");
            entry(zip,"_rels/.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            entry(zip,"xl/workbook.xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Archive statistics\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            entry(zip,"xl/_rels/workbook.xml.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
            StringBuilder xml=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheetData>");
            int index=1;for(String[] row:rows){xml.append("<row r=\"").append(index++).append("\">");for(int col=0;col<row.length;col++){String cell=row[col];if(col==1&&cell.matches("[0-9]+"))xml.append("<c t=\"n\"><v>").append(cell).append("</v></c>");else xml.append("<c t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(html(cell)).append("</t></is></c>");}xml.append("</row>");}
            entry(zip,"xl/worksheets/sheet1.xml",xml.append("</sheetData><drawing r:id=\"rId1\"/></worksheet>").toString());
            chart(zip,rows);
        }
        zip.finish();zip.flush();
    }
    private static void chart(ZipOutputStream zip,List<String[]> rows)throws IOException {
        entry(zip,"xl/worksheets/_rels/sheet1.xml.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing\" Target=\"../drawings/drawing1.xml\"/></Relationships>");
        entry(zip,"xl/drawings/_rels/drawing1.xml.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/chart\" Target=\"../charts/chart1.xml\"/></Relationships>");
        entry(zip,"xl/drawings/drawing1.xml","<?xml version=\"1.0\"?><xdr:wsDr xmlns:xdr=\"http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"><xdr:twoCellAnchor><xdr:from><xdr:col>3</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>1</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from><xdr:to><xdr:col>12</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>18</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:to><xdr:graphicFrame macro=\"\"><xdr:nvGraphicFramePr><xdr:cNvPr id=\"2\" name=\"Archive counts\"/><xdr:cNvGraphicFramePr/></xdr:nvGraphicFramePr><xdr:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/></xdr:xfrm><a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/chart\"><c:chart xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" r:id=\"rId1\"/></a:graphicData></a:graphic></xdr:graphicFrame><xdr:clientData/></xdr:twoCellAnchor></xdr:wsDr>");
        int total=Math.max(0,Math.min(5,rows.size()-1)),end=total+1;
        StringBuilder cats=new StringBuilder(),numbers=new StringBuilder();
        for(int i=0;i<total;i++){String[] row=rows.get(i+1);cats.append("<c:pt idx=\"").append(i).append("\"><c:v>").append(html(row[0])).append("</c:v></c:pt>");numbers.append("<c:pt idx=\"").append(i).append("\"><c:v>").append(row.length>1&&row[1].matches("[0-9]+")?row[1]:"0").append("</c:v></c:pt>");}
        String chart="<?xml version=\"1.0\"?><c:chartSpace xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\"><c:chart><c:autoTitleDeleted val=\"1\"/><c:plotArea><c:layout/><c:barChart><c:barDir val=\"col\"/><c:grouping val=\"clustered\"/><c:ser><c:idx val=\"0\"/><c:order val=\"0\"/><c:tx><c:v>Archive counts</c:v></c:tx><c:cat><c:strRef><c:f>'Archive statistics'!$A$2:$A$"+end+"</c:f><c:strCache><c:ptCount val=\""+total+"\"/>"+cats+"</c:strCache></c:strRef></c:cat><c:val><c:numRef><c:f>'Archive statistics'!$B$2:$B$"+end+"</c:f><c:numCache><c:formatCode>General</c:formatCode><c:ptCount val=\""+total+"\"/>"+numbers+"</c:numCache></c:numRef></c:val></c:ser><c:axId val=\"10\"/><c:axId val=\"20\"/></c:barChart><c:catAx><c:axId val=\"10\"/><c:scaling><c:orientation val=\"minMax\"/></c:scaling><c:axPos val=\"b\"/><c:crossAx val=\"20\"/><c:crosses val=\"autoZero\"/></c:catAx><c:valAx><c:axId val=\"20\"/><c:scaling><c:orientation val=\"minMax\"/></c:scaling><c:axPos val=\"l\"/><c:crossAx val=\"10\"/><c:crosses val=\"autoZero\"/><c:crossBetween val=\"between\"/></c:valAx></c:plotArea><c:plotVisOnly val=\"1\"/></c:chart></c:chartSpace>";
        entry(zip,"xl/charts/chart1.xml",chart);
    }
    private ExportFormats() { }
}
