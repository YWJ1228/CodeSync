//package com.ssafy.codesync.util;
//
//import com.jcraft.jsch.Session;
//
//import java.io.File;
//
//public class SessionFileStorage {
//    private static SessionFileStorage instance;
//    private String name;
//    private File file;
//
//    // private 생성자
//    private SessionFileStorage() {}
//    public void setSessionFileStorage(String name, File file) {
//        instance.name = name;
//        instance.file = file;
//    }
//
//    // 인스턴스를 가져오는 메서드
//    public static synchronized SessionFileStorage getInstance() {
//        if (instance == null) {
//            instance = new SessionFileStorage();
//        }
//        return instance;
//    }
//
//    public void setName(String name) {
//        this.name = name;
//    }
//
//    public String getName() {
//        return name;
//    }
//
//    public void setFile(File file) {
//        this.file = file;
//    }
//
//    public File getFile() {
//        return file;
//    }
//}