package com.chat.file;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileHistoryXmlManager {
    public static File getXmlFile(String user, boolean isGroup, String id) {
        String base = System.getProperty("user.home") + File.separator + "Documents" + File.separator + "ChatFiles" + File.separator + user;
        new File(base).mkdirs();
        String type = isGroup ? "group" : "private";
        return new File(base, "files_" + type + "_" + id + ".xml");
    }

    public static List<FileRecord> loadHistory(String user, boolean isGroup, String id) {
        List<FileRecord> list = new ArrayList<>();
        File file = getXmlFile(user, isGroup, id);
        if (!file.exists()) return list;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(file);
            NodeList nodes = doc.getElementsByTagName("file");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element e = (Element) nodes.item(i);
                FileRecord r = new FileRecord(
                    e.getAttribute("fileId"),
                    e.getAttribute("fileName"),
                    Long.parseLong(e.getAttribute("fileSize")),
                    e.getAttribute("sender"),
                    e.getAttribute("receiver"),
                    Boolean.parseBoolean(e.getAttribute("isGroup")),
                    Long.parseLong(e.getAttribute("timestamp"))
                );
                list.add(r);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public static void saveHistory(String user, boolean isGroup, String id, List<FileRecord> records) {
        File file = getXmlFile(user, isGroup, id);
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.newDocument();
            Element root = doc.createElement("files");
            doc.appendChild(root);
            for (FileRecord r : records) {
                Element e = doc.createElement("file");
                e.setAttribute("fileId", r.fileId);
                e.setAttribute("fileName", r.fileName);
                e.setAttribute("fileSize", String.valueOf(r.fileSize));
                e.setAttribute("sender", r.sender);
                e.setAttribute("receiver", r.receiver);
                e.setAttribute("isGroup", String.valueOf(r.isGroup));
                e.setAttribute("timestamp", String.valueOf(r.timestamp));
                root.appendChild(e);
            }
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer t = tf.newTransformer();
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            t.setOutputProperty(OutputKeys.INDENT, "yes");
            t.transform(new DOMSource(doc), new StreamResult(file));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void addRecord(String user, boolean isGroup, String id, FileRecord record) {
        List<FileRecord> list = loadHistory(user, isGroup, id);
        list.add(record);
        saveHistory(user, isGroup, id, list);
    }

    public static void removeRecord(String user, boolean isGroup, String id, String fileId) {
        List<FileRecord> list = loadHistory(user, isGroup, id);
        list.removeIf(r -> r.fileId.equals(fileId));
        saveHistory(user, isGroup, id, list);
    }
}

